package ru.elytrix.dungeons.hook;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.elytrix.dungeons.model.CuboidRegion;

public final class WorldEditHook {

    private final boolean available;

    public WorldEditHook() {
        Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        this.available = (we != null && we.isEnabled()) || (fawe != null && fawe.isEnabled());
    }

    public boolean isAvailable() {
        return available;
    }

    public CuboidRegion getPlayerSelection(Player player) {
        if (!available || player == null) {
            return null;
        }
        try {
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object wePlayer = adapter.getMethod("adapt", Player.class).invoke(null, player);
            World w = player.getWorld();
            Object weWorld = adapter.getMethod("adapt", World.class).invoke(null, w);

            Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object weInst = weClass.getMethod("getInstance").invoke(null);
            Object sessionMgr = weClass.getMethod("getSessionManager").invoke(weInst);
            Class<?> sessionOwner = Class.forName("com.sk89q.worldedit.session.SessionOwner");
            Object localSession = sessionMgr.getClass().getMethod("getIfPresent", sessionOwner).invoke(sessionMgr, wePlayer);
            if (localSession == null) {
                return null;
            }
            Class<?> weWorldIface = Class.forName("com.sk89q.worldedit.world.World");
            Object region = localSession.getClass().getMethod("getSelection", weWorldIface).invoke(localSession, weWorld);
            if (region == null) {
                return null;
            }
            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);
            Method gx = min.getClass().getMethod("x");
            Method gy = min.getClass().getMethod("y");
            Method gz = min.getClass().getMethod("z");
            return new CuboidRegion(
                    w.getName(),
                    (int) gx.invoke(min), (int) gy.invoke(min), (int) gz.invoke(min),
                    (int) gx.invoke(max), (int) gy.invoke(max), (int) gz.invoke(max)
            ).normalized();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
