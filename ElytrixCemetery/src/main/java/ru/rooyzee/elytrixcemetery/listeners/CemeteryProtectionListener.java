package ru.rooyzee.elytrixcemetery.listeners;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

public class CemeteryProtectionListener implements Listener {

    private final Main plugin;

    public CemeteryProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean isInCemetery(Location loc) {
        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(center.getWorld())) return false;
        int radius = plugin.getConfigManager().getBoundaryRadius();
        int dx = Math.abs(loc.getBlockX() - center.getBlockX());
        int dz = Math.abs(loc.getBlockZ() - center.getBlockZ());
        return dx <= radius && dz <= radius;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        // Do not undo WorldGuard or another plugin's protection decision.
        if (event.isCancelled()) return;
        if (player.hasPermission("elytrixcemetery.admin") && player.isSneaking()) return;
        if (!isInCemetery(block.getLocation())) return;

        // Leave the event untouched so other protection plugins can still veto it.
        if (plugin.getGraveManager().isAboveGraveColumn(block.getLocation())) return;

        // Grave blocks and their buried chests are handled by CemeteryDigListener.
        if (plugin.getGraveManager().getGraveAt(block.getLocation()) == null
                && plugin.getGraveManager().getGraveByChest(block.getLocation()) == null) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cТерритория кладбища защищена от разрушений!"));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("elytrixcemetery.admin") && player.isSneaking()) return;

        if (isInCemetery(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cСтроительство на территории кладбища запрещено!"));
        }
    }
}
