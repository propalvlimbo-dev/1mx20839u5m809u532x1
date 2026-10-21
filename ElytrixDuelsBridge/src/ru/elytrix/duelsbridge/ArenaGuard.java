package ru.elytrix.duelsbridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Правила на боевых копиях арен (arena_instance_*):
 *   * блоки и жидкости ставить нельзя;
 *   * взрывы не ломают ни одного блока.
 *
 * Шаблоны (arena_template_*) не трогаем — там администратор строит арену.
 * Срабатываем позже EpicDuels (HIGHEST/MONITOR), потому что его
 * WorldProtectionListener разрешает установку блоков во время боя.
 */
final class ArenaGuard implements Listener {

    private static final String INSTANCE = "arena_instance_";
    private static final long WARN_DELAY_MS = 3000L;

    private final ElytrixDuelsBridge plugin;
    private final Map<UUID, Long> warned = new HashMap<UUID, Long>();

    ArenaGuard(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * Общий мир арен нельзя выгрузить: родная команда /duel arena delete
     * в этом режиме попыталась бы удалить папку мира duel целиком.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (!plugin.isEnabled()) return;
        String shared = plugin.sharedArenaWorld();
        if (shared.isEmpty() || event.getWorld() == null) return;
        if (!shared.equals(event.getWorld().getName())) return;
        event.setCancelled(true);
        plugin.getLogger().warning("Арены: мир \"" + shared
                + "\" — общий для всех арен, выгружать его нельзя.");
    }

    private static boolean arena(String world) {
        return world != null && world.startsWith(INSTANCE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-place", true)) return;
        if (!arena(event.getBlock().getWorld().getName())) return;
        event.setCancelled(true);
        warn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-place", true)) return;
        if (!arena(event.getBlock().getWorld().getName())) return;
        event.setCancelled(true);
        warn(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosions", true)) return;
        if (!arena(event.getBlock().getWorld().getName())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosions", true)) return;
        if (!arena(event.getLocation().getWorld().getName())) return;
        event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.explosions", true)) return;
        if (!arena(event.getBlock().getWorld().getName())) return;
        event.blockList().clear();
    }

    private void warn(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = warned.get(id);
        if (last != null && now - last < WARN_DELAY_MS) return;
        warned.put(id, now);
        plugin.msg(player, plugin.string("messages.arena-no-build",
                "&#F8BEFBДуэли &8» &cНа арене нельзя ставить блоки."));
    }
}
