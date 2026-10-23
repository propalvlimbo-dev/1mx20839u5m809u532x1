package ru.elytrix.dungeons.service;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
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

public final class SpotFinder {

    public record PlacementPlan(BlockPosKey origin, CuboidRegion fullBounds, int surfaceY) {}

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
        int spawnExcl = Math.max(40, plugin.getConfig().getInt("world-settings.spawn-exclusion-radius", 180));
        int baseMinDist = Math.max(80, plugin.getConfig().getInt("world-settings.min-distance-between-dungeons", 320));
        int padding = Math.max(4, plugin.getConfig().getInt("protection.claim-padding-blocks", 12));

        Location worldSpawn = world.getSpawnLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double[] relaxFactors = {1.0, 0.72, 0.48};
        for (double factor : relaxFactors) {
            double reqDistSq = Math.pow(baseMinDist * factor, 2);
            for (int attempt = 0; attempt < 90; attempt++) {
                int ox = centerX + rng.nextInt(-halfSize, halfSize - tpl.getSizeX());
                int oz = centerZ + rng.nextInt(-halfSize, halfSize - tpl.getSizeZ());
                int midX = ox + tpl.getSizeX() / 2;
                int midZ = oz + tpl.getSizeZ() / 2;

                if (Math.hypot(midX - centerX, midZ - centerZ) < spawnExcl) {
                    continue;
                }
                if (Math.hypot(midX - worldSpawn.getBlockX(), midZ - worldSpawn.getBlockZ()) < spawnExcl) {
                    continue;
                }
                if (tooCloseToExisting(world.getName(), midX, midZ, existingDungeons, ignoreDungeonId, reqDistSq)) {
                    continue;
                }

                PlacementPlan plan = evaluateSpot(world, tpl, ox, oz, padding, rng, factor < 0.8);
                if (plan != null) {
                    return plan;
                }
            }
        }
        return null;
    }

    private PlacementPlan evaluateSpot(
            World world,
            DungeonTemplate tpl,
            int ox,
            int oz,
            int padding,
            ThreadLocalRandom rng,
            boolean relaxedSlope
    ) {
        int sx = tpl.getSizeX(), sy = tpl.getSizeY(), sz = tpl.getSizeZ();
        int midX = ox + sx / 2, midZ = oz + sz / 2;

        if (tpl.getCategory() == DungeonCategory.SKY) {
            int minSky = plugin.getConfig().getInt("world-settings.sky-min-y", 148);
            int maxSky = Math.min(world.getMaxHeight() - sy - 8, plugin.getConfig().getInt("world-settings.sky-max-y", 182));
            int oy = minSky >= maxSky ? minSky : rng.nextInt(minSky, maxSky + 1);
            int groundY = world.getHighestBlockYAt(midX, midZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (oy < groundY + 24) {
                oy = Math.min(world.getMaxHeight() - sy - 4, groundY + 30);
            }
            CuboidRegion box = new CuboidRegion(world.getName(), ox, oy, oz, ox + sx - 1, oy + sy - 1, oz + sz - 1);
            if (plugin.getWorldGuardHook().hasForeignRegions(world, box.expand(padding, 8))) {
                return null;
            }
            return new PlacementPlan(new BlockPosKey(world.getName(), ox, oy, oz), box, groundY);
        }

        int[][] probePts = {
            {midX, midZ},
            {ox + 2, oz + 2},
            {ox + sx - 3, oz + 2},
            {ox + 2, oz + sz - 3},
            {ox + sx - 3, oz + sz - 3}
        };
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        for (int[] p : probePts) {
            int topY = world.getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(p[0], topY - 1, p[1]);
            Material gm = ground.getType();
            if (ground.isLiquid() || gm == Material.WATER || gm == Material.LAVA
                    || gm == Material.ICE || gm == Material.BLUE_ICE || gm == Material.PACKED_ICE
                    || gm == Material.KELP || gm == Material.SEAGRASS) {
                return null;
            }
            minH = Math.min(minH, topY);
            maxH = Math.max(maxH, topY);
        }

        int surfaceY = world.getHighestBlockYAt(midX, midZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);

        if (tpl.getCategory() == DungeonCategory.SURFACE) {
            if (maxH - minH > (relaxedSlope ? 11 : 6)) {
                return null;
            }
            int oy = Math.max(world.getMinHeight() + 8, minH - 1);
            CuboidRegion box = new CuboidRegion(world.getName(), ox, oy - 4, oz, ox + sx - 1, oy + sy + 1, oz + sz - 1);
            if (plugin.getWorldGuardHook().hasForeignRegions(world, box.expand(padding, 8))) {
                return null;
            }
            return new PlacementPlan(new BlockPosKey(world.getName(), ox, oy, oz), box, surfaceY);
        }

        // UNDERGROUND: deep vault + vertical entrance shaft up through surfaceY
        int minUg = Math.max(world.getMinHeight() + 6, plugin.getConfig().getInt("world-settings.underground-min-y", -26));
        int maxUg = Math.min(surfaceY - sy - 16, plugin.getConfig().getInt("world-settings.underground-max-y", 6));
        if (maxUg <= minUg) {
            return null;
        }
        int oy = rng.nextInt(minUg, maxUg + 1);
        CuboidRegion fullColumn = new CuboidRegion(world.getName(), ox, oy, oz, ox + sx - 1, surfaceY + 7, oz + sz - 1);
        if (plugin.getWorldGuardHook().hasForeignRegions(world, fullColumn.expand(padding, 4))) {
            return null;
        }
        return new PlacementPlan(new BlockPosKey(world.getName(), ox, oy, oz), fullColumn, surfaceY);
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
