package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.DungeonTemplate;
import ru.elytrix.dungeons.model.LootPoolType;
import ru.elytrix.dungeons.model.SurfaceShulkerRarity;
import ru.elytrix.dungeons.structure.SpongeSchematicCodec;
import ru.elytrix.dungeons.structure.TerrainSnapshotStore;
import ru.elytrix.dungeons.structure.TerrainSnapshotStore.BlockRestoreDiff;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonManager {

    public static final int MAX_DUNGEONS_PER_CATEGORY = 7;
    // Restart just before the 2:58 track ends to hide tick scheduling/network jitter.
    private static final long MUSIC_DISC_5_DURATION_MS = 177_000L;

    public enum CreateStatus {
        CREATED,
        MAX_REACHED,
        INVALID_ID,
        ID_ALREADY_EXISTS,
        MISSING_SCHEMATIC,
        TEMPLATE_REQUIRED,
        INVALID_TEMPLATE,
        TEMPLATE_CATEGORY_MISMATCH,
        WORLD_NOT_FOUND,
        NO_SAFE_SPOT,
        SNAPSHOT_FAILED,
        SCAN_IN_PROGRESS
    }

    public record CreateResult(CreateStatus status, DungeonInstance dungeon, DungeonCategory category, int count) {
        public boolean success() {
            return status == CreateStatus.CREATED && dungeon != null;
        }
    }

    private record BlockWrite(int x, int y, int z, BlockData data) {}
    private record BlockCoordinate(int x, int y, int z) {}
    private record CreateRequest(DungeonCategory category, String dungeonId, int slotNumber) {}

    private final ElytrixDungeonsPlugin plugin;
    private final File dungeonsFile;
    private final File schematicsRoot;
    private final TerrainSnapshotStore snapshotStore;
    private final SpotFinder spotFinder;
    private final DungeonAuctionBar auctionBar;
    private final NamespacedKey hologramPdc;
    private final NamespacedKey containerHintPdc;

    private final Map<String, DungeonTemplate> templatesById = new LinkedHashMap<>();
    private final Map<DungeonCategory, List<DungeonTemplate>> templatesByCategory = new EnumMap<>(DungeonCategory.class);
    private final Map<String, DungeonInstance> dungeons = new LinkedHashMap<>();
    private final Map<BlockPosKey, String> protectedContainerIndex = new HashMap<>();
    private final Map<UUID, String> houseMusicPlayers = new HashMap<>();
    private final Map<UUID, Long> houseMusicStartedAtMs = new HashMap<>();

    private boolean hiddenFromWorld = false;
    private int pendingScanTotal = 0;
    private int pendingScanDone = 0;
    private int activePasteCount = 0;

    private BukkitTask heartbeatTask;
    private BukkitTask workerTask;
    private final Deque<Runnable> jobQueue = new ArrayDeque<>();

    public DungeonManager(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.dungeonsFile = new File(plugin.getDataFolder(), "dungeons.yml");
        this.schematicsRoot = new File(plugin.getDataFolder(), "schematics");
        this.snapshotStore = new TerrainSnapshotStore(plugin.getDataFolder());
        this.spotFinder = new SpotFinder(plugin);
        this.auctionBar = new DungeonAuctionBar(plugin);
        this.hologramPdc = new NamespacedKey(plugin, "dungeon_hologram");
        this.containerHintPdc = new NamespacedKey(plugin, "container_hint");
    }

    public File categorySchemDir(DungeonCategory cat) {
        File dir = new File(schematicsRoot, cat.name().toLowerCase(Locale.ROOT));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void startup() {
        loadSchematics();
        boolean hadSaved = loadDungeonsFromDisk();
        removeLegacyDungeonDisplays();
        startHeartbeat();

        if (hadSaved) {
            // Re-place previously found dungeons onto their saved spots WITHOUT searching for new locations!
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
                    enqueueJob(() -> restoreSavedDungeonOnStartup(d));
                }
            }, 20L);
        } else if (plugin.getConfig().getBoolean("auto-spawn.startup-enabled", false)) {
            Bukkit.getScheduler().runTaskLater(plugin, this::ensureTargetDungeonCounts, 20L);
        }
    }

    /**
     * When the plugin disables / server shuts down:
     *  - Stops fights & removes all Allay ghosts and holograms
     *  - Saves locked spots & state in dungeons.yml
     *  - Restores virgin natural terrain across all 14 dungeons so if the plugin stays disabled/removed,
     *    zero traces of any dungeon remain in the world!
     */
    public void shutdown() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        if (workerTask != null) {
            workerTask.cancel();
            workerTask = null;
        }
        jobQueue.clear();
        stopHouseMusic();
        plugin.getTrialController().shutdownAll();
        plugin.getGhostController().shutdownAll(dungeons.values());
        saveDungeons();

        if (plugin.getConfig().getBoolean("auto-spawn.hide-dungeons-on-disable", true) && !hiddenFromWorld) {
            for (DungeonInstance d : dungeons.values()) {
                removeHologram(d);
                removeContainerHolograms(d);
                try {
                    snapshotStore.restoreWorldSnapshotInstant(d.getId());
                } catch (IOException ex) {
                    plugin.getLogger().severe("Не удалось восстановить исходный рельеф для " + d.getId() + ": " + ex.getMessage());
                }
            }
            hiddenFromWorld = true;
        }
    }

    public void hideAllDungeonsNow() {
        stopHouseMusic();
        plugin.getTrialController().shutdownAll();
        plugin.getGhostController().shutdownAll(dungeons.values());
        for (DungeonInstance d : dungeons.values()) {
            removeHologram(d);
            removeContainerHolograms(d);
            try {
                snapshotStore.restoreWorldSnapshotInstant(d.getId());
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось восстановить исходный рельеф для " + d.getId() + ": " + ex.getMessage());
            }
        }
        hiddenFromWorld = true;
        saveDungeons();
    }

    public void showAllDungeonsNow() {
        hiddenFromWorld = false;
        for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
            enqueueJob(() -> restoreSavedDungeonOnStartup(d));
        }
    }

    public boolean isHiddenFromWorld() { return hiddenFromWorld; }
    public int getPendingScanDone() { return pendingScanDone; }
    public int getPendingScanTotal() { return pendingScanTotal; }
    public boolean isScanning() { return pendingScanTotal > 0 && pendingScanDone < pendingScanTotal; }
    public boolean isPlacementInProgress() { return activePasteCount > 0; }

    public void loadSchematics() {
        templatesById.clear();
        for (DungeonCategory category : DungeonCategory.values()) {
            templatesByCategory.put(category, new ArrayList<>());
            File[] files = categorySchemDir(category).listFiles();
            if (files == null) continue;
            Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".schem") && !name.endsWith(".schematic")) continue;
                try {
                    DungeonTemplate template = SpongeSchematicCodec.readSchem(file, category);
                    templatesById.put(template.getId(), template);
                    templatesByCategory.get(category).add(template);
                } catch (Exception ex) {
                    plugin.getLogger().severe("Не удалось загрузить схему " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private boolean loadDungeonsFromDisk() {
        dungeons.clear();
        protectedContainerIndex.clear();
        if (!dungeonsFile.exists()) return false;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dungeonsFile);
        ConfigurationSection sec = yml.getConfigurationSection("dungeons");
        if (sec == null || sec.getKeys(false).isEmpty()) return false;

        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);
        boolean allowBreak = plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);

        for (String id : sec.getKeys(false)) {
            ConfigurationSection d = sec.getConfigurationSection(id);
            if (d == null) continue;
            BlockPosKey origin = BlockPosKey.deserialize(d.getString("origin"));
            DungeonCategory cat;
            try {
                cat = DungeonCategory.valueOf(d.getString("category", "SURFACE"));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            DungeonTemplate tpl = resolveTemplateOrCategoryFallback(d.getString("template", ""), cat);
            if (origin == null || tpl == null) continue;

            CuboidRegion structBounds = new CuboidRegion(
                    origin.worldName(),
                    d.getInt("bounds.min-x"), d.getInt("bounds.min-y"), d.getInt("bounds.min-z"),
                    d.getInt("bounds.max-x"), d.getInt("bounds.max-y"), d.getInt("bounds.max-z")
            );
            CuboidRegion snapBounds = d.isConfigurationSection("snapshot-bounds")
                    ? new CuboidRegion(
                            origin.worldName(),
                            d.getInt("snapshot-bounds.min-x"), d.getInt("snapshot-bounds.min-y"), d.getInt("snapshot-bounds.min-z"),
                            d.getInt("snapshot-bounds.max-x"), d.getInt("snapshot-bounds.max-y"), d.getInt("snapshot-bounds.max-z"))
                    : structBounds.expand(12, 8);

            DungeonInstance inst = new DungeonInstance(
                    id,
                    d.getString("display-name", cat.getTitle()),
                    tpl.getId(),
                    cat,
                    origin,
                    structBounds,
                    snapBounds,
                    d.getInt("surface-y", origin.y()),
                    d.getLong("next-reset-ms", System.currentTimeMillis() + resetIntervalMs())
            );
            inst.setGhostRespawnAtMs(d.getLong("ghost-respawn-ms", 0L));
            try {
                DungeonStatus st = DungeonStatus.valueOf(d.getString("status", "READY"));
                inst.setStatus(st == DungeonStatus.CLEARED ? DungeonStatus.CLEARED : DungeonStatus.READY);
            } catch (IllegalArgumentException ignored) {
            }

            inst.applyTemplateMarkers(tpl);
            for (String unlockedKey : d.getStringList("unlocked-shulkers")) {
                BlockPosKey pk = BlockPosKey.deserialize(unlockedKey);
                if (pk != null && inst.getShulkerUnlocked().containsKey(pk)) {
                    inst.getShulkerUnlocked().put(pk, Boolean.TRUE);
                }
            }
            if (cat == DungeonCategory.SURFACE) {
                for (String state : d.getStringList("surface-shulkers")) {
                    String[] parts = state.split("\\|", 4);
                    if (parts.length < 3) continue;
                    BlockPosKey pos = BlockPosKey.deserialize(parts[0]);
                    SurfaceShulkerRarity rarity = SurfaceShulkerRarity.fromName(parts[1]);
                    if (pos == null || rarity == null || !inst.getShulkerPositions().contains(pos)) continue;
                    try {
                        inst.getSurfaceShulkerRarities().put(pos, rarity.name());
                        inst.getSurfaceShulkerRefreshAtMs().put(pos, Long.parseLong(parts[2]));
                        if (parts.length >= 4 && Boolean.parseBoolean(parts[3])) {
                            inst.getPendingSurfaceShulkerRefresh().add(pos);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            registerDungeonRuntime(inst, pad, allowPvp, allowBreak);
        }
        return !dungeons.isEmpty();
    }

    private void restoreSavedDungeonOnStartup(DungeonInstance inst) {
        DungeonTemplate tpl = resolveTemplateOrCategoryFallback(inst.getTemplateId(), inst.getCategory());
        World w = inst.getOrigin().world();
        if (tpl == null || w == null) return;
        if (!snapshotStore.hasSnapshot(inst.getId())) {
            try {
                snapshotStore.saveWorldSnapshot(inst.getId(), w, inst.getSnapshotBounds());
            } catch (IOException ex) {
                plugin.getLogger().severe("Не удалось сохранить исходный рельеф для " + inst.getId() + ": " + ex.getMessage());
                return;
            }
        }
        DungeonStatus savedStatus = inst.getStatus();
        Map<BlockPosKey, Boolean> savedShulkers = new LinkedHashMap<>(inst.getShulkerUnlocked());
        pasteWithRestoreDiffsBatched(w, inst, tpl, () -> {
            inst.setStatus(savedStatus);
            inst.getShulkerUnlocked().putAll(savedShulkers);
        }, true);
    }

    private DungeonTemplate resolveTemplateOrCategoryFallback(String tplId, DungeonCategory cat) {
        DungeonTemplate direct = templatesById.get(tplId);
        if (direct != null) return direct;
        List<DungeonTemplate> list = templatesByCategory.get(cat);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    public boolean saveDungeons() {
        YamlConfiguration yml = new YamlConfiguration();
        for (DungeonInstance d : dungeons.values()) {
            String p = "dungeons." + d.getId();
            yml.set(p + ".display-name", d.getDisplayName());
            yml.set(p + ".template", d.getTemplateId());
            yml.set(p + ".category", d.getCategory().name());
            yml.set(p + ".origin", d.getOrigin().serialize());
            yml.set(p + ".surface-y", d.getSurfaceY());
            yml.set(p + ".status", d.getStatus().name());
            yml.set(p + ".next-reset-ms", d.getNextResetEpochMs());
            yml.set(p + ".ghost-respawn-ms", d.getGhostRespawnAtMs());
            writeRegion(yml, p + ".bounds", d.getBounds());
            writeRegion(yml, p + ".snapshot-bounds", d.getSnapshotBounds());

            List<String> unlocked = new ArrayList<>();
            for (Map.Entry<BlockPosKey, Boolean> e : d.getShulkerUnlocked().entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) unlocked.add(e.getKey().serialize());
            }
            yml.set(p + ".unlocked-shulkers", unlocked);

            List<String> surfaceShulkers = new ArrayList<>();
            if (d.getCategory() == DungeonCategory.SURFACE) {
                for (BlockPosKey pos : d.getShulkerPositions()) {
                    String rarity = d.getSurfaceShulkerRarities().get(pos);
                    Long refreshAt = d.getSurfaceShulkerRefreshAtMs().get(pos);
                    if (rarity == null || refreshAt == null) continue;
                    boolean pending = d.getPendingSurfaceShulkerRefresh().contains(pos);
                    surfaceShulkers.add(pos.serialize() + "|" + rarity + "|" + refreshAt + "|" + pending);
                }
            }
            yml.set(p + ".surface-shulkers", surfaceShulkers);
        }
        File tempFile = new File(plugin.getDataFolder(), "dungeons.yml.tmp");
        try {
            yml.save(tempFile);
            try {
                Files.move(tempFile.toPath(), dungeonsFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile.toPath(), dungeonsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить dungeons.yml: " + ex.getMessage());
            return false;
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    private static void writeRegion(YamlConfiguration yml, String path, CuboidRegion b) {
        yml.set(path + ".min-x", b.minX());
        yml.set(path + ".min-y", b.minY());
        yml.set(path + ".min-z", b.minZ());
        yml.set(path + ".max-x", b.maxX());
        yml.set(path + ".max-y", b.maxY());
        yml.set(path + ".max-z", b.maxZ());
    }

    /**
     * Wipes old dungeons cleanly back to natural terrain, reloads schematics/{surface,underground}/,
     * and scans fresh ideal spots across ungenerated chunks: 7 SURFACE (4 + 3) & 7 UNDERGROUND (4 + 3)!
     */
    public void rebuildAllFromSchematics() {
        if (isPlacementInProgress()) {
            enqueueJob(this::rebuildAllFromSchematics);
            return;
        }
        hiddenFromWorld = false;
        jobQueue.clear();
        pendingScanTotal = pendingScanDone;
        for (String id : new ArrayList<>(dungeons.keySet())) {
            removeDungeon(id, true);
        }
        loadSchematics();
        ensureTargetDungeonCounts();
    }

    public void ensureTargetDungeonCounts() {
        hiddenFromWorld = false;
        String worldName = plugin.getConfig().getString("world-settings.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) return;

        int surfaceTarget = Math.min(MAX_DUNGEONS_PER_CATEGORY,
                Math.max(0, plugin.getConfig().getInt("auto-spawn.surface-count", MAX_DUNGEONS_PER_CATEGORY)));
        int undergroundTarget = Math.min(MAX_DUNGEONS_PER_CATEGORY,
                Math.max(0, plugin.getConfig().getInt("auto-spawn.underground-count", MAX_DUNGEONS_PER_CATEGORY)));
        int needSurface = Math.max(0, surfaceTarget - countByCategory(DungeonCategory.SURFACE));
        int needUg = Math.max(0, undergroundTarget - countByCategory(DungeonCategory.UNDERGROUND));
        pendingScanDone = 0;
        pendingScanTotal = needSurface + needUg;

        scheduleCategory(world, DungeonCategory.SURFACE, surfaceTarget);
        scheduleCategory(world, DungeonCategory.UNDERGROUND, undergroundTarget);
    }

    /**
     * Round-robin across the administrator's sorted .schem files if mass placement is explicitly enabled.
     * With two loaded templates and a target of seven, the first is used four times and the second three times.
     */
    private void scheduleCategory(World world, DungeonCategory cat, int targetCount) {
        targetCount = Math.min(MAX_DUNGEONS_PER_CATEGORY, Math.max(0, targetCount));
        List<DungeonTemplate> available = templatesByCategory.get(cat);
        int have = countByCategory(cat);
        int need = Math.max(0, targetCount - have);
        if (available == null || available.isEmpty()) {
            pendingScanDone += need;
            return;
        }
        for (int i = 0; i < need; i++) {
            int number = nextFreeNumber(cat, i);
            if (number > MAX_DUNGEONS_PER_CATEGORY) break;
            int slotIndex = number - 1;
            DungeonTemplate chosen = available.get(slotIndex % available.size());
            String id = cat.name().toLowerCase(Locale.ROOT) + "_" + number;
            enqueueJob(() -> scanAndPlaceSingle(world, id, chosen));
        }
    }

    private int nextFreeNumber(DungeonCategory cat, int offset) {
        int found = 0;
        String prefix = cat.name().toLowerCase(Locale.ROOT) + "_";
        for (int candidate = 1; candidate <= MAX_DUNGEONS_PER_CATEGORY; candidate++) {
            if (!dungeons.containsKey(prefix + candidate)) {
                if (found == offset) return candidate;
                found++;
            }
        }
        return MAX_DUNGEONS_PER_CATEGORY + 1;
    }

    public int countByCategory(DungeonCategory cat) {
        int c = 0;
        for (DungeonInstance d : dungeons.values()) {
            if (d.getCategory() == cat) c++;
        }
        return c;
    }

    /** Creates one dungeon from the administrator's explicit category and loaded schematic selection. */
    public CreateResult createDungeonFromCommand(String rawId, String rawTemplateId) {
        CreateRequest request = parseCreateRequest(rawId);
        if (request == null) {
            return new CreateResult(CreateStatus.INVALID_ID, null, null, 0);
        }

        DungeonCategory category = request.category();
        int count = countByCategory(category);
        if (request.slotNumber() > MAX_DUNGEONS_PER_CATEGORY || count >= MAX_DUNGEONS_PER_CATEGORY) {
            return new CreateResult(CreateStatus.MAX_REACHED, null, category, count);
        }
        if (isScanning() || isPlacementInProgress()) {
            return new CreateResult(CreateStatus.SCAN_IN_PROGRESS, null, category, count);
        }
        if (dungeons.containsKey(request.dungeonId())) {
            return new CreateResult(CreateStatus.ID_ALREADY_EXISTS, null, category, count);
        }

        List<DungeonTemplate> available = templatesByCategory.get(category);
        if (available == null || available.isEmpty()) {
            return new CreateResult(CreateStatus.MISSING_SCHEMATIC, null, category, count);
        }
        if (rawTemplateId == null || rawTemplateId.isBlank()) {
            return new CreateResult(CreateStatus.TEMPLATE_REQUIRED, null, category, count);
        }
        DungeonTemplate template = getTemplate(category, rawTemplateId);
        if (template == null) {
            DungeonTemplate otherCategoryTemplate = getTemplate(rawTemplateId);
            return new CreateResult(otherCategoryTemplate == null
                    ? CreateStatus.INVALID_TEMPLATE : CreateStatus.TEMPLATE_CATEGORY_MISMATCH, null, category, count);
        }

        String worldName = plugin.getConfig().getString("world-settings.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return new CreateResult(CreateStatus.WORLD_NOT_FOUND, null, category, count);
        }

        SpotFinder.PlacementPlan plan = spotFinder.findPlacement(world, template, dungeons.values(), request.dungeonId());
        if (plan == null) {
            return new CreateResult(CreateStatus.NO_SAFE_SPOT, null, category, count);
        }

        DungeonInstance created = placeDungeonWithSnapshot(
                request.dungeonId(), template, plan.origin(), plan.structureBounds(), plan.snapshotBounds(), plan.surfaceY(), true);
        if (created == null) {
            return new CreateResult(CreateStatus.SNAPSHOT_FAILED, null, category, count);
        }
        return new CreateResult(CreateStatus.CREATED, created, category, count + 1);
    }

    private CreateRequest parseCreateRequest(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        String input = rawId.trim().toLowerCase(Locale.ROOT);

        if (input.equals("surface")) return nextCreateRequest(DungeonCategory.SURFACE);
        if (input.equals("underground")) return nextCreateRequest(DungeonCategory.UNDERGROUND);

        int separator = input.indexOf('_');
        if (separator <= 0) return null;
        DungeonCategory category = parseCategory(input.substring(0, separator));
        if (category == null) return null;

        String suffix = input.substring(separator + 1);
        if (suffix.isEmpty()) return null;
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) return null;
        }
        int number;
        try {
            number = Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            number = MAX_DUNGEONS_PER_CATEGORY + 1;
        }
        if (number < 1) return null;
        String id = category.name().toLowerCase(Locale.ROOT) + "_" + number;
        return new CreateRequest(category, id, number);
    }

    private CreateRequest nextCreateRequest(DungeonCategory category) {
        int number = nextAvailableDungeonNumber(category);
        if (number < 1) number = MAX_DUNGEONS_PER_CATEGORY + 1;
        String id = category.name().toLowerCase(Locale.ROOT) + "_" + number;
        return new CreateRequest(category, id, number);
    }

    private int nextAvailableDungeonNumber(DungeonCategory category) {
        String prefix = category.name().toLowerCase(Locale.ROOT) + "_";
        for (int number = 1; number <= MAX_DUNGEONS_PER_CATEGORY; number++) {
            if (!dungeons.containsKey(prefix + number)) return number;
        }
        return -1;
    }

    private static DungeonCategory parseCategory(String input) {
        return switch (input) {
            case "surface" -> DungeonCategory.SURFACE;
            case "underground" -> DungeonCategory.UNDERGROUND;
            default -> null;
        };
    }

    private void scanAndPlaceSingle(World world, String id, DungeonTemplate tpl) {
        pendingScanDone++;
        if (dungeons.containsKey(id) || countByCategory(tpl.getCategory()) >= MAX_DUNGEONS_PER_CATEGORY) return;
        SpotFinder.PlacementPlan plan = spotFinder.findPlacement(world, tpl, dungeons.values(), id);
        if (plan == null) return;
        placeDungeonWithSnapshot(
                id, tpl, plan.origin(), plan.structureBounds(), plan.snapshotBounds(), plan.surfaceY(), true);
    }

    public DungeonInstance manualSpawnHere(Player admin, DungeonTemplate tpl) {
        if (admin == null || tpl == null || isScanning() || isPlacementInProgress()
                || countByCategory(tpl.getCategory()) >= MAX_DUNGEONS_PER_CATEGORY) {
            return null;
        }
        int number = nextAvailableDungeonNumber(tpl.getCategory());
        if (number < 1) return null;
        String id = tpl.getCategory().name().toLowerCase(Locale.ROOT) + "_" + number;

        World w = admin.getWorld();
        Location loc = admin.getLocation();
        int ox = loc.getBlockX() - tpl.getSizeX() / 2;
        int oy = loc.getBlockY() - 1;
        int oz = loc.getBlockZ() - tpl.getSizeZ() / 2;
        int surfY = w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int bufXZ = plugin.getConfig().getInt("protection.surround-restore-padding-xz", 12);
        int bufY = plugin.getConfig().getInt("protection.surround-restore-padding-y", 8);

        CuboidRegion structBox = new CuboidRegion(w.getName(), ox, oy, oz, ox + tpl.getSizeX() - 1, oy + tpl.getSizeY() - 1, oz + tpl.getSizeZ() - 1);
        int snapTop = tpl.getCategory() == DungeonCategory.UNDERGROUND ? Math.max(structBox.maxY() + bufY, surfY + 6) : structBox.maxY() + bufY;
        CuboidRegion snapBox = new CuboidRegion(w.getName(), ox - bufXZ, Math.max(w.getMinHeight(), oy - bufY), oz - bufXZ,
                structBox.maxX() + bufXZ, Math.min(w.getMaxHeight() - 1, snapTop), structBox.maxZ() + bufXZ);

        int claimPadding = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
        for (DungeonInstance existing : dungeons.values()) {
            if (!existing.getOrigin().worldName().equals(w.getName())) continue;
            if (existing.getSnapshotBounds().intersects3D(snapBox)
                    || existing.claimGuardRegion(claimPadding).intersects3D(snapBox)) {
                return null;
            }
        }
        if (plugin.getWorldGuardHook().hasForeignRegions(w, snapBox.expand(claimPadding, 6))) {
            return null;
        }

        return placeDungeonWithSnapshot(id, tpl, new BlockPosKey(w.getName(), ox, oy, oz), structBox, snapBox, surfY, true);
    }

    private DungeonInstance placeDungeonWithSnapshot(
            String id,
            DungeonTemplate tpl,
            BlockPosKey origin,
            CuboidRegion structBounds,
            CuboidRegion snapBounds,
            int surfaceY,
            boolean saveTerrainFirst
    ) {
        World w = origin.world();
        if (w == null) return null;
        if (saveTerrainFirst) {
            try {
                snapshotStore.saveWorldSnapshot(id, w, snapBounds);
            } catch (IOException ex) {
                snapshotStore.deleteSnapshot(id);
                plugin.getLogger().severe("Не удалось сохранить исходный рельеф для " + id + ": " + ex.getMessage());
                return null;
            }
        }
        DungeonInstance inst = new DungeonInstance(
                id,
                tpl.getDisplayName(),
                tpl.getId(),
                tpl.getCategory(),
                origin,
                structBounds,
                snapBounds,
                surfaceY,
                System.currentTimeMillis() + resetIntervalMs()
        );
        inst.applyTemplateMarkers(tpl);

        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);
        boolean allowBreak = plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);
        registerDungeonRuntime(inst, pad, allowPvp, allowBreak);

        pasteWithRestoreDiffsBatched(w, inst, tpl, null, false);
        saveDungeons();
        return inst;
    }

    public void syncAllWorldGuardRegions() {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);
        boolean allowBreak = plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);
        for (DungeonInstance d : dungeons.values()) {
            plugin.getWorldGuardHook().syncDungeonRegion(d, pad, allowPvp, allowBreak);
        }
    }

    private void registerDungeonRuntime(DungeonInstance inst, int pad, boolean allowPvp, boolean allowBreak) {
        dungeons.put(inst.getId(), inst);
        reindexProtectedContainers(inst);
        plugin.getWorldGuardHook().syncDungeonRegion(inst, pad, allowPvp, allowBreak);
    }

    public void reindexProtectedContainers(DungeonInstance inst) {
        protectedContainerIndex.entrySet().removeIf(e -> e.getValue().equals(inst.getId()));
        for (BlockPosKey p : inst.getChestPositions()) protectedContainerIndex.put(p, inst.getId());
        for (BlockPosKey p : inst.getBarrelPositions()) protectedContainerIndex.put(p, inst.getId());
        for (BlockPosKey p : inst.getShulkerPositions()) protectedContainerIndex.put(p, inst.getId());
    }

    public void regenerateDungeon(DungeonInstance inst, boolean forceRelocate) {
        if (inst == null || dungeons.get(inst.getId()) != inst) return;
        DungeonTemplate tpl = resolveTemplateOrCategoryFallback(inst.getTemplateId(), inst.getCategory());
        World w = inst.getOrigin().world();
        if (tpl == null || w == null || inst.getStatus() == DungeonStatus.REGENERATING) {
            return;
        }
        plugin.getTrialController().abortFight(inst.getId(), false);
        plugin.getGhostController().clearHouseGhosts(inst.getId());
        removeContainerHolograms(inst);
        inst.setStatus(DungeonStatus.REGENERATING);

        boolean relocate = forceRelocate || plugin.getConfig().getBoolean("regeneration.relocate-on-reset", false);
        if (relocate) {
            removeHologram(inst);
            plugin.getWorldGuardHook().removeDungeonRegion(w, inst.getId());
            try {
                snapshotStore.restoreWorldSnapshotInstant(inst.getId());
            } catch (IOException ignored) {
            }
            SpotFinder.PlacementPlan newPlan = spotFinder.findPlacement(w, tpl, dungeons.values(), inst.getId());
            if (newPlan != null) {
                try {
                    snapshotStore.saveWorldSnapshot(inst.getId(), w, newPlan.snapshotBounds());
                } catch (IOException ignored) {
                }
                inst.setOrigin(newPlan.origin());
                inst.setBounds(newPlan.structureBounds());
                inst.setSnapshotBounds(newPlan.snapshotBounds());
                inst.setSurfaceY(newPlan.surfaceY());
            }
            int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
            plugin.getWorldGuardHook().syncDungeonRegion(inst, pad,
                    plugin.getConfig().getBoolean("protection.allow-pvp", true),
                    plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true));
        }

        inst.applyTemplateMarkers(tpl);
        reindexProtectedContainers(inst);
        inst.setNextResetEpochMs(System.currentTimeMillis() + resetIntervalMs());
        pasteWithRestoreDiffsBatched(w, inst, tpl, null, false);
        saveDungeons();
    }

    public boolean removeDungeon(String id, boolean restoreTerrain) {
        if (id == null || isScanning() || isPlacementInProgress()) return false;
        DungeonInstance inst = dungeons.get(id);
        if (inst == null || inst.getStatus() == DungeonStatus.REGENERATING) return false;

        World world = inst.getOrigin().world();
        if (restoreTerrain) {
            if (world == null) {
                plugin.getLogger().severe("Не удалось удалить данж " + id + ": мир данжа не загружен.");
                return false;
            }
            try {
                if (!snapshotStore.restoreWorldSnapshotInstant(id, inst.getOrigin().worldName())) {
                    plugin.getLogger().severe("Не удалось удалить данж " + id + ": снимок рельефа недоступен.");
                    return false;
                }
            } catch (IOException | RuntimeException ex) {
                plugin.getLogger().severe("Не удалось восстановить рельеф для " + id + "; данж оставлен: " + ex.getMessage());
                return false;
            }
        }

        if (restoreTerrain) evacuatePlayersFromDungeon(inst, world);
        plugin.getTrialController().abortFight(id, false);
        plugin.getGhostController().clearHouseGhosts(id);
        stopHouseMusicForDungeon(id);
        removeHologram(inst);
        removeContainerHolograms(inst);
        plugin.getWorldGuardHook().removeDungeonRegion(world, id);
        dungeons.remove(id);
        protectedContainerIndex.entrySet().removeIf(e -> e.getValue().equals(id));

        if (!saveDungeons()) {
            dungeons.put(id, inst);
            reindexProtectedContainers(inst);
            int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
            plugin.getWorldGuardHook().syncDungeonRegion(inst, pad,
                    plugin.getConfig().getBoolean("protection.allow-pvp", true),
                    plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true));
            if (restoreTerrain) enqueueJob(() -> restoreSavedDungeonOnStartup(inst));
            return false;
        }

        snapshotStore.deleteSnapshot(id);
        return true;
    }

    /**
     * Combines BOTH:
     *   1) Full surround-terrain restoration diffs (wipes ALL nearby grief craters, cobble, tunnels, water/lava!)
     *   2) Fresh .schem paste (skipping structure_void so curved caves blend naturally!)
     * Executed smoothly across ticks without lagging TPS!
     */
    private void pasteWithRestoreDiffsBatched(
            World world,
            DungeonInstance inst,
            DungeonTemplate tpl,
            Runnable postHook,
            boolean preserveSurfaceShulkerState
    ) {
        TerrainSnapshotStore.ensureChunksLoaded(world, inst.getSnapshotBounds());
        List<BlockWrite> writes = new ArrayList<>();

        if (snapshotStore.hasSnapshot(inst.getId())) {
            try {
                for (BlockRestoreDiff diff : snapshotStore.collectRestoreDiffs(inst.getId())) {
                    writes.add(new BlockWrite(diff.x(), diff.y(), diff.z(), diff.data()));
                }
            } catch (IOException ignored) {
            }
        }

        Map<String, BlockData> bdCache = new HashMap<>();
        int ox = inst.getOrigin().x(), oy = inst.getOrigin().y(), oz = inst.getOrigin().z();

        // Solid foundation under Surface House so zero gaps exist on slight 1–3 block terrain slopes
        if (inst.getCategory() == DungeonCategory.SURFACE) {
            BlockData stoneBricks = Bukkit.createBlockData(Material.STONE_BRICKS);
            BlockData cobble = Bukkit.createBlockData(Material.COBBLESTONE);
            for (int x = 1; x <= tpl.getSizeX() - 2; x++) {
                for (int z = 1; z <= tpl.getSizeZ() - 2; z++) {
                    for (int dy = -4; dy <= -1; dy++) {
                        writes.add(new BlockWrite(ox + x, oy + dy, oz + z, dy == -1 ? stoneBricks : cobble));
                    }
                }
            }
            addSurfaceLandscapingWrites(world, inst, tpl, writes);
        }

        for (int y = 0; y < tpl.getSizeY(); y++) {
            for (int z = 0; z < tpl.getSizeZ(); z++) {
                for (int x = 0; x < tpl.getSizeX(); x++) {
                    String state = tpl.getBlock(x, y, z);
                    if (state.endsWith("structure_void")) {
                        continue;
                    }
                    BlockData bd = bdCache.computeIfAbsent(state, s -> {
                        try {
                            return Bukkit.createBlockData(s);
                        } catch (IllegalArgumentException ex) {
                            return Bukkit.createBlockData(Material.AIR);
                        }
                    });
                    writes.add(new BlockWrite(ox + x, oy + y, oz + z, bd));
                }
            }
        }

        int perTick = Math.max(300, plugin.getConfig().getInt("auto-spawn.blocks-per-tick", 1200));
        activePasteCount++;
        new BukkitRunnable() {
            private int idx = 0;

            @Override
            public void run() {
                int end = Math.min(writes.size(), idx + perTick);
                while (idx < end) {
                    BlockWrite bw = writes.get(idx++);
                    Block b = world.getBlockAt(bw.x, bw.y, bw.z);
                    if (b.getState() instanceof InventoryHolder holder) {
                        holder.getInventory().clear();
                    }
                    b.setBlockData(bw.data, false);
                }
                if (idx >= writes.size()) {
                    cancel();
                    try {
                        finalizeDungeonAfterPaste(world, inst, preserveSurfaceShulkerState);
                        if (postHook != null) postHook.run();
                    } finally {
                        activePasteCount = Math.max(0, activePasteCount - 1);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void addSurfaceLandscapingWrites(
            World world, DungeonInstance inst, DungeonTemplate tpl, List<BlockWrite> writes
    ) {
        if (!plugin.getConfig().getBoolean("surface-dungeon.landscaping.enabled", true)) {
            return;
        }

        int ox = inst.getOrigin().x(), oz = inst.getOrigin().z();
        int minX = ox, maxX = ox + tpl.getSizeX() - 1;
        int minZ = oz, maxZ = oz + tpl.getSizeZ() - 1;
        CuboidRegion snapshot = inst.getSnapshotBounds();
        int availablePadding = Math.min(
                Math.min(minX - snapshot.minX(), snapshot.maxX() - maxX),
                Math.min(minZ - snapshot.minZ(), snapshot.maxZ() - maxZ));
        int radius = Math.min(Math.max(0, plugin.getConfig().getInt("surface-dungeon.landscaping.radius", 6)),
                Math.max(0, availablePadding));
        if (radius == 0) return;

        int baseY = inst.getOrigin().y() - 1;
        BlockData grass = Bukkit.createBlockData(Material.GRASS_BLOCK);
        BlockData dirt = Bukkit.createBlockData(Material.DIRT);
        BlockData air = Bukkit.createBlockData(Material.AIR);
        BlockData shortGrass = Bukkit.createBlockData(Material.SHORT_GRASS);
        Map<BlockCoordinate, Material> restoredMaterials = new HashMap<>();
        for (BlockWrite write : writes) {
            if (write.x >= minX - radius && write.x <= maxX + radius
                    && write.z >= minZ - radius && write.z <= maxZ + radius
                    && write.y >= snapshot.minY() && write.y <= snapshot.maxY()) {
                restoredMaterials.put(new BlockCoordinate(write.x, write.y, write.z), write.data.getMaterial());
            }
        }
        for (int x = minX - radius; x <= maxX + radius; x++) {
            for (int z = minZ - radius; z <= maxZ + radius; z++) {
                int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
                int dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
                int distance = Math.max(dx, dz);
                if (distance < 1 || distance > radius) continue;

                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                int worldTop = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (worldTop > snapshot.maxY()) continue;
                int scanTop = Math.min(snapshot.maxY(), Math.max(worldTop, baseY + radius + 6));
                int scanMin = Math.max(world.getMinHeight(), snapshot.minY());
                int groundY = Integer.MIN_VALUE;
                Material groundType = null;
                boolean obstructed = false;
                for (int y = scanTop; y >= scanMin; y--) {
                    Material material = terrainMaterialAt(world, restoredMaterials, x, y, z);
                    if (material.isAir() || material.name().endsWith("_LEAVES") || !material.isSolid()) {
                        continue;
                    }
                    if (!isNaturalTerrain(material)) {
                        obstructed = true;
                        break;
                    }
                    groundY = y;
                    groundType = material;
                    break;
                }
                if (obstructed || groundY == Integer.MIN_VALUE || groundType == null
                        || groundY <= world.getMinHeight() || Math.abs(groundY - baseY) > radius + 6) continue;

                int targetY = baseY + (int) Math.round((groundY - baseY) * (distance / (double) radius));
                int noise = Math.floorMod((x * 73_428_767) ^ (z * 912_931) ^ inst.getId().hashCode(), 19);
                if (distance < radius && (groundType == Material.GRASS_BLOCK || groundType == Material.DIRT
                        || groundType == Material.COARSE_DIRT || groundType == Material.PODZOL)) {
                    if (noise == 0 || noise == 1) targetY++;
                    else if (noise == 2) targetY--;
                }

                boolean safeColumn = true;
                if (targetY > groundY) {
                    for (int y = groundY + 1; y <= targetY; y++) {
                        if (!terrainMaterialAt(world, restoredMaterials, x, y, z).isAir()) {
                            safeColumn = false;
                            break;
                        }
                    }
                    if (!safeColumn) continue;
                    for (int y = groundY + 1; y < targetY; y++) {
                        writes.add(new BlockWrite(x, y, z, dirt));
                    }
                    writes.add(new BlockWrite(x, targetY, z, grass));
                } else if (targetY < groundY) {
                    for (int y = targetY + 1; y <= groundY; y++) {
                        if (!isNaturalTerrain(terrainMaterialAt(world, restoredMaterials, x, y, z))) {
                            safeColumn = false;
                            break;
                        }
                    }
                    if (!safeColumn) continue;
                    for (int y = targetY + 1; y <= groundY; y++) {
                        writes.add(new BlockWrite(x, y, z, air));
                    }
                    writes.add(new BlockWrite(x, targetY, z, grass));
                }
                if (targetY == groundY && groundType == Material.GRASS_BLOCK && noise == 3
                        && terrainMaterialAt(world, restoredMaterials, x, targetY + 1, z).isAir()) {
                    // Scatter a few tufts through the transition band so the edge reads as terrain.
                    writes.add(new BlockWrite(x, targetY + 1, z, shortGrass));
                }
            }
        }
    }

    private static Material terrainMaterialAt(
            World world, Map<BlockCoordinate, Material> restoredMaterials, int x, int y, int z
    ) {
        Material restored = restoredMaterials.get(new BlockCoordinate(x, y, z));
        return restored != null ? restored : world.getBlockAt(x, y, z).getType();
    }

    private static boolean isNaturalTerrain(Material material) {
        return material == Material.GRASS_BLOCK || material == Material.DIRT
                || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT
                || material == Material.PODZOL || material == Material.MYCELIUM
                || material == Material.STONE || material == Material.GRAVEL || material == Material.SAND
                || material == Material.RED_SAND || material == Material.MOSS_BLOCK;
    }

    private void finalizeDungeonAfterPaste(World world, DungeonInstance inst, boolean preserveSurfaceShulkerState) {
        inst.setStatus(DungeonStatus.READY);
        inst.clearOpenedContainers();
        removeContainerHolograms(inst);

        if (inst.getCategory() == DungeonCategory.SURFACE) {
            int chestStacks = plugin.getConfig().getInt("surface-dungeon.chest-stacks", 6);
            for (BlockPosKey p : inst.getChestPositions()) {
                Block b = world.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.SURFACE_CHEST, chestStacks);
                }
            }
            for (BlockPosKey p : inst.getBarrelPositions()) {
                Block b = world.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.SURFACE_CHEST, chestStacks);
                }
            }
            fillSurfaceShulkerContainers(inst, world, preserveSurfaceShulkerState);
            plugin.getGhostController().populateInitialGhosts(inst);
        } else {
            int sideStacks = plugin.getConfig().getInt("underground-dungeon.side-container-stacks", 4);
            for (BlockPosKey p : inst.getChestPositions()) {
                Block b = world.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.UNDERGROUND_CONTAINER, sideStacks);
                }
            }
            for (BlockPosKey p : inst.getBarrelPositions()) {
                Block b = world.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.UNDERGROUND_CONTAINER, sideStacks);
                }
            }
            for (BlockPosKey sp : inst.getShulkerUnlocked().keySet()) {
                Block b = world.getBlockAt(sp.x(), sp.y(), sp.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    holder.getInventory().clear();
                }
            }
        }

        Location safe = inst.teleportLocation();
        if (safe != null) {
            for (Player p : world.getPlayers()) {
                // Restore buffers extend beyond the house; only move players inside the actual schematic.
                if (inst.getBounds().contains(p.getLocation())) {
                    p.setFallDistance(0f);
                    p.teleport(safe);
                    p.playSound(safe, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.35f);
                    p.sendMessage(plugin.msg("dungeon-regenerated"));
                }
            }
        }
        saveDungeons();
    }

    private void fillSurfaceShulkerContainers(
            DungeonInstance dungeon, World world, boolean preserveSurfaceShulkerState
    ) {
        long now = System.currentTimeMillis();
        long refreshInterval = surfaceShulkerRefreshIntervalMs();
        int stacks = Math.max(1, plugin.getConfig().getInt("surface-dungeon.shulker-stacks", 14));

        for (BlockPosKey pos : dungeon.getShulkerPositions()) {
            SurfaceShulkerRarity rarity = SurfaceShulkerRarity.fromName(
                    dungeon.getSurfaceShulkerRarities().get(pos));
            Long refreshAt = dungeon.getSurfaceShulkerRefreshAtMs().get(pos);
            if (!preserveSurfaceShulkerState || rarity == null || refreshAt == null || refreshAt <= now) {
                rarity = SurfaceShulkerRarity.roll();
                dungeon.getSurfaceShulkerRarities().put(pos, rarity.name());
                dungeon.getSurfaceShulkerRefreshAtMs().put(pos, now + refreshInterval);
            }
            applySurfaceShulkerState(dungeon, world, pos, rarity, stacks);
        }
    }

    private void refreshSurfaceShulkerLootIfDue(DungeonInstance dungeon, long now) {
        if (dungeon.getCategory() != DungeonCategory.SURFACE || dungeon.getShulkerPositions().isEmpty()) {
            return;
        }
        World world = dungeon.getOrigin().world();
        if (world == null) return;

        boolean changed = false;
        int stacks = Math.max(1, plugin.getConfig().getInt("surface-dungeon.shulker-stacks", 14));
        long refreshInterval = surfaceShulkerRefreshIntervalMs();
        for (BlockPosKey pos : dungeon.getShulkerPositions()) {
            SurfaceShulkerRarity rarity = SurfaceShulkerRarity.fromName(
                    dungeon.getSurfaceShulkerRarities().get(pos));
            Long refreshAt = dungeon.getSurfaceShulkerRefreshAtMs().get(pos);
            if (rarity == null || refreshAt == null || refreshAt <= now) {
                rarity = SurfaceShulkerRarity.roll();
                dungeon.getSurfaceShulkerRarities().put(pos, rarity.name());
                dungeon.getSurfaceShulkerRefreshAtMs().put(pos, now + refreshInterval);
                if (world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                    applySurfaceShulkerState(dungeon, world, pos, rarity, stacks);
                } else {
                    dungeon.getPendingSurfaceShulkerRefresh().add(pos);
                }
                changed = true;
            } else if (dungeon.getPendingSurfaceShulkerRefresh().contains(pos)
                    && world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                changed |= applySurfaceShulkerState(dungeon, world, pos, rarity, stacks);
            }
        }
        if (changed) {
            saveDungeons();
        }
    }

    private boolean applySurfaceShulkerState(
            DungeonInstance dungeon, World world, BlockPosKey pos, SurfaceShulkerRarity rarity, int stacks
    ) {
        if (rarity == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            dungeon.getPendingSurfaceShulkerRefresh().add(pos);
            return false;
        }

        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getType() != rarity.getBlockMaterial()) {
            block.setType(rarity.getBlockMaterial(), false);
        }
        BlockState state = block.getState();
        if (state instanceof InventoryHolder holder) {
            plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), rarity.getLootPool(), stacks);
        }
        if (state instanceof Nameable nameable) {
            nameable.setCustomName(TextUtil.color("&7« " + rarity.getColorHex()
                    + rarity.getDisplayName() + " шалкер &7»"));
            state.update(true, false);
        }
        dungeon.getPendingSurfaceShulkerRefresh().remove(pos);
        return true;
    }

    private long surfaceShulkerRefreshIntervalMs() {
        return Math.max(60L, plugin.getConfig().getLong("surface-dungeon.shulker-refresh-seconds", 21600L)) * 1000L;
    }

    private void updateHouseMusic() {
        if (!plugin.getConfig().getBoolean("surface-dungeon.ambience.enabled", true)) {
            stopHouseMusic();
            return;
        }

        // Volume above 1 increases the positional sound radius; keep older 0.55 configs audible
        // throughout the house while allowing an administrator to choose a stronger level.
        float volume = (float) Math.max(1.5, Math.min(4.0,
                plugin.getConfig().getDouble("surface-dungeon.ambience.volume", 2.0)));
        float pitch = (float) Math.max(0.5, plugin.getConfig().getDouble("surface-dungeon.ambience.pitch", 0.85));
        long now = System.currentTimeMillis();
        Map<UUID, String> insideNow = new HashMap<>();

        for (DungeonInstance house : dungeons.values()) {
            if (house.getCategory() != DungeonCategory.SURFACE || house.getStatus() == DungeonStatus.REGENERATING) continue;
            World world = house.getOrigin().world();
            if (world == null) continue;
            for (Player player : world.getPlayers()) {
                UUID playerId = player.getUniqueId();
                if (!house.getBounds().contains(player.getLocation()) || insideNow.containsKey(playerId)) continue;
                insideNow.put(playerId, house.getId());
                String previousHouse = houseMusicPlayers.get(playerId);
                Long startedAt = houseMusicStartedAtMs.get(playerId);
                if (!house.getId().equals(previousHouse)) {
                    if (previousHouse != null) player.stopSound(Sound.MUSIC_DISC_5);
                    player.playSound(player.getLocation(), Sound.MUSIC_DISC_5, volume, pitch);
                    houseMusicStartedAtMs.put(playerId, now);
                } else if (startedAt == null || now - startedAt >= MUSIC_DISC_5_DURATION_MS) {
                    // MUSIC_DISC_5 is a finite vanilla track. Restart it at its end so the house
                    // has a continuous ambience rather than a one-shot effect.
                    player.stopSound(Sound.MUSIC_DISC_5);
                    player.playSound(player.getLocation(), Sound.MUSIC_DISC_5, volume, pitch);
                    houseMusicStartedAtMs.put(playerId, now);
                }
            }
        }

        for (UUID playerId : new ArrayList<>(houseMusicPlayers.keySet())) {
            if (!insideNow.containsKey(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) player.stopSound(Sound.MUSIC_DISC_5);
                houseMusicStartedAtMs.remove(playerId);
            }
        }
        houseMusicPlayers.clear();
        houseMusicPlayers.putAll(insideNow);
        houseMusicStartedAtMs.keySet().removeIf(playerId -> !insideNow.containsKey(playerId));
    }

    private void evacuatePlayersFromDungeon(DungeonInstance dungeon, World world) {
        Location safeLocation = world.getSpawnLocation();
        for (Player player : world.getPlayers()) {
            if (!dungeon.getBounds().contains(player.getLocation())) continue;
            player.setFallDistance(0f);
            player.teleport(safeLocation);
            player.sendMessage(plugin.prefix() + "Данж удалён, вы перемещены на безопасное место.");
        }
    }

    private void stopHouseMusicForDungeon(String dungeonId) {
        for (Map.Entry<UUID, String> entry : new ArrayList<>(houseMusicPlayers.entrySet())) {
            if (!dungeonId.equals(entry.getValue())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.stopSound(Sound.MUSIC_DISC_5);
            houseMusicPlayers.remove(entry.getKey());
            houseMusicStartedAtMs.remove(entry.getKey());
        }
    }

    private void stopHouseMusic() {
        for (UUID playerId : houseMusicPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.stopSound(Sound.MUSIC_DISC_5);
        }
        houseMusicPlayers.clear();
        houseMusicStartedAtMs.clear();
    }

    private void startHeartbeat() {
        this.heartbeatTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (hiddenFromWorld) {
                    stopHouseMusic();
                    return;
                }
                long now = System.currentTimeMillis();
                updateHouseMusic();
                plugin.getTrialController().tickOncePerSecond();
                plugin.getGhostController().tickHousesOncePerSecond(dungeons.values());

                for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
                    if (d.getStatus() != DungeonStatus.REGENERATING && now >= d.getNextResetEpochMs()) {
                        enqueueJob(() -> regenerateDungeon(d, false));
                        continue;
                    }
                    World w = d.getOrigin().world();
                    if (d.getCategory() == DungeonCategory.SURFACE
                            && d.getStatus() != DungeonStatus.REGENERATING && activePasteCount == 0) {
                        refreshSurfaceShulkerLootIfDue(d, now);
                    }
                }
                auctionBar.tick(dungeons.values(), now);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void enqueueJob(Runnable job) {
        jobQueue.addLast(job);
        if (workerTask == null || workerTask.isCancelled()) {
            long stepTicks = Math.max(2L, plugin.getConfig().getLong("auto-spawn.scan-interval-ticks", 4L));
            workerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (activePasteCount > 0) return;
                    Runnable next = jobQueue.pollFirst();
                    if (next == null) {
                        cancel();
                        workerTask = null;
                        return;
                    }
                    next.run();
                }
            }.runTaskTimer(plugin, 2L, stepTicks);
        }
    }

    /** Compatibility entry point: dungeon TextDisplay holograms are retired. */
    public void updateDungeonHologram(DungeonInstance dungeon) {
        removeHologram(dungeon);
        removeContainerHolograms(dungeon);
    }

    private void removeContainerHologram(BlockPosKey pos) {
        World world = pos.world();
        if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return;
        Location near = new Location(world, pos.x() + 0.5, pos.y() + 1.5, pos.z() + 0.5);
        for (Entity entity : world.getNearbyEntities(near, 2.0, 2.0, 2.0)) {
            if (entity instanceof org.bukkit.entity.TextDisplay display
                    && pos.serialize().equals(display.getPersistentDataContainer().get(containerHintPdc, PersistentDataType.STRING))) {
                display.remove();
            }
        }
    }

    private void removeContainerHolograms(DungeonInstance dungeon) {
        cleanupDungeonDisplays(dungeon);
        for (BlockPosKey pos : dungeon.getChestPositions()) removeContainerHologram(pos);
        for (BlockPosKey pos : dungeon.getBarrelPositions()) removeContainerHologram(pos);
    }

    private boolean isLootContainerPosition(DungeonInstance dungeon, BlockPosKey pos) {
        return dungeon.getChestPositions().contains(pos) || dungeon.getBarrelPositions().contains(pos);
    }

    public void removeHologram(DungeonInstance dungeon) {
        cleanupDungeonDisplays(dungeon);
    }

    public void removeLegacyDungeonDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof TextDisplay display
                            && (display.getPersistentDataContainer().has(hologramPdc, PersistentDataType.STRING)
                            || display.getPersistentDataContainer().has(containerHintPdc, PersistentDataType.STRING))) {
                        display.remove();
                    }
                }
                plugin.getGhostController().handleChunkLoad(chunk);
            }
        }
        for (DungeonInstance dungeon : dungeons.values()) dungeon.setHologramUuid(null);
    }

    private void cleanupDungeonDisplays(DungeonInstance dungeon) {
        World world = dungeon.getOrigin().world();
        if (world == null) return;
        CuboidRegion bounds = dungeon.getBounds();
        int minChunkX = bounds.minX() >> 4, maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4, maxChunkZ = bounds.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof org.bukkit.entity.TextDisplay display
                            && (display.getPersistentDataContainer().has(hologramPdc, PersistentDataType.STRING)
                            || display.getPersistentDataContainer().has(containerHintPdc, PersistentDataType.STRING))) {
                        display.remove();
                    }
                }
            }
        }
        dungeon.setHologramUuid(null);
    }

    public void handleChunkLoad(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay display
                    && (display.getPersistentDataContainer().has(hologramPdc, PersistentDataType.STRING)
                    || display.getPersistentDataContainer().has(containerHintPdc, PersistentDataType.STRING))) {
                display.remove();
            }
        }
        plugin.getGhostController().handleChunkLoad(chunk);
        if (hiddenFromWorld) return;
        for (DungeonInstance dungeon : dungeons.values()) {
            applyPendingSurfaceShulkersInChunk(dungeon, chunk);
        }
    }

    private void applyPendingSurfaceShulkersInChunk(DungeonInstance dungeon, Chunk chunk) {
        if (dungeon.getCategory() != DungeonCategory.SURFACE) return;
        int stacks = Math.max(1, plugin.getConfig().getInt("surface-dungeon.shulker-stacks", 14));
        boolean changed = false;
        for (BlockPosKey pos : dungeon.getShulkerPositions()) {
            if (!pos.worldName().equals(chunk.getWorld().getName())
                    || (pos.x() >> 4) != chunk.getX() || (pos.z() >> 4) != chunk.getZ()
                    || !dungeon.getPendingSurfaceShulkerRefresh().contains(pos)) {
                continue;
            }
            SurfaceShulkerRarity rarity = SurfaceShulkerRarity.fromName(
                    dungeon.getSurfaceShulkerRarities().get(pos));
            changed |= applySurfaceShulkerState(dungeon, chunk.getWorld(), pos, rarity, stacks);
        }
        if (changed) saveDungeons();
    }

    public DungeonInstance getByProtectedContainer(BlockPosKey pos) {
        String id = protectedContainerIndex.get(pos);
        return id == null ? null : dungeons.get(id);
    }

    public boolean markContainerOpened(BlockPosKey pos) {
        DungeonInstance dungeon = getByProtectedContainer(pos);
        if (dungeon == null || !isLootContainerPosition(dungeon, pos)) return false;
        dungeon.markContainerOpened(pos);
        removeContainerHologram(pos);
        return true;
    }

    public DungeonInstance findContainingStructureOrBuffer(Location loc) {
        for (DungeonInstance d : dungeons.values()) {
            if (d.getBounds().expand(3, 3).contains(loc)) return d;
        }
        return null;
    }

    public DungeonInstance findContainingClaimZone(Location loc) {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
        for (DungeonInstance d : dungeons.values()) {
            if (d.claimGuardRegion(pad).contains(loc)) return d;
        }
        return null;
    }

    public DungeonInstance findIntersectingClaimZone(CuboidRegion selection) {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 40);
        for (DungeonInstance d : dungeons.values()) {
            if (d.claimGuardRegion(pad).intersects3D(selection)) return d;
        }
        return null;
    }

    public Collection<DungeonInstance> getAllDungeons() { return Collections.unmodifiableCollection(dungeons.values()); }
    public DungeonInstance getDungeon(String id) { return dungeons.get(id); }
    public Map<String, DungeonTemplate> getTemplates() { return Collections.unmodifiableMap(templatesById); }
    public List<DungeonTemplate> getTemplates(DungeonCategory category) {
        List<DungeonTemplate> templates = templatesByCategory.get(category);
        return templates == null ? List.of() : Collections.unmodifiableList(templates);
    }
    public DungeonTemplate getTemplate(String id) {
        return id == null ? null : templatesById.get(id.toLowerCase(Locale.ROOT));
    }
    public DungeonTemplate getTemplate(DungeonCategory category, String id) {
        if (category == null || id == null) return null;
        for (DungeonTemplate template : getTemplates(category)) {
            if (template.getId().equalsIgnoreCase(id.trim())) return template;
        }
        return null;
    }

    public long resetIntervalMs() {
        return Math.max(60L, plugin.getConfig().getLong("regeneration.interval-seconds", 43200L)) * 1000L;
    }
}
