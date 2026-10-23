package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
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
import ru.elytrix.dungeons.model.TrialType;
import ru.elytrix.dungeons.structure.BuiltinArchitect;
import ru.elytrix.dungeons.structure.SpongeSchematicReader;
import ru.elytrix.dungeons.structure.TerrainSnapshotStore;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonManager {

    private record BlockWrite(int x, int y, int z, BlockData data) {}

    private final ElytrixDungeonsPlugin plugin;
    private final File dungeonsFile;
    private final File schematicsDir;
    private final TerrainSnapshotStore snapshotStore;
    private final SpotFinder spotFinder;
    private final NamespacedKey hologramPdc;

    private final Map<String, DungeonTemplate> templates = new LinkedHashMap<>();
    private final Map<String, DungeonInstance> dungeons = new LinkedHashMap<>();
    private final Map<BlockPosKey, String> specialBlockIndex = new HashMap<>();

    private BukkitTask heartbeatTask;
    private BukkitTask pasteWorkerTask;
    private final Deque<Runnable> jobQueue = new ArrayDeque<>();

    public DungeonManager(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.dungeonsFile = new File(plugin.getDataFolder(), "dungeons.yml");
        this.schematicsDir = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }
        this.snapshotStore = new TerrainSnapshotStore(plugin.getDataFolder());
        this.spotFinder = new SpotFinder(plugin);
        this.hologramPdc = new NamespacedKey(plugin, "dungeon_hologram");
    }

    public void startup() {
        loadTemplates();
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

    public void loadTemplates() {
        templates.clear();
        templates.putAll(BuiltinArchitect.buildAll());

        File[] files = schematicsDir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            String lower = f.getName().toLowerCase();
            try {
                if (lower.endsWith(".yml")) {
                    DungeonTemplate loaded = loadTemplateYaml(f);
                    if (loaded != null) {
                        templates.put(loaded.getId(), loaded);
                    }
                } else if (lower.endsWith(".schem") || lower.endsWith(".schematic")) {
                    DungeonTemplate loaded = SpongeSchematicReader.readSchem(f, DungeonCategory.SURFACE, 3);
                    templates.put(loaded.getId(), loaded);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not load custom schematic " + f.getName() + ": " + ex.getMessage());
            }
        }
    }

    public void saveTemplateYaml(DungeonTemplate tpl) throws IOException {
        File out = new File(schematicsDir, tpl.getId().toLowerCase() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("id", tpl.getId());
        yml.set("display-name", tpl.getDisplayName());
        yml.set("category", tpl.getCategory().name());
        yml.set("tier", tpl.getTier());
        yml.set("trial-type", tpl.getTrialType().name());
        yml.set("size-x", tpl.getSizeX());
        yml.set("size-y", tpl.getSizeY());
        yml.set("size-z", tpl.getSizeZ());
        yml.set("entrance", posList(tpl.getEntranceOffset()));
        yml.set("altar", posList(tpl.getAltarOffset()));
        yml.set("seals", toStringOffsets(tpl.getSealOffsets()));
        yml.set("explorer-chests", toStringOffsets(tpl.getExplorerChestOffsets()));
        yml.set("reward-chests", toStringOffsets(tpl.getRewardChestOffsets()));
        yml.set("mob-spawns", toStringOffsets(tpl.getMobSpawnOffsets()));
        yml.set("palette", tpl.getPalette());
        yml.set("rle", tpl.encodeRle());
        yml.save(out);
        templates.put(tpl.getId().toLowerCase(), tpl);
    }

    private DungeonTemplate loadTemplateYaml(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String id = yml.getString("id", file.getName().replace(".yml", "")).toLowerCase();
        String name = yml.getString("display-name", id);
        DungeonCategory cat = DungeonCategory.valueOf(yml.getString("category", "SURFACE"));
        int tier = yml.getInt("tier", 3);
        TrialType tt = TrialType.valueOf(yml.getString("trial-type", "WAVES"));
        int sx = yml.getInt("size-x", 15), sy = yml.getInt("size-y", 10), sz = yml.getInt("size-z", 15);
        DungeonTemplate tpl = new DungeonTemplate(id, name, cat, tier, tt, sx, sy, sz);
        tpl.loadPaletteAndRle(yml.getStringList("palette"), yml.getString("rle", ""));
        int[] ent = parseOffset(yml.getString("entrance"));
        if (ent != null) tpl.setEntranceOffset(ent[0], ent[1], ent[2]);
        int[] alt = parseOffset(yml.getString("altar"));
        if (alt != null) tpl.setAltarOffset(alt[0], alt[1], alt[2]);
        readOffsets(yml.getStringList("seals"), tpl.getSealOffsets());
        readOffsets(yml.getStringList("explorer-chests"), tpl.getExplorerChestOffsets());
        readOffsets(yml.getStringList("reward-chests"), tpl.getRewardChestOffsets());
        readOffsets(yml.getStringList("mob-spawns"), tpl.getMobSpawnOffsets());
        return tpl;
    }

    public void loadDungeons() {
        dungeons.clear();
        specialBlockIndex.clear();
        if (!dungeonsFile.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dungeonsFile);
        ConfigurationSection sec = yml.getConfigurationSection("dungeons");
        if (sec == null) {
            return;
        }
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);

        for (String id : sec.getKeys(false)) {
            ConfigurationSection d = sec.getConfigurationSection(id);
            if (d == null) continue;
            BlockPosKey origin = BlockPosKey.deserialize(d.getString("origin"));
            DungeonCategory cat = DungeonCategory.valueOf(d.getString("category", "SURFACE"));
            TrialType tt = TrialType.valueOf(d.getString("trial-type", "WAVES"));
            String tplId = d.getString("template", "surface_ruin_temple");
            DungeonTemplate tpl = templates.get(tplId);
            if (origin == null || tpl == null) continue;

            CuboidRegion bounds = new CuboidRegion(
                    origin.worldName(),
                    d.getInt("bounds.min-x"), d.getInt("bounds.min-y"), d.getInt("bounds.min-z"),
                    d.getInt("bounds.max-x"), d.getInt("bounds.max-y"), d.getInt("bounds.max-z")
            );
            DungeonInstance inst = new DungeonInstance(
                    id,
                    d.getString("display-name", tpl.getDisplayName()),
                    tplId,
                    cat,
                    d.getInt("tier", tpl.getTier()),
                    tt,
                    origin,
                    bounds,
                    d.getInt("surface-y", origin.y()),
                    d.getLong("next-reset-ms", System.currentTimeMillis() + resetIntervalMs())
            );
            DungeonStatus savedStatus;
            try {
                savedStatus = DungeonStatus.valueOf(d.getString("status", "READY"));
            } catch (IllegalArgumentException ex) {
                savedStatus = DungeonStatus.READY;
            }
            inst.setStatus(savedStatus == DungeonStatus.CLEARED ? DungeonStatus.CLEARED : DungeonStatus.READY);
            String holoStr = d.getString("hologram-uuid");
            if (holoStr != null && !holoStr.isEmpty()) {
                try {
                    inst.setHologramUuid(UUID.fromString(holoStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
            inst.applyTemplateMarkers(tpl);
            registerDungeonRuntime(inst, pad, allowPvp);
        }
    }

    public void saveDungeons() {
        YamlConfiguration yml = new YamlConfiguration();
        for (DungeonInstance d : dungeons.values()) {
            String p = "dungeons." + d.getId();
            yml.set(p + ".display-name", d.getDisplayName());
            yml.set(p + ".template", d.getTemplateId());
            yml.set(p + ".category", d.getCategory().name());
            yml.set(p + ".tier", d.getTier());
            yml.set(p + ".trial-type", d.getTrialType().name());
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
        }
        try {
            yml.save(dungeonsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save dungeons.yml: " + ex.getMessage());
        }
    }

    public void ensureTargetDungeonCounts() {
        String worldName = plugin.getConfig().getString("world-settings.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            plugin.getLogger().warning("Target world not loaded yet; cannot auto-generate dungeons.");
            return;
        }

        int targetSurface = plugin.getConfig().getInt("auto-spawn.surface-count", 5);
        int targetUnderground = plugin.getConfig().getInt("auto-spawn.underground-count", 5);
        int targetSky = plugin.getConfig().getInt("auto-spawn.sky-count", 5);

        scheduleMissingForCategory(world, DungeonCategory.SURFACE, targetSurface, List.of(
                "surface_ruin_temple", "surface_ruin_temple",
                "surface_blackstone_bastion", "surface_blackstone_bastion",
                "surface_purpur_keep"
        ));
        scheduleMissingForCategory(world, DungeonCategory.UNDERGROUND, targetUnderground, List.of(
                "underground_rift_crypt", "underground_rift_crypt", "underground_rift_crypt",
                "underground_sculk_sanctum", "underground_sculk_sanctum"
        ));
        scheduleMissingForCategory(world, DungeonCategory.SKY, targetSky, List.of(
                "sky_astral_island", "sky_astral_island", "sky_astral_island",
                "sky_elytrix_citadel", "sky_elytrix_citadel"
        ));
    }

    private void scheduleMissingForCategory(World world, DungeonCategory cat, int targetCount, List<String> rotation) {
        int have = countByCategory(cat);
        int need = Math.max(0, targetCount - have);
        for (int i = 0; i < need; i++) {
            String tplId = rotation.get((have + i) % rotation.size());
            int indexNum = nextIndexForCategory(cat) + i;
            String id = cat.name().toLowerCase() + "_" + indexNum;
            World targetWorld = world;
            enqueueBuildJob(() -> autoCreateSingleDungeon(targetWorld, id, tplId));
        }
    }

    private int nextIndexForCategory(DungeonCategory cat) {
        int n = 1;
        while (dungeons.containsKey(cat.name().toLowerCase() + "_" + n)) {
            n++;
        }
        return n;
    }

    public int countByCategory(DungeonCategory cat) {
        int c = 0;
        for (DungeonInstance d : dungeons.values()) {
            if (d.getCategory() == cat) c++;
        }
        return c;
    }

    private void autoCreateSingleDungeon(World world, String id, String tplId) {
        if (dungeons.containsKey(id)) {
            return;
        }
        DungeonTemplate tpl = templates.get(tplId);
        if (tpl == null) {
            return;
        }
        SpotFinder.PlacementPlan plan = spotFinder.findPlacement(world, tpl, dungeons.values(), id);
        if (plan == null) {
            plugin.getLogger().warning("Could not find free spot for dungeon " + id + " (" + tplId + ")");
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
        int surfY = w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ(), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int maxY = tpl.getCategory() == DungeonCategory.UNDERGROUND
                ? Math.max(oy + tpl.getSizeY() - 1, surfY + 7)
                : oy + tpl.getSizeY() + 1;
        CuboidRegion bounds = new CuboidRegion(w.getName(), ox, oy - 3, oz, ox + tpl.getSizeX() - 1, maxY, oz + tpl.getSizeZ() - 1);
        String id = "custom_" + (dungeons.size() + 1) + "_" + (System.currentTimeMillis() % 1000);
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
        if (w == null) {
            return null;
        }
        if (saveTerrainFirst) {
            try {
                snapshotStore.saveWorldSnapshot(id, w, bounds);
            } catch (IOException ex) {
                plugin.getLogger().warning("Snapshot warning for " + id + ": " + ex.getMessage());
            }
        }
        long nextReset = System.currentTimeMillis() + resetIntervalMs();
        DungeonInstance inst = new DungeonInstance(
                id,
                tpl.getDisplayName(),
                tpl.getId(),
                tpl.getCategory(),
                tpl.getTier(),
                tpl.getTrialType(),
                origin,
                bounds,
                surfaceY,
                nextReset
        );
        inst.applyTemplateMarkers(tpl);

        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        boolean allowPvp = plugin.getConfig().getBoolean("protection.allow-pvp", true);
        registerDungeonRuntime(inst, pad, allowPvp);

        pasteDungeonBatched(w, inst, tpl);
        saveDungeons();
        plugin.getLogger().info("Placed " + inst.getCategory() + " dungeon '" + id + "' at "
                + origin.x() + ", " + origin.y() + ", " + origin.z());
        return inst;
    }

    private void registerDungeonRuntime(DungeonInstance inst, int pad, boolean allowPvp) {
        dungeons.put(inst.getId(), inst);
        reindexDungeonBlocks(inst);
        if (plugin.getConfig().getBoolean("protection.prevent-claims", true)) {
            plugin.getWorldGuardHook().syncDungeonRegion(inst, pad, allowPvp);
        }
    }

    private void reindexDungeonBlocks(DungeonInstance inst) {
        specialBlockIndex.entrySet().removeIf(e -> e.getValue().equals(inst.getId()));
        if (inst.getAltarPos() != null) {
            specialBlockIndex.put(inst.getAltarPos(), inst.getId());
        }
        for (BlockPosKey p : inst.getSealPositions()) {
            specialBlockIndex.put(p, inst.getId());
        }
        for (BlockPosKey p : inst.getRewardChests()) {
            specialBlockIndex.put(p, inst.getId());
        }
        for (BlockPosKey p : inst.getExplorerChests()) {
            specialBlockIndex.put(p, inst.getId());
        }
    }

    public void regenerateDungeon(DungeonInstance inst, boolean relocateOverride) {
        DungeonTemplate tpl = templates.get(inst.getTemplateId());
        World w = inst.getOrigin().world();
        if (tpl == null || w == null || inst.getStatus() == DungeonStatus.REGENERATING) {
            return;
        }
        plugin.getTrialController().abortTrial(inst.getId(), false);
        inst.setStatus(DungeonStatus.REGENERATING);

        boolean relocate = relocateOverride || plugin.getConfig().getBoolean("regeneration.relocate-on-reset", false);
        if (relocate) {
            removeHologram(inst);
            plugin.getWorldGuardHook().removeDungeonRegion(w, inst.getId());
            try {
                snapshotStore.restoreWorldSnapshotInstant(inst.getId());
            } catch (IOException ex) {
                plugin.getLogger().warning("Restore error on relocate " + inst.getId() + ": " + ex.getMessage());
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
                inst.applyTemplateMarkers(tpl);
                reindexDungeonBlocks(inst);
            }
            int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
            if (plugin.getConfig().getBoolean("protection.prevent-claims", true)) {
                plugin.getWorldGuardHook().syncDungeonRegion(inst, pad, plugin.getConfig().getBoolean("protection.allow-pvp", true));
            }
        }

        // First clean player-placed blocks around/above bounds via original snapshot before re-pasting template
        if (!relocate && snapshotStore.hasSnapshot(inst.getId())) {
            try {
                snapshotStore.restoreWorldSnapshotInstant(inst.getId());
            } catch (IOException ignored) {
            }
        }

        inst.setNextResetEpochMs(System.currentTimeMillis() + resetIntervalMs());
        pasteDungeonBatched(w, inst, tpl);
        saveDungeons();
    }

    public boolean removeDungeon(String id, boolean restoreTerrain) {
        DungeonInstance inst = dungeons.remove(id);
        if (inst == null) {
            return false;
        }
        plugin.getTrialController().abortTrial(id, false);
        specialBlockIndex.entrySet().removeIf(e -> e.getValue().equals(id));
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

        // Solid foundation apron under SURFACE dungeons so hills have zero floating edges
        if (inst.getCategory() == DungeonCategory.SURFACE) {
            BlockData cobble = Bukkit.createBlockData(Material.COBBLESTONE);
            BlockData stoneBricks = Bukkit.createBlockData(Material.STONE_BRICKS);
            for (int x = 1; x <= tpl.getSizeX() - 2; x++) {
                for (int z = 1; z <= tpl.getSizeZ() - 2; z++) {
                    for (int dy = -3; dy <= -1; dy++) {
                        writes.add(new BlockWrite(ox + x, oy + dy, oz + z, dy == -1 ? stoneBricks : cobble));
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

        // Build vertical crypt shaft + surface ruined gate for UNDERGROUND dungeons
        if (inst.getCategory() == DungeonCategory.UNDERGROUND) {
            appendUndergroundShaftWrites(writes, inst, tpl);
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
                    finalizePlacedDungeon(world, inst);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void appendUndergroundShaftWrites(List<BlockWrite> writes, DungeonInstance inst, DungeonTemplate tpl) {
        int shaftCenterX = inst.getOrigin().x() + tpl.getSizeX() / 2;
        int shaftCenterZ = inst.getOrigin().z() + tpl.getSizeZ() - 5;
        int startY = inst.getOrigin().y() + tpl.getSizeY() - 2;
        int topY = inst.getSurfaceY();

        BlockData air = Bukkit.createBlockData(Material.AIR);
        BlockData wall = Bukkit.createBlockData(Material.DEEPSLATE_BRICKS);
        BlockData mossy = Bukkit.createBlockData(Material.MOSSY_STONE_BRICKS);
        BlockData ladder = Bukkit.createBlockData("minecraft:ladder[facing=south]");
        BlockData soulLantern = Bukkit.createBlockData("minecraft:soul_lantern[hanging=false]");
        BlockData bar = Bukkit.createBlockData(Material.DEEPSLATE_BRICK_WALL);

        for (int y = startY; y <= topY; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean border = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    writes.add(new BlockWrite(shaftCenterX + dx, y, shaftCenterZ + dz, border ? wall : air));
                }
            }
            writes.add(new BlockWrite(shaftCenterX, y, shaftCenterZ - 1, ladder));
        }

        // Atmospheric ruined crypt entrance arch on surface
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                writes.add(new BlockWrite(shaftCenterX + dx, topY, shaftCenterZ + dz, mossy));
                for (int y = topY + 1; y <= topY + 5; y++) {
                    writes.add(new BlockWrite(shaftCenterX + dx, y, shaftCenterZ + dz, air));
                }
            }
        }
        int[][] posts = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : posts) {
            for (int y = topY + 1; y <= topY + 4; y++) {
                writes.add(new BlockWrite(shaftCenterX + p[0], y, shaftCenterZ + p[1], bar));
            }
            writes.add(new BlockWrite(shaftCenterX + p[0], topY + 5, shaftCenterZ + p[1], soulLantern));
        }
    }

    private void finalizePlacedDungeon(World world, DungeonInstance inst) {
        inst.setStatus(DungeonStatus.READY);

        // Fill explorer barrels immediately; keep reward chests physically empty until trial completion
        for (BlockPosKey expPos : inst.getExplorerChests()) {
            Block b = world.getBlockAt(expPos.x(), expPos.y(), expPos.z());
            if (b.getState() instanceof InventoryHolder holder) {
                plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), inst.getTier(), false, 3 + inst.getTier() / 2);
            }
        }
        for (BlockPosKey rewPos : inst.getRewardChests()) {
            Block b = world.getBlockAt(rewPos.x(), rewPos.y(), rewPos.z());
            if (b.getState() instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }
        }

        // Safely evacuate any player inside solid blocks to dungeon entrance
        Location safe = inst.teleportLocation();
        if (safe != null) {
            for (Player p : world.getPlayers()) {
                if (inst.getBounds().contains(p.getLocation())) {
                    p.setFallDistance(0f);
                    p.teleport(safe);
                    p.playSound(safe, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.35f);
                    p.sendMessage(plugin.msg("dungeon-regenerated").replace("{name}", inst.getDisplayName()));
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
                plugin.getTrialController().tickAllOncePerSecond();

                for (DungeonInstance d : new ArrayList<>(dungeons.values())) {
                    if (d.getStatus() != DungeonStatus.REGENERATING && now >= d.getNextResetEpochMs()) {
                        enqueueBuildJob(() -> regenerateDungeon(d, false));
                        continue;
                    }
                    World w = d.getOrigin().world();
                    if (w != null && d.getAltarPos() != null
                            && w.isChunkLoaded(d.getAltarPos().x() >> 4, d.getAltarPos().z() >> 4)) {
                        updateDungeonHologram(d);
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
        BlockPosKey altar = d.getAltarPos();
        if (altar == null) return;
        World w = altar.world();
        if (w == null || !w.isChunkLoaded(altar.x() >> 4, altar.z() >> 4)) {
            return;
        }

        Location holoLoc = new Location(w, altar.x() + 0.5, altar.y() + 2.35, altar.z() + 0.5);
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
        td.setLineWidth(plugin.getConfig().getInt("hologram.line-width", 260));
        td.setViewRange((float) plugin.getConfig().getDouble("hologram.view-range", 0.75));
        td.setText(buildHologramText(d));
    }

    private String buildHologramText(DungeonInstance d) {
        String timer = TextUtil.formatDuration(d.secondsUntilReset());
        String stars = TextUtil.stars(d.getTier());
        List<String> templateLines;
        String progress = "";
        if (d.getStatus() == DungeonStatus.ACTIVE_TRIAL) {
            templateLines = plugin.getConfig().getStringList("hologram.active-lines");
            TrialController.ActiveTrial tr = plugin.getTrialController().getTrial(d.getId());
            progress = tr != null ? tr.liveStatusLine : "&fИдёт бой...";
        } else if (d.getStatus() == DungeonStatus.CLEARED) {
            templateLines = plugin.getConfig().getStringList("hologram.cleared-lines");
        } else {
            templateLines = plugin.getConfig().getStringList("hologram.ready-lines");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < templateLines.size(); i++) {
            if (i > 0) sb.append('\n');
            String line = templateLines.get(i)
                    .replace("{name}", d.getDisplayName())
                    .replace("{stars}", stars)
                    .replace("{timer}", timer)
                    .replace("{progress}", progress);
            sb.append(TextUtil.color(line));
        }
        return sb.toString();
    }

    private TextDisplay findHologram(World w, DungeonInstance d, Location near) {
        if (d.getHologramUuid() != null) {
            Entity e = w.getEntity(d.getHologramUuid());
            if (e instanceof TextDisplay td && td.isValid()) {
                return td;
            }
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
        if (w == null) return;
        if (d.getHologramUuid() != null) {
            Entity e = w.getEntity(d.getHologramUuid());
            if (e != null) e.remove();
            d.setHologramUuid(null);
        }
    }

    public void handleChunkLoad(Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (e instanceof TextDisplay td) {
                String id = td.getPersistentDataContainer().get(hologramPdc, PersistentDataType.STRING);
                if (id != null && !dungeons.containsKey(id)) {
                    td.remove();
                }
            }
        }
        for (DungeonInstance d : dungeons.values()) {
            BlockPosKey alt = d.getAltarPos();
            if (alt != null && alt.worldName().equals(chunk.getWorld().getName())
                    && (alt.x() >> 4) == chunk.getX() && (alt.z() >> 4) == chunk.getZ()) {
                updateDungeonHologram(d);
            }
        }
    }

    public DungeonInstance getBySpecialBlock(BlockPosKey pos) {
        String id = specialBlockIndex.get(pos);
        return id == null ? null : dungeons.get(id);
    }

    public DungeonInstance findContainingClaimZone(Location loc) {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        for (DungeonInstance d : dungeons.values()) {
            if (d.claimGuardRegion(pad).contains(loc)) {
                return d;
            }
        }
        return null;
    }

    public DungeonInstance findIntersectingClaimZone(CuboidRegion selection) {
        int pad = plugin.getConfig().getInt("protection.claim-padding-blocks", 12);
        for (DungeonInstance d : dungeons.values()) {
            if (d.claimGuardRegion(pad).intersects3D(selection)) {
                return d;
            }
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
        return Collections.unmodifiableMap(templates);
    }

    public DungeonTemplate getTemplate(String id) {
        return templates.get(id.toLowerCase());
    }

    public long resetIntervalMs() {
        return Math.max(60L, plugin.getConfig().getLong("regeneration.interval-seconds", 43200L)) * 1000L;
    }

    private static String posList(int[] p) {
        return p[0] + ";" + p[1] + ";" + p[2];
    }

    private static List<String> toStringOffsets(List<int[]> list) {
        List<String> out = new ArrayList<>(list.size());
        for (int[] p : list) out.add(posList(p));
        return out;
    }

    private static int[] parseOffset(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(";");
        if (parts.length != 3) return null;
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static void readOffsets(List<String> raw, List<int[]> target) {
        target.clear();
        for (String s : raw) {
            int[] p = parseOffset(s);
            if (p != null) target.add(p);
        }
    }
}
