package ru.elytrix.dungeons.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;

import java.lang.reflect.Method;

/** Bridges dungeon rewards to the single Ghost Shard factory owned by ElytrixCemetery. */
public final class CemeteryShardHook {

    private final ElytrixDungeonsPlugin plugin;
    private boolean warned;

    public CemeteryShardHook(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createGhostShard(int amount) {
        Plugin cemetery = Bukkit.getPluginManager().getPlugin("ElytrixCemetery");
        if (cemetery == null || !cemetery.isEnabled()) {
            warnOnce("ElytrixCemetery is missing or disabled.");
            return null;
        }

        try {
            Method factory = cemetery.getClass().getMethod("createGhostShard", int.class);
            Object result = factory.invoke(cemetery, Math.max(1, amount));
            if (result instanceof ItemStack) return (ItemStack) result;
            warnOnce("ElytrixCemetery did not return an ItemStack.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            warnOnce("The installed ElytrixCemetery version does not expose createGhostShard(int).");
        }
        return null;
    }

    private void warnOnce(String reason) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning(reason + " The dungeon will not create a duplicate Ghost Shard.");
    }
}
