package ru.elytrix.dungeons.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonTemplate;

/**
 * Finds ideal, flat, water-free locations across ungenerated chunks before a wipe:
 *  - Explicitly generates/loads candidate chunks, scores slope across 9 points, picks flattest spot
 *  - Unloads unused candidate chunks so server memory stays lean
 *  - Computes both exact structureBounds and wide surrounding snapshotBounds (+12 XZ, +8 Y, and up to surface)
 */
public final class SpotFinder {

    public record PlacementPlan(
            BlockPosKey origin,
            CuboidRegion structureBounds,
            CuboidRegion snapshotBounds,
            int surfaceY,
            int slopeDelta
    ) {}

    private final ElytrixDungeonsPlugin plugin;

    public SpotFinder(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public PlacementPlan findPlacement(
            World world,
            DungeonTemplate tpl,
            Collection<DungeonInstance> existingDungeons,
            String ignoreDungeonId
    ) {
        int centerX = plugin.getConfig().getInt("world-settings.center-x", 0);
        int centerZ = plugin.getConfig().getInt("world-settings.center-z", 0);
        int halfSize = Math.max(250, plugin.getConfig().getInt("world-settings.half-size", 1420));
        int spawnExcl = Math.max(40, plugin.getConfig().getInt("world-settings.spawn-exclusion-radius", 200));
        int baseMinDist = Math.max(80, plugin.getConfig().getInt("world-settings.min-distance-between-dungeons", 340));
        int claimPad = tpl.getCategory() == DungeonCategory.UNDERGROUND
                ? Math.max(0, plugin.getConfig().getInt("underground-dungeon.claim-padding-blocks", 8))
                : Math.max(8, plugin.getConfig().getInt("protection.claim-padding-blocks", 40));
        int candidatesTarget = Math.max(8, plugin.getConfig().getInt("auto-spawn.candidates-per-dungeon", 24));

        Location worldSpawn = world.getSpawnLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        PlacementPlan bestPlan = null;

        double[] relaxFactors = {1.0, 0.72, 0.5};
        for (double factor : relaxFactors) {
            double reqDistSq = Math.pow(baseMinDist * factor, 2);
            for (int attempt = 0; attempt < candidatesTarget; attempt++) {
                int ox = centerX + rng.nextInt(-halfSize, Math.max(-halfSize + 1, halfSize - tpl.getSizeX()));
                int oz = centerZ + rng.nextInt(-halfSize, Math.max(-halfSize + 1, halfSize - tpl.getSizeZ()));
                int midX = ox + tpl.getSizeX() / 2;
                int midZ = oz + tpl.getSizeZ() / 2;

                if (Math.hypot(midX - centerX, midZ - centerZ) < spawnExcl) continue;
                if (Math.hypot(midX - worldSpawn.getBlockX(), midZ - worldSpawn.getBlockZ()) < spawnExcl) continue;
                if (tooCloseToExisting(world.getName(), midX, midZ, existingDungeons, ignoreDungeonId, reqDistSq)) continue;

                Set<Long> newlyLoaded = preloadFootprintChunks(world, ox, oz, tpl.getSizeX(), tpl.getSizeZ());
                PlacementPlan cand = evaluateSpot(world, tpl, ox, oz, claimPad, rng, factor < 0.8);
                unloadTemporaryChunks(world, newlyLoaded);

                if (cand != null) {
                    if (bestPlan == null || cand.slopeDelta() < bestPlan.slopeDelta()) {
                        bestPlan = cand;
                        if (bestPlan.slopeDelta() <= 2) {
                            return bestPlan;
                        }
                    }
                }
            }
            if (bestPlan != null) {
                return bestPlan;
            }
        }
        return null;
    }

    private PlacementPlan evaluateSpot(
            World world,
            DungeonTemplate tpl,
            int ox,
            int oz,
            int claimPad,
            ThreadLocalRandom rng,
            boolean relaxedSlope
    ) {
        int sx = tpl.getSizeX(), sy = tpl.getSizeY(), sz = tpl.getSizeZ();
        int midX = ox + sx / 2, midZ = oz + sz / 2;

        // 9-point terrain flatness & dry-ground inspection across the entire schematic footprint
        int[][] probePts = {
                {midX, midZ},
                {ox + 1, oz + 1},
                {ox + sx - 2, oz + 1},
                {ox + 1, oz + sz - 2},
                {ox + sx - 2, oz + sz - 2},
                {midX, oz + 1},
                {midX, oz + sz - 2},
                {ox + 1, midZ},
                {ox + sx - 2, midZ}
        };

        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        for (int[] p : probePts) {
            int topY = world.getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(p[0], topY - 1, p[1]);
            Material gm = ground.getType();
            if (ground.isLiquid() || gm == Material.WATER || gm == Material.LAVA
                    || gm == Material.ICE || gm == Material.BLUE_ICE || gm == Material.PACKED_ICE
                    || gm == Material.KELP || gm == Material.SEAGRASS || gm == Material.MAGMA_BLOCK) {
                return null;
            }
            minH = Math.min(minH, topY);
            maxH = Math.max(maxH, topY);
        }

        int delta = maxH - minH;
        int surfaceY = world.getHighestBlockYAt(midX, midZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int bufXZ = Math.max(4, plugin.getConfig().getInt("protection.surround-restore-padding-xz", 12));
        int bufY = Math.max(4, plugin.getConfig().getInt("protection.surround-restore-padding-y", 8));

        if (tpl.getCategory() == DungeonCategory.SURFACE) {
            if (delta > (relaxedSlope ? 8 : 4)) {
                return null;
            }
            int oy = Math.max(world.getMinHeight() + 8, minH);
            CuboidRegion structBox = new CuboidRegion(world.getName(), ox, oy, oz, ox + sx - 1, oy + sy - 1, oz + sz - 1);
            CuboidRegion snapBox = structBox.expand(bufXZ, bufY);
            if (plugin.getWorldGuardHook().hasForeignRegions(world, structBox.expand(claimPad, 12))) {
                return null;
            }
            return new PlacementPlan(new BlockPosKey(world.getName(), ox, oy, oz), structBox, snapBox, surfaceY, delta);
        }

        // UNDERGROUND: deep schematic + wide surrounding rock + full column up to grass surface!
        int minUg = Math.max(world.getMinHeight() + 10, plugin.getConfig().getInt("world-settings.underground-min-y", -24));
        int maxUg = Math.min(surfaceY - sy - 12, plugin.getConfig().getInt("world-settings.underground-max-y", 8));
        if (maxUg <= minUg) {
            return null;
        }
        int oy = rng.nextInt(minUg, maxUg + 1);
        CuboidRegion structBox = new CuboidRegion(world.getName(), ox, oy, oz, ox + sx - 1, oy + sy - 1, oz + sz - 1);

        boolean toSurface = plugin.getConfig().getBoolean("protection.restore-column-to-surface", true);
        int snapMaxY = toSurface ? Math.max(oy + sy - 1 + bufY, surfaceY + 6) : oy + sy - 1 + bufY;
        CuboidRegion snapBox = new CuboidRegion(
                world.getName(),
                ox - bufXZ, Math.max(world.getMinHeight(), oy - bufY), oz - bufXZ,
                ox + sx - 1 + bufXZ, Math.min(world.getMaxHeight() - 1, snapMaxY), oz + sz - 1 + bufXZ
        );

        if (plugin.getWorldGuardHook().hasForeignRegions(world, snapBox.expand(claimPad, 6))) {
            return null;
        }
        return new PlacementPlan(new BlockPosKey(world.getName(), ox, oy, oz), structBox, snapBox, surfaceY, delta);
    }

    private static Set<Long> preloadFootprintChunks(World world, int ox, int oz, int sx, int sz) {
        Set<Long> loadedNow = new HashSet<>();
        int minCx = (ox - 2) >> 4, maxCx = (ox + sx + 2) >> 4;
        int minCz = (oz - 2) >> 4, maxCz = (oz + sz + 2) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz);
                    loadedNow.add((((long) cx) << 32) ^ (cz & 0xffffffffL));
                }
            }
        }
        return loadedNow;
    }

    private static void unloadTemporaryChunks(World world, Set<Long> keys) {
        for (long key : keys) {
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (world.isChunkLoaded(cx, cz)) {
                Chunk ch = world.getChunkAt(cx, cz);
                ch.unload();
            }
        }
    }

    private static boolean tooCloseToExisting(
            String worldName,
            int midX,
            int midZ,
            Collection<DungeonInstance> existing,
            String ignoreId,
            double minDistSq
    ) {
        for (DungeonInstance d : existing) {
            if (d.getId().equalsIgnoreCase(ignoreId)) continue;
            if (!d.getOrigin().worldName().equals(worldName)) continue;
            int dx = (d.getBounds().minX() + d.getBounds().maxX()) / 2 - midX;
            int dz = (d.getBounds().minZ() + d.getBounds().maxZ()) / 2 - midZ;
            if ((double) dx * dx + (double) dz * dz < minDistSq) {
                return true;
            }
        }
        return false;
    }
}
