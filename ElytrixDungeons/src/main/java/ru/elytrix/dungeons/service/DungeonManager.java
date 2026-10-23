package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
    private final Map<BlockPosKey, String> interactiveBlockIndex = new HashMap<>();

    private BukkitTask heartbeatTask;
    private BukkitTask pasteWorkerTask;
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
        loadDungeons();
        startHeartbeat();

        if (plugin.getConfig().getBoolean("auto-spawn.enabled", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, this::ensureTargetDungeonCounts, 20L);
        }
    }

    public void shutdown() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        if (pasteWorkerTask != null) {
            pasteWorkerTask.cancel();
            pasteWorkerTask = null;
        }
        plugin.getTrialController().shutdownAll();
        saveDungeons();
    }

    public Set<String> configuredShardMaterials() {
        Set<String> set = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("sky-dungeon.shard-block-types")) {
            set.add(s.toUpperCase(Locale.ROOT));
        }
        if (set.isEmpty()) {
            set.add("AMETHYST_CLUSTER");
            set.add("LARGE_AMETHYST_BUD");
            set.add("BUDDING_AMETHYST");
        }
        return set;
    }

    public void loadSchematics() {
        templatesById.clear();
        for (DungeonCategory cat : DungeonCategory.values()) {
            templatesByCategory.put(cat, new ArrayList<>());
            File dir = categorySchemDir(cat);
            if (plugin.getConfig().getBoolean("auto-spawn.write-starter-schems-if-empty", true) && !hasAnySchemFile(dir)) {
                try {
                    switch (cat) {
                        case SURFACE -> StarterSchemFactory.writeStarterSurfaceSchem(new File(dir, "surface_starter.schem"));
                        case UNDERGROUND -> StarterSchemFactory.writeStarterUndergroundSchem(new File(dir, "underground_starter.schem"));
                        case SKY -> StarterSchemFactory.writeStarterSkySchem(new File(dir, "sky_starter.schem"));
                    }
                } catch (IOException ex) {
                    plugin.getLogger().warning("Failed writing starter .schem in " + dir + ": " + ex.getMessage());
                }
            }
            File[] files = dir.listFiles();
            if (files == null) continue;
            int uniqueCount = plugin.getConfig().getInt("surface-dungeon.unique-shulker-count", 3);
            int minShards = plugin.getConfig().getInt("sky-dungeon.min-shards-if-missing-in-schem", 8);
            Set<String> shardMats = configuredShardMaterials();

            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".schem") && !n.endsWith(".schematic")) continue;
                try {
                    DungeonTemplate tpl = SpongeSchematicCodec.readSchem(f, cat, uniqueCount, minShards, shardMats);
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

    public void loadDungeons() {
        dungeons.clear();
        interactiveBlockIndex.clear();
        if (!dungeonsFile.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dungeonsFile);
        ConfigurationSection sec = yml.getConfigurationSection("dungeons");
        if (sec == null) return;

        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);
        int maxSurfaceShulkers = plugin.getConfig().getInt("surface-dungeon.unique-shulker-count", 3);

        for (String id : sec.getKeys(false)) {
            ConfigurationSection d = sec.getConfigurationSection(id);
            if (d == null) continue;
            BlockPosKey origin = BlockPosKey.deserialize(d.getString("origin"));
            DungeonCategory cat = DungeonCategory.valueOf(d.getString("category", "SURFACE"));
            String tplId = d.getString("template", "");
            DungeonTemplate tpl = resolveTemplateOrCategoryFallback(tplId, cat);
            if (origin == null || tpl == null) continue;

            CuboidRegion bounds = new CuboidRegion(
                    origin.worldName(),
                    d.getInt("bounds.min-x"), d.getInt("bounds.min-y"), d.getInt("bounds.min-z"),
                    d.getInt("bounds.max-x"), d.getInt("bounds.max-y"), d.getInt("bounds.max-z")
            );
            DungeonInstance inst = new DungeonInstance(
                    id,
                    d.getString("display-name", cat.getTitle() + " данж"),
                    tpl.getId(),
                    cat,
                    origin,
                    bounds,
                    d.getInt("surface-y", origin.y()),
                    d.getLong("next-reset-ms", System.currentTimeMillis() + resetIntervalMs())
            );
            DungeonStatus st;
            try {
                st = DungeonStatus.valueOf(d.getString("status", "READY"));
            } catch (IllegalArgumentException ex) {
                st = DungeonStatus.READY;
            }
            inst.setStatus(st == DungeonStatus.CLEARED ? DungeonStatus.CLEARED : DungeonStatus.READY);

            String holoStr = d.getString("hologram-uuid");
            if (holoStr != null && !holoStr.isEmpty()) {
                try {
                    inst.setHologramUuid(UUID.fromString(holoStr));
                } catch (IllegalArgumentException ignored) {
                }
            }

            inst.applyTemplateMarkers(tpl, maxSurfaceShulkers);

            for (String unlockedKey : d.getStringList("unlocked-shulkers")) {
                BlockPosKey pk = BlockPosKey.deserialize(unlockedKey);
                if (pk != null && inst.getShulkerUnlocked().containsKey(pk)) {
                    inst.getShulkerUnlocked().put(pk, Boolean.TRUE);
                }
            }
            ConfigurationSection shardSec = d.getConfigurationSection("shards");
            if (shardSec != null) {
                int initialCount = d.getInt("initial-shards", inst.getShardExplosions().size());
                inst.getShardExplosions().clear();
                for (String key : shardSec.getKeys(false)) {
                    BlockPosKey pk = BlockPosKey.deserialize(key.replace('|', ';'));
                    if (pk != null) {
                        inst.getShardExplosions().put(pk, shardSec.getInt(key, 0));
                    }
                }
                inst.setInitialShardCount(initialCount);
            }

            registerDungeonRuntime(inst, pad, allowPvp);
        }
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
            yml.set(p + ".hologram-uuid", d.getHologramUuid() == null ? null : d.getHologramUuid().toString());
            CuboidRegion b = d.getBounds();
            yml.set(p + ".bounds.min-x", b.minX());
            yml.set(p + ".bounds.min-y", b.minY());
            yml.set(p + ".bounds.min-z", b.minZ());
            yml.set(p + ".bounds.max-x", b.maxX());
            yml.set(p + ".bounds.max-y", b.maxY());
            yml.set(p + ".bounds.max-z", b.maxZ());

            List<String> unlocked = new ArrayList<>();
            for (Map.Entry<BlockPosKey, Boolean> e : d.getShulkerUnlocked().entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) unlocked.add(e.getKey().serialize());
            }
            yml.set(p + ".unlocked-shulkers", unlocked);
            yml.set(p + ".initial-shards", d.getInitialShardCount());
            for (Map.Entry<BlockPosKey, Integer> e : d.getShardExplosions().entrySet()) {
                yml.set(p + ".shards." + e.getKey().serialize().replace(';', '|'), e.getValue());
            }
        }
        try {
            yml.save(dungeonsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save dungeons.yml: " + ex.getMessage());
        }
    }

    public void rebuildAllFromSchematics() {
        for (String id : new ArrayList<>(dungeons.keySet())) {
            removeDungeon(id, true);
        }
        loadSchematics();
        ensureTargetDungeonCounts();
    }

    public void ensureTargetDungeonCounts() {
        String worldName = plugin.getConfig().getString("world-settings.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) return;

        scheduleCategory(world, DungeonCategory.SURFACE, plugin.getConfig().getInt("auto-spawn.surface-count", 5));
        scheduleCategory(world, DungeonCategory.UNDERGROUND, plugin.getConfig().getInt("auto-spawn.underground-count", 5));
        scheduleCategory(world, DungeonCategory.SKY, plugin.getConfig().getInt("auto-spawn.sky-count", 5));
    }

    private void scheduleCategory(World world, DungeonCategory cat, int targetCount) {
        List<DungeonTemplate> available = templatesByCategory.get(cat);
        if (available == null || available.isEmpty()) {
            plugin.getLogger().warning("No .schem files found in schematics/" + cat.name().toLowerCase(Locale.ROOT) + "/");
            return;
        }
        int have = countByCategory(cat);
        int need = Math.max(0, targetCount - have);
        for (int i = 0; i < need; i++) {
            DungeonTemplate chosen = available.get((have + i) % available.size());
            String id = cat.name().toLowerCase(Locale.ROOT) + "_" + (nextFreeNumber(cat, i));
            World targetWorld = world;
            enqueueBuildJob(() -> autoCreateSingle(targetWorld, id, chosen));
        }
    }

    private int nextFreeNumber(DungeonCategory cat, int offset) {
        int found = 0;
        int candidate = 1;
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

    private void autoCreateSingle(World world, String id, DungeonTemplate tpl) {
        if (dungeons.containsKey(id)) return;
        SpotFinder.PlacementPlan plan = spotFinder.findPlacement(world, tpl, dungeons.values(), id);
        if (plan == null) {
            plugin.getLogger().warning("Could not find free spot in 3000x3000 world for " + id);
            return;
        }
        placeDungeonWithSnapshot(id, tpl, plan.origin(), plan.fullBounds(), plan.surfaceY(), true);
    }

    public DungeonInstance manualSpawnHere(Player admin, DungeonTemplate tpl) {
        World w = admin.getWorld();
        Location loc = admin.getLocation();
        int ox = loc.getBlockX() - tpl.getSizeX() / 2;
        int oy = loc.getBlockY() - 1;
        int oz = loc.getBlockZ() - tpl.getSizeZ() / 2;
        int surfY = w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int maxY = tpl.getCategory() == DungeonCategory.UNDERGROUND
                ? Math.max(oy + tpl.getSizeY() - 1, surfY + 7)
                : oy + tpl.getSizeY() + 1;
        CuboidRegion bounds = new CuboidRegion(w.getName(), ox, oy - 3, oz, ox + tpl.getSizeX() - 1, maxY, oz + tpl.getSizeZ() - 1);
        String id = "manual_" + (dungeons.size() + 1) + "_" + (System.currentTimeMillis() % 1000);
        return placeDungeonWithSnapshot(id, tpl, new BlockPosKey(w.getName(), ox, oy, oz), bounds, surfY, true);
    }

    private DungeonInstance placeDungeonWithSnapshot(
            String id,
            DungeonTemplate tpl,
            BlockPosKey origin,
            CuboidRegion bounds,
            int surfaceY,
            boolean saveTerrainFirst
    ) {
        World w = origin.world();
        if (w == null) return null;
        if (saveTerrainFirst) {
            try {
                snapshotStore.saveWorldSnapshot(id, w, bounds);
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
                bounds,
                surfaceY,
                System.currentTimeMillis() + resetIntervalMs()
        );
        inst.applyTemplateMarkers(tpl, plugin.getConfig().getInt("surface-dungeon.unique-shulker-count", 3));

        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);
        registerDungeonRuntime(inst, pad, allowPvp);

        pasteDungeonBatched(w, inst, tpl);
        saveDungeons();
        plugin.getLogger().info("Spawned " + inst.getCategory() + " dungeon '" + id + "' at "
                + origin.x() + ", " + origin.y() + ", " + origin.z());
        return inst;
    }

    private void registerDungeonRuntime(DungeonInstance inst, int pad, boolean allowPvp) {
        dungeons.put(inst.getId(), inst);
        reindexBlocks(inst);
        if (plugin.getConfig().getBoolean("protection.prevent-claims", true)) {
            plugin.getWorldGuardHook().syncDungeonRegion(inst, pad, allowPvp);
        }
    }

    public void reindexBlocks(DungeonInstance inst) {
        interactiveBlockIndex.entrySet().removeIf(e -> e.getValue().equals(inst.getId()));
        for (BlockPosKey p : inst.getShulkerUnlocked().keySet()) {
            interactiveBlockIndex.put(p, inst.getId());
        }
        for (BlockPosKey p : inst.getShardExplosions().keySet()) {
            interactiveBlockIndex.put(p, inst.getId());
        }
    }

    public void regenerateDungeon(DungeonInstance inst, boolean forceRelocate) {
        DungeonTemplate tpl = resolveTemplateOrCategoryFallback(inst.getTemplateId(), inst.getCategory());
        World w = inst.getOrigin().world();
        if (tpl == null || w == null || inst.getStatus() == DungeonStatus.REGENERATING) {
            return;
        }
        plugin.getTrialController().abortFight(inst.getId(), false);
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
                    snapshotStore.saveWorldSnapshot(inst.getId(), w, newPlan.fullBounds());
                } catch (IOException ignored) {
                }
                inst.setOrigin(newPlan.origin());
                inst.setBounds(newPlan.fullBounds());
                inst.setSurfaceY(newPlan.surfaceY());
            }
            int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
            if (plugin.getConfig().getBoolean("protection.prevent-claims", true)) {
                plugin.getWorldGuardHook().syncDungeonRegion(inst, pad, plugin.getConfig().getBoolean("protection.allow-pvp", true));
            }
        } else if (snapshotStore.hasSnapshot(inst.getId())) {
            try {
                snapshotStore.restoreWorldSnapshotInstant(inst.getId());
            } catch (IOException ignored) {
            }
        }

        inst.applyTemplateMarkers(tpl, plugin.getConfig().getInt("surface-dungeon.unique-shulker-count", 3));
        reindexBlocks(inst);
        inst.setNextResetEpochMs(System.currentTimeMillis() + resetIntervalMs());
        pasteDungeonBatched(w, inst, tpl);
        saveDungeons();
    }

    public boolean removeDungeon(String id, boolean restoreTerrain) {
        DungeonInstance inst = dungeons.remove(id);
        if (inst == null) return false;
        plugin.getTrialController().abortFight(id, false);
        interactiveBlockIndex.entrySet().removeIf(e -> e.getValue().equals(id));
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

    private void pasteDungeonBatched(World world, DungeonInstance inst, DungeonTemplate tpl) {
        List<BlockWrite> writes = new ArrayList<>(tpl.getSizeX() * tpl.getSizeY() * tpl.getSizeZ());
        Map<String, BlockData> bdCache = new HashMap<>();
        int ox = inst.getOrigin().x(), oy = inst.getOrigin().y(), oz = inst.getOrigin().z();

        if (inst.getCategory() == DungeonCategory.SURFACE) {
            BlockData cobble = Bukkit.createBlockData(Material.COBBLESTONE);
            BlockData bricks = Bukkit.createBlockData(Material.STONE_BRICKS);
            for (int x = 1; x <= tpl.getSizeX() - 2; x++) {
                for (int z = 1; z <= tpl.getSizeZ() - 2; z++) {
                    for (int dy = -3; dy <= -1; dy++) {
                        writes.add(new BlockWrite(ox + x, oy + dy, oz + z, dy == -1 ? bricks : cobble));
                    }
                }
            }
        }

        for (int y = 0; y < tpl.getSizeY(); y++) {
            for (int z = 0; z < tpl.getSizeZ(); z++) {
                for (int x = 0; x < tpl.getSizeX(); x++) {
                    String state = tpl.getBlock(x, y, z);
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

        if (inst.getCategory() == DungeonCategory.UNDERGROUND
                && plugin.getConfig().getBoolean("auto-spawn.underground-surface-shaft", true)) {
            appendUndergroundShaft(writes, inst, tpl);
        }

        int perTick = Math.max(250, plugin.getConfig().getInt("auto-spawn.blocks-per-tick", 900));
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
                    finalizeDungeonContainers(world, inst);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void appendUndergroundShaft(List<BlockWrite> writes, DungeonInstance inst, DungeonTemplate tpl) {
        int[] ent = tpl.getEntranceOffset();
        int shaftX = inst.getOrigin().x() + ent[0];
        int shaftZ = inst.getOrigin().z() + ent[2];
        int startY = inst.getOrigin().y() + tpl.getSizeY() - 2;
        int topY = inst.getSurfaceY();

        BlockData air = Bukkit.createBlockData(Material.AIR);
        BlockData wall = Bukkit.createBlockData(Material.DEEPSLATE_BRICKS);
        BlockData mossy = Bukkit.createBlockData(Material.MOSSY_STONE_BRICKS);
        BlockData ladder = Bukkit.createBlockData("minecraft:ladder[facing=south]");
        BlockData lantern = Bukkit.createBlockData("minecraft:soul_lantern[hanging=false]");
        BlockData post = Bukkit.createBlockData(Material.DEEPSLATE_BRICK_WALL);

        for (int y = startY; y <= topY; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    writes.add(new BlockWrite(shaftX + dx, y, shaftZ + dz, border ? wall : air));
                }
            }
            writes.add(new BlockWrite(shaftX, y, shaftZ - 1, ladder));
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                writes.add(new BlockWrite(shaftX + dx, topY, shaftZ + dz, mossy));
                for (int y = topY + 1; y <= topY + 4; y++) {
                    writes.add(new BlockWrite(shaftX + dx, y, shaftZ + dz, air));
                }
            }
        }
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] c : corners) {
            for (int y = topY + 1; y <= topY + 3; y++) {
                writes.add(new BlockWrite(shaftX + c[0], y, shaftZ + c[1], post));
            }
            writes.add(new BlockWrite(shaftX + c[0], topY + 4, shaftZ + c[1], lantern));
        }
    }

    private void finalizeDungeonContainers(World world, DungeonInstance inst) {
        inst.setStatus(DungeonStatus.READY);

        if (inst.getCategory() == DungeonCategory.SURFACE) {
            int chestStacks = plugin.getConfig().getInt("surface-dungeon.chest-stacks", 5);
            int barrelStacks = plugin.getConfig().getInt("surface-dungeon.barrel-stacks", 4);
            for (BlockPosKey p : inst.getChestPositions()) {
                Block b = world.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.SURFACE_CHEST, chestStacks);
                }
            }
            for (BlockPosKey p : inst.getBarrelPositions()) {
                Block b = world.getBlockAt(p.x(), p.y(), p.z());
                if (b.getState() instanceof InventoryHolder holder) {
                    plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.SURFACE_BARREL, barrelStacks);
                }
            }
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
        }

        // All locked shulkers start empty until unlocked by Unique Key (SURFACE) or defeating all guards (UNDERGROUND)
        for (BlockPosKey sp : inst.getShulkerUnlocked().keySet()) {
            Block b = world.getBlockAt(sp.x(), sp.y(), sp.z());
            if (b.getState() instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }
        }

        Location safe = inst.teleportLocation();
        if (safe != null) {
            for (Player p : world.getPlayers()) {
                if (inst.getBounds().contains(p.getLocation())) {
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
                long now = System.currentTimeMillis();
                plugin.getTrialController().tickOncePerSecond();

                for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
                    if (d.getStatus() != DungeonStatus.REGENERATING && now >= d.getNextResetEpochMs()) {
                        enqueueBuildJob(() -> regenerateDungeon(d, false));
                        continue;
                    }
                    World w = d.getOrigin().world();
                    BlockPosKey anchor = d.getHologramAnchor();
                    if (w != null && anchor != null && w.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
                        updateDungeonHologram(d);
                        if (d.getCategory() == DungeonCategory.SKY) {
                            for (BlockPosKey sh : d.getShardExplosions().keySet()) {
                                w.spawnParticle(Particle.END_ROD, sh.centerLocation().add(0, 0.35, 0), 1, 0.18, 0.22, 0.18, 0.01);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void enqueueBuildJob(Runnable job) {
        jobQueue.addLast(job);
        if (pasteWorkerTask == null || pasteWorkerTask.isCancelled()) {
            pasteWorkerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    Runnable next = jobQueue.pollFirst();
                    if (next == null) {
                        cancel();
                        pasteWorkerTask = null;
                        return;
                    }
                    next.run();
                }
            }.runTaskTimer(plugin, 5L, 35L);
        }
    }

    public void updateDungeonHologram(DungeonInstance d) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            removeHologram(d);
            return;
        }
        BlockPosKey anchor = d.getHologramAnchor();
        if (anchor == null) return;
        World w = anchor.world();
        if (w == null || !w.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
            return;
        }

        Location holoLoc = new Location(w, anchor.x() + 0.5, anchor.y() + 2.45, anchor.z() + 0.5);
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
        int left = 0, total = 0, mobs = 0;
        String clock = "00:00";

        if (d.getCategory() == DungeonCategory.SURFACE) {
            lines = plugin.getConfig().getStringList("hologram.surface-lines");
            left = d.countLockedShulkers();
            total = d.getShulkerUnlocked().size();
        } else if (d.getCategory() == DungeonCategory.SKY) {
            lines = plugin.getConfig().getStringList("hologram.sky-lines");
            left = d.getShardExplosions().size();
            total = d.getInitialShardCount();
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
                    .replace("{left}", String.valueOf(left))
                    .replace("{total}", String.valueOf(total))
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
                if (id != null && !dungeons.containsKey(id)) td.remove();
            }
        }
        for (DungeonInstance d : dungeons.values()) {
            BlockPosKey anchor = d.getHologramAnchor();
            if (anchor != null && anchor.worldName().equals(chunk.getWorld().getName())
                    && (anchor.x() >> 4) == chunk.getX() && (anchor.z() >> 4) == chunk.getZ()) {
                updateDungeonHologram(d);
            }
        }
    }

    public DungeonInstance getByInteractiveBlock(BlockPosKey pos) {
        String id = interactiveBlockIndex.get(pos);
        return id == null ? null : dungeons.get(id);
    }

    public void unregisterSingleInteractiveBlock(BlockPosKey pos) {
        interactiveBlockIndex.remove(pos);
    }

    public DungeonInstance findContainingClaimZone(Location loc) {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        for (DungeonInstance d : dungeons.values()) {
            if (d.claimGuardRegion(pad).contains(loc)) return d;
        }
        return null;
    }

    public DungeonInstance findIntersectingClaimZone(CuboidRegion selection) {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        for (DungeonInstance d : dungeons.values()) {
            if (d.claimGuardRegion(pad).intersects3D(selection)) return d;
        }
        return null;
    }

    public Collection<DungeonInstance> getAllDungeons() {
        return Collections.unmodifiableCollection(dungeons.values());
    }

    public DungeonInstance getDungeon(String id) {
        return dungeons.get(id);
    }

    public Map<String, DungeonTemplate> getTemplates() {
        return Collections.unmodifiableMap(templatesById);
    }

    public DungeonTemplate getTemplate(String id) {
        return templatesById.get(id.toLowerCase(Locale.ROOT));
    }

    public long resetIntervalMs() {
        return Math.max(60L, plugin.getConfig().getLong("regeneration.interval-seconds", 43200L)) * 1000L;
    }
}
