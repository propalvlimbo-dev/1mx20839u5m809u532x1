package ru.elytrix.dungeons.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonInstance;

public final class WorldGuardHook {

    private static final String REGION_PREFIX = "elytrix_dungeon_";
    private boolean available;

    public WorldGuardHook() {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        this.available = wg != null && wg.isEnabled();
    }

    public boolean isAvailable() {
        return available;
    }

    public String regionIdFor(String dungeonId) {
        return REGION_PREFIX + dungeonId.toLowerCase();
    }

    public void syncDungeonRegion(DungeonInstance dungeon, int padding, boolean allowPvp, boolean allowBlockModify) {
        if (!available) return;
        World bukkitWorld = dungeon.getOrigin().world();
        if (bukkitWorld == null) return;
        try {
            Object regionManager = getRegionManager(bukkitWorld);
            if (regionManager == null) return;
            CuboidRegion box = dungeon.claimGuardRegion(padding);
            Class<?> bv3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method atMethod = bv3Class.getMethod("at", int.class, int.class, int.class);
            Object minVec = atMethod.invoke(null, box.minX(), Math.max(bukkitWorld.getMinHeight(), box.minY()), box.minZ());
            Object maxVec = atMethod.invoke(null, box.maxX(), Math.min(bukkitWorld.getMaxHeight() - 1, box.maxY()), box.maxZ());

            Class<?> cuboidClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Constructor<?> ctor = cuboidClass.getConstructor(String.class, bv3Class, bv3Class);
            Object region = ctor.newInstance(regionIdFor(dungeon.getId()), minVec, maxVec);

            Class<?> prClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            prClass.getMethod("setPriority", int.class).invoke(region, 100);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Class<?> flagInterface = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
            Class<?> stateEnum = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag$State");
            Object allowState = Enum.valueOf(stateEnum.asSubclass(Enum.class), "ALLOW");
            Object denyState = Enum.valueOf(stateEnum.asSubclass(Enum.class), "DENY");
            Method setFlag = prClass.getMethod("setFlag", flagInterface, Object.class);

            Object buildState = allowBlockModify ? allowState : denyState;
            setStateFlagIfPresent(flagsClass, setFlag, region, "PASSTHROUGH", buildState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "BUILD", buildState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "BLOCK_BREAK", buildState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "BLOCK_PLACE", buildState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "OTHER_EXPLOSION", buildState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "TNT", buildState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "INTERACT", allowState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "USE", allowState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "CHEST_ACCESS", allowState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "MOB_SPAWNING", allowState);
            setStateFlagIfPresent(flagsClass, setFlag, region, "PVP", allowPvp ? allowState : denyState);

            regionManager.getClass().getMethod("addRegion", prClass).invoke(regionManager, region);
        } catch (Throwable ignored) {
            this.available = false;
        }
    }

    public void removeDungeonRegion(World bukkitWorld, String dungeonId) {
        // Attempt cleanup even if a previous optional WorldGuard operation marked the hook unavailable.
        if (bukkitWorld == null) return;
        try {
            Object regionManager = getRegionManager(bukkitWorld);
            if (regionManager != null) {
                regionManager.getClass().getMethod("removeRegion", String.class)
                        .invoke(regionManager, regionIdFor(dungeonId));
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public boolean hasForeignRegions(World bukkitWorld, CuboidRegion area) {
        if (!available || bukkitWorld == null) return false;
        try {
            Object regionManager = getRegionManager(bukkitWorld);
            if (regionManager == null) return false;
            Map<String, Object> regions = (Map<String, Object>) regionManager.getClass()
                    .getMethod("getRegions").invoke(regionManager);
            if (regions == null || regions.isEmpty()) return false;

            Class<?> prClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Method minPoint = prClass.getMethod("getMinimumPoint");
            Method maxPoint = prClass.getMethod("getMaximumPoint");
            Class<?> bv3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method gx = bv3Class.getMethod("x"), gy = bv3Class.getMethod("y"), gz = bv3Class.getMethod("z");

            for (Map.Entry<String, Object> e : regions.entrySet()) {
                String id = e.getKey();
                if (id == null || id.equalsIgnoreCase("__global__") || id.startsWith(REGION_PREFIX)) continue;
                Object reg = e.getValue();
                Object mn = minPoint.invoke(reg), mx = maxPoint.invoke(reg);
                CuboidRegion foreign = new CuboidRegion(bukkitWorld.getName(),
                        (int) gx.invoke(mn), (int) gy.invoke(mn), (int) gz.invoke(mn),
                        (int) gx.invoke(mx), (int) gy.invoke(mx), (int) gz.invoke(mx));
                if (foreign.intersects3D(area)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void setStateFlagIfPresent(Class<?> flagsClass, Method setFlag, Object region, String fieldName, Object stateValue) {
        try {
            Field f = flagsClass.getField(fieldName);
            Object flagObj = f.get(null);
            if (flagObj != null) setFlag.invoke(region, flagObj, stateValue);
        } catch (Throwable ignored) {
        }
    }

    private static Object getRegionManager(World bukkitWorld) throws Exception {
        Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
        Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Object weWorld = adapterClass.getMethod("adapt", World.class).invoke(null, bukkitWorld);
        Class<?> weWorldIface = Class.forName("com.sk89q.worldedit.world.World");
        return container.getClass().getMethod("get", weWorldIface).invoke(container, weWorld);
    }
}
