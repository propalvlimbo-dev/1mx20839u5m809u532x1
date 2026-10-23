package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
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
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import ru.elytrix.dungeons.structure.SpongeSchematicCodec;
import ru.elytrix.dungeons.structure.StarterSchemFactory;
import ru.elytrix.dungeons.structure.TerrainSnapshotStore;
import ru.elytrix.dungeons.structure.TerrainSnapshotStore.BlockRestoreDiff;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonManager {

    private record BlockWrite(int x, int y, int z, BlockData data) {}

    private final ElytrixDungeonsPlugin plugin;
    private final File dungeonsFile;
    private final File schematicsRoot;
    private final TerrainSnapshotStore snapshotStore;
    private final SpotFinder spotFinder;
    private final NamespacedKey hologramPdc;

    private final Map<String, DungeonTemplate> templatesById = new LinkedHashMap<>();
    private final Map<DungeonCategory, List<DungeonTemplate>> templatesByCategory = new EnumMap<>(DungeonCategory.class);
    private final Map<String, DungeonInstance> dungeons = new LinkedHashMap<>();
    private final Map<BlockPosKey, String> protectedContainerIndex = new HashMap<>();

    private boolean hiddenFromWorld = false;
    private int pendingScanTotal = 0;
    private int pendingScanDone = 0;

    private BukkitTask heartbeatTask;
    private BukkitTask workerTask;
    private final Deque<Runnable> jobQueue = new ArrayDeque<>();

    public DungeonManager(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.dungeonsFile = new File(plugin.getDataFolder(), "dungeons.yml");
        this.schematicsRoot = new File(plugin.getDataFolder(), "schematics");
        this.snapshotStore = new TerrainSnapshotStore(plugin.getDataFolder());
        this.spotFinder = new SpotFinder(plugin);
        this.hologramPdc = new NamespacedKey(plugin, "dungeon_hologram");
    }

    public File categorySchemDir(DungeonCategory cat) {
        File dir = new File(schematicsRoot, cat.name().toLowerCase(Locale.ROOT));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void startup() {
        loadSchematics();
        boolean hadSaved = loadDungeonsFromDisk();
        startHeartbeat();

        if (hadSaved) {
            // Re-place previously found dungeons onto their saved spots WITHOUT searching for new locations!
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("Restoring " + dungeons.size() + " saved dungeons onto locked coordinates...");
                for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
                    enqueueJob(() -> restoreSavedDungeonOnStartup(d));
                }
            }, 20L);
        } else if (plugin.getConfig().getBoolean("auto-spawn.enabled", true)) {
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
        plugin.getTrialController().shutdownAll();
        plugin.getGhostController().shutdownAll(dungeons.values());
        saveDungeons();

        if (plugin.getConfig().getBoolean("auto-spawn.hide-dungeons-on-disable", true) && !hiddenFromWorld) {
            plugin.getLogger().info("Hiding " + dungeons.size() + " dungeons & restoring natural terrain (locations stay saved in dungeons.yml)...");
            for (DungeonInstance d : dungeons.values()) {
                removeHologram(d);
                try {
                    snapshotStore.restoreWorldSnapshotInstant(d.getId());
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not restore snapshot on disable for " + d.getId() + ": " + ex.getMessage());
                }
            }
            hiddenFromWorld = true;
        }
    }

    public void hideAllDungeonsNow() {
        plugin.getTrialController().shutdownAll();
        plugin.getGhostController().shutdownAll(dungeons.values());
        for (DungeonInstance d : dungeons.values()) {
            removeHologram(d);
            try {
                snapshotStore.restoreWorldSnapshotInstant(d.getId());
            } catch (IOException ignored) {
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

    public void loadSchematics() {
        templatesById.clear();
        for (DungeonCategory cat : DungeonCategory.values()) {
            templatesByCategory.put(cat, new ArrayList<>());
            File dir = categorySchemDir(cat);
            if (plugin.getConfig().getBoolean("auto-spawn.write-starter-schems-if-empty", true) && !hasAnySchemFile(dir)) {
                try {
                    switch (cat) {
                        case SURFACE -> StarterSchemFactory.writeStarterSurfacePair(dir);
                        case UNDERGROUND -> StarterSchemFactory.writeStarterUndergroundPair(dir);
                    }
                } catch (IOException ex) {
                    plugin.getLogger().warning("Failed writing starter .schem in " + dir + ": " + ex.getMessage());
                }
            }
            File[] files = dir.listFiles();
            if (files == null) continue;
            Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".schem") && !n.endsWith(".schematic")) continue;
                try {
                    DungeonTemplate tpl = SpongeSchematicCodec.readSchem(f, cat);
                    templatesById.put(tpl.getId(), tpl);
                    templatesByCategory.get(cat).add(tpl);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not load .schem " + f.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private static boolean hasAnySchemFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".schem") || n.endsWith(".schematic")) return true;
        }
        return false;
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
            } catch (IOException ignored) {
            }
        }
        DungeonStatus savedStatus = inst.getStatus();
        Map<BlockPosKey, Boolean> savedShulkers = new LinkedHashMap<>(inst.getShulkerUnlocked());
        pasteWithRestoreDiffsBatched(w, inst, tpl, () -> {
            inst.setStatus(savedStatus);
            inst.getShulkerUnlocked().putAll(savedShulkers);
            updateDungeonHologram(inst);
        });
    }

    private DungeonTemplate resolveTemplateOrCategoryFallback(String tplId, DungeonCategory cat) {
        DungeonTemplate direct = templatesById.get(tplId);
        if (direct != null) return direct;
        List<DungeonTemplate> list = templatesByCategory.get(cat);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    public void saveDungeons() {
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
            writeRegion(yml, p + ".bounds", d.getBounds());
            writeRegion(yml, p + ".snapshot-bounds", d.getSnapshotBounds());

            List<String> unlocked = new ArrayList<>();
            for (Map.Entry<BlockPosKey, Boolean> e : d.getShulkerUnlocked().entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) unlocked.add(e.getKey().serialize());
            }
            yml.set(p + ".unlocked-shulkers", unlocked);
        }
        try {
            yml.save(dungeonsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save dungeons.yml: " + ex.getMessage());
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
        hiddenFromWorld = false;
        jobQueue.clear();
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

        int needSurface = Math.max(0, plugin.getConfig().getInt("auto-spawn.surface-count", 7) - countByCategory(DungeonCategory.SURFACE));
        int needUg = Math.max(0, plugin.getConfig().getInt("auto-spawn.underground-count", 7) - countByCategory(DungeonCategory.UNDERGROUND));
        pendingScanDone = 0;
        pendingScanTotal = needSurface + needUg;

        scheduleCategory(world, DungeonCategory.SURFACE, plugin.getConfig().getInt("auto-spawn.surface-count", 7));
        scheduleCategory(world, DungeonCategory.UNDERGROUND, plugin.getConfig().getInt("auto-spawn.underground-count", 7));
    }

    /**
     * Round-robin across sorted .schem files in the category folder:
     * With 2 files (e.g. surface_1.schem, surface_2.schem) and targetCount = 7:
     *   slots 0, 2, 4, 6 -> surface_1.schem (4 dungeons!)
     *   slots 1, 3, 5    -> surface_2.schem (3 dungeons!)
     */
    private void scheduleCategory(World world, DungeonCategory cat, int targetCount) {
        List<DungeonTemplate> available = templatesByCategory.get(cat);
        if (available == null || available.isEmpty()) {
            plugin.getLogger().warning("No .schem files in schematics/" + cat.name().toLowerCase(Locale.ROOT) + "/");
            return;
        }
        int have = countByCategory(cat);
        int need = Math.max(0, targetCount - have);
        for (int i = 0; i < need; i++) {
            int slotIndex = have + i;
            DungeonTemplate chosen = available.get(slotIndex % available.size());
            String id = cat.name().toLowerCase(Locale.ROOT) + "_" + nextFreeNumber(cat, i);
            enqueueJob(() -> scanAndPlaceSingle(world, id, chosen));
        }
    }

    private int nextFreeNumber(DungeonCategory cat, int offset) {
        int found = 0, candidate = 1;
        while (true) {
            if (!dungeons.containsKey(cat.name().toLowerCase(Locale.ROOT) + "_" + candidate)) {
                if (found == offset) return candidate;
                found++;
            }
            candidate++;
        }
    }

    public int countByCategory(DungeonCategory cat) {
        int c = 0;
        for (DungeonInstance d : dungeons.values()) {
            if (d.getCategory() == cat) c++;
        }
        return c;
    }

    private void scanAndPlaceSingle(World world, String id, DungeonTemplate tpl) {
        pendingScanDone++;
        if (dungeons.containsKey(id)) return;
        SpotFinder.PlacementPlan plan = spotFinder.findPlacement(world, tpl, dungeons.values(), id);
        if (plan == null) {
            plugin.getLogger().warning("[" + pendingScanDone + "/" + pendingScanTotal + "] Could not find ideal spot for " + id);
            return;
        }
        DungeonInstance created = placeDungeonWithSnapshot(
                id, tpl, plan.origin(), plan.structureBounds(), plan.snapshotBounds(), plan.surfaceY(), true);
        if (created != null) {
            String logMsg = "[" + pendingScanDone + "/" + Math.max(pendingScanDone, pendingScanTotal) + "] Найден идеальный участок для "
                    + id + " (" + tpl.getId() + ") -> X: " + plan.origin().x()
                    + ", Y: " + plan.origin().y() + ", Z: " + plan.origin().z();
            plugin.getLogger().info(logMsg);
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.hasPermission("elytrixdungeons.admin")) {
                    op.sendMessage(plugin.prefix() + "§7" + logMsg);
                }
            }
        }
    }

    public DungeonInstance manualSpawnHere(Player admin, DungeonTemplate tpl) {
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

        String id = "manual_" + (dungeons.size() + 1) + "_" + (System.currentTimeMillis() % 1000);
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
                plugin.getLogger().warning("Snapshot error for " + id + ": " + ex.getMessage());
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

        pasteWithRestoreDiffsBatched(w, inst, tpl, null);
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
        for (BlockPosKey p : inst.getShulkerUnlocked().keySet()) protectedContainerIndex.put(p, inst.getId());
    }

    public void regenerateDungeon(DungeonInstance inst, boolean forceRelocate) {
        DungeonTemplate tpl = resolveTemplateOrCategoryFallback(inst.getTemplateId(), inst.getCategory());
        World w = inst.getOrigin().world();
        if (tpl == null || w == null || inst.getStatus() == DungeonStatus.REGENERATING) {
            return;
        }
        plugin.getTrialController().abortFight(inst.getId(), false);
        plugin.getGhostController().clearHouseGhosts(inst.getId());
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
        pasteWithRestoreDiffsBatched(w, inst, tpl, null);
        saveDungeons();
    }

    public boolean removeDungeon(String id, boolean restoreTerrain) {
        DungeonInstance inst = dungeons.remove(id);
        if (inst == null) return false;
        plugin.getTrialController().abortFight(id, false);
        plugin.getGhostController().clearHouseGhosts(id);
        protectedContainerIndex.entrySet().removeIf(e -> e.getValue().equals(id));
        removeHologram(inst);
        World w = inst.getOrigin().world();
        plugin.getWorldGuardHook().removeDungeonRegion(w, id);
        if (restoreTerrain) {
            try {
                snapshotStore.restoreWorldSnapshotInstant(id);
            } catch (IOException ignored) {
            }
        }
        snapshotStore.deleteSnapshot(id);
        saveDungeons();
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
            Runnable postHook
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
                    finalizeDungeonAfterPaste(world, inst);
                    if (postHook != null) postHook.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finalizeDungeonAfterPaste(World world, DungeonInstance inst) {
        inst.setStatus(DungeonStatus.READY);

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
                if (inst.getSnapshotBounds().contains(p.getLocation())) {
                    p.setFallDistance(0f);
                    p.teleport(safe);
                    p.playSound(safe, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.35f);
                    p.sendMessage(plugin.msg("dungeon-regenerated"));
                }
            }
        }
        updateDungeonHologram(inst);
    }

    private void startHeartbeat() {
        this.heartbeatTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (hiddenFromWorld) return;
                long now = System.currentTimeMillis();
                plugin.getTrialController().tickOncePerSecond();
                plugin.getGhostController().tickHousesOncePerSecond(dungeons.values());

                for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
                    if (d.getStatus() != DungeonStatus.REGENERATING && now >= d.getNextResetEpochMs()) {
                        enqueueJob(() -> regenerateDungeon(d, false));
                        continue;
                    }
                    World w = d.getOrigin().world();
                    BlockPosKey anchor = d.getHologramAnchor();
                    if (w != null && anchor != null && w.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
                        updateDungeonHologram(d);
                    }
                }
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

    public void updateDungeonHologram(DungeonInstance d) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true) || hiddenFromWorld) {
            removeHologram(d);
            return;
        }
        BlockPosKey anchor = d.getHologramAnchor();
        if (anchor == null) return;
        World w = anchor.world();
        if (w == null || !w.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) return;

        double yOff = d.getCategory() == DungeonCategory.UNDERGROUND ? 2.35 : 2.15;
        Location holoLoc = new Location(w, anchor.x() + 0.5, anchor.y() + yOff, anchor.z() + 0.5);
        TextDisplay td = findHologram(w, d, holoLoc);
        if (td == null) {
            Entity spawned = w.spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
            if (!(spawned instanceof TextDisplay created)) {
                spawned.remove();
                return;
            }
            td = created;
            d.setHologramUuid(td.getUniqueId());
        } else if (td.getLocation().distanceSquared(holoLoc) > 0.04) {
            td.teleport(holoLoc);
        }

        td.setPersistent(false);
        td.getPersistentDataContainer().set(hologramPdc, PersistentDataType.STRING, d.getId());
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setDefaultBackground(false);
        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        td.setShadowed(true);
        td.setSeeThrough(false);
        td.setLineWidth(plugin.getConfig().getInt("hologram.line-width", 270));
        td.setViewRange((float) plugin.getConfig().getDouble("hologram.view-range", 0.75));
        td.setText(buildHologramText(d));
    }

    private String buildHologramText(DungeonInstance d) {
        String timer = TextUtil.formatDuration(d.secondsUntilReset());
        List<String> lines;
        int ghosts = 0, mobs = 0;
        String clock = "00:00";

        if (d.getCategory() == DungeonCategory.SURFACE) {
            lines = plugin.getConfig().getStringList("hologram.surface-lines");
            ghosts = plugin.getGhostController().countAliveGhosts(d.getId());
        } else {
            TrialController.ActiveFight f = plugin.getTrialController().getFight(d.getId());
            if (f != null) {
                lines = plugin.getConfig().getStringList("hologram.underground-fight-lines");
                mobs = f.aliveMobs.size();
                clock = TextUtil.formatShortClock((f.deadlineMs - System.currentTimeMillis()) / 1000L);
            } else if (d.getStatus() == DungeonStatus.CLEARED) {
                lines = plugin.getConfig().getStringList("hologram.underground-cooldown-lines");
            } else {
                lines = plugin.getConfig().getStringList("hologram.underground-ready-lines");
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(TextUtil.color(lines.get(i)
                    .replace("{ghosts}", String.valueOf(ghosts))
                    .replace("{mobs}", String.valueOf(mobs))
                    .replace("{clock}", clock)
                    .replace("{timer}", timer)));
        }
        return sb.toString();
    }

    private TextDisplay findHologram(World w, DungeonInstance d, Location near) {
        if (d.getHologramUuid() != null) {
            Entity e = w.getEntity(d.getHologramUuid());
            if (e instanceof TextDisplay td && td.isValid()) return td;
        }
        for (Entity e : w.getNearbyEntities(near, 3.0, 4.0, 3.0)) {
            if (e instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(hologramPdc, PersistentDataType.STRING);
                if (d.getId().equals(tag)) {
                    d.setHologramUuid(td.getUniqueId());
                    return td;
                }
            }
        }
        return null;
    }

    public void removeHologram(DungeonInstance d) {
        World w = d.getOrigin().world();
        if (w != null && d.getHologramUuid() != null) {
            Entity e = w.getEntity(d.getHologramUuid());
            if (e != null) e.remove();
            d.setHologramUuid(null);
        }
    }

    public void handleChunkLoad(Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (e instanceof TextDisplay td) {
                String id = td.getPersistentDataContainer().get(hologramPdc, PersistentDataType.STRING);
                if (id != null && (!dungeons.containsKey(id) || hiddenFromWorld)) {
                    td.remove();
                }
            }
        }
        plugin.getGhostController().handleChunkLoad(chunk);
        if (hiddenFromWorld) return;
        for (DungeonInstance d : dungeons.values()) {
            BlockPosKey anchor = d.getHologramAnchor();
            if (anchor != null && anchor.worldName().equals(chunk.getWorld().getName())
                    && (anchor.x() >> 4) == chunk.getX() && (anchor.z() >> 4) == chunk.getZ()) {
                updateDungeonHologram(d);
            }
        }
    }

    public DungeonInstance getByProtectedContainer(BlockPosKey pos) {
        String id = protectedContainerIndex.get(pos);
        return id == null ? null : dungeons.get(id);
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
    public DungeonTemplate getTemplate(String id) { return templatesById.get(id.toLowerCase(Locale.ROOT)); }

    public long resetIntervalMs() {
        return Math.max(60L, plugin.getConfig().getLong("regeneration.interval-seconds", 43200L)) * 1000L;
    }
}
