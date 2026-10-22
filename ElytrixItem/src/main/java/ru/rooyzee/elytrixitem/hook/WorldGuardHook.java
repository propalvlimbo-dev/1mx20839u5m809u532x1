package ru.rooyzee.elytrixitem.hook;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Проверки WorldGuard.
 *
 * WorldGuard — необязательная зависимость: если плагина нет (или он не
 * загрузился), все проверки пропускают действия. Поэтому любой вызов
 * обёрнут в try/catch: классы WorldGuard просто не резолвятся без плагина.
 */
public final class WorldGuardHook {
    private WorldGuardHook() {
    }

    public static boolean canBuild(Player player, Location location) {
        try {
            return WorldGuardAccess.canBuild(player, location);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Проверяет, пересекает ли прямоугольник ловушки регион или находится ли
     * ближе указанного количества блоков к его границе.
     */
    public static boolean isNearRegion(World world, int minX, int maxX, int minY, int maxY,
                                       int minZ, int maxZ, int margin) {
        try {
            return WorldGuardAccess.isNearRegion(world, minX, maxX, minY, maxY, minZ, maxZ, margin);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Отдельный класс, чтобы отсутствие WorldGuard не ломало загрузку хука. */
    private static final class WorldGuardAccess {

        static boolean canBuild(Player player, Location location) {
            if (com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst() == null) {
                return true;
            }
            com.sk89q.worldguard.protection.regions.RegionQuery query =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return query.testState(
                    com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location),
                    com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(player),
                    com.sk89q.worldguard.protection.flags.Flags.BUILD);
        }

        static boolean isNearRegion(World world, int minX, int maxX, int minY, int maxY,
                                    int minZ, int maxZ, int margin) {
            if (com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst() == null || world == null) {
                return false;
            }

            com.sk89q.worldguard.protection.managers.RegionManager manager =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer()
                            .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            if (manager == null) {
                return false;
            }

            int expandedMinX = minX - margin;
            int expandedMaxX = maxX + margin;
            int expandedMinY = minY - margin;
            int expandedMaxY = maxY + margin;
            int expandedMinZ = minZ - margin;
            int expandedMaxZ = maxZ + margin;

            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : manager.getRegions().values()) {
                if ("__global__".equalsIgnoreCase(region.getId())) {
                    continue;
                }
                com.sk89q.worldedit.math.BlockVector3 regionMin = region.getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 regionMax = region.getMaximumPoint();
                if (regionMin.getX() <= expandedMaxX && regionMax.getX() >= expandedMinX
                        && regionMin.getY() <= expandedMaxY && regionMax.getY() >= expandedMinY
                        && regionMin.getZ() <= expandedMaxZ && regionMax.getZ() >= expandedMinZ) {
                    return true;
                }
            }
            return false;
        }
    }
}
