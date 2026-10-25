package ru.rooyzee.elytrixcemetery.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

public class CemeteryDigListener implements Listener {

    private final Main plugin;

    public CemeteryDigListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Grave grave = plugin.getGraveManager().getGraveAt(block.getLocation());
        boolean chestBlock = false;
        if (grave == null) {
            grave = plugin.getGraveManager().getGraveByChest(block.getLocation());
            chestBlock = grave != null;
        }
        if (grave == null) return;

        Player player = event.getPlayer();
        if (grave.isDug()) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Раскопанную могилу по-прежнему можно открыть ПКМ по сундуку.
                event.setCancelled(true);
                grave.openChestFor(player);
            }
            return;
        }

        // Refresh the remaining-cycle display on left-click, but do not change progress here.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && !chestBlock
                && isShovel(player.getInventory().getItemInMainHand())
                && plugin.getActionBarTask() != null) {
            plugin.getActionBarTask().showDigProgress(player, grave.getDigProgress());
        }

        // ПКМ никогда не начинает и не засчитывает раскопку. ЛКМ доходит до BlockBreakEvent.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!isShovel(player.getInventory().getItemInMainHand())) {
                player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cДля раскопки нужна лопата в основной руке."));
            } else {
                player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cПКМ не раскапывает могилу. Используйте ЛКМ: нужно 20 полных циклов ломания."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        // Never count a break cycle vetoed by another protection plugin.
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        Grave grave = plugin.getGraveManager().getGraveAt(block.getLocation());
        boolean chestBlock = false;
        if (grave == null) {
            grave = plugin.getGraveManager().getGraveByChest(block.getLocation());
            chestBlock = grave != null;
        }
        if (grave == null) return;

        // Не даём разрушить блок могилы: каждый завершённый BlockBreakEvent — один полный цикл.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (grave.isDug()) {
            if (chestBlock) grave.openChestFor(player);
            return;
        }
        if (chestBlock) {
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cСначала раскопайте верхний блок могилы лопатой."));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isShovel(hand)) {
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cЧтобы раскапывать могилу, используйте лопату."));
            return;
        }

        int progress = grave.recordDigCycle();
        if (progress >= Grave.REQUIRED_DIG_CYCLES) {
            boolean dug = grave.dig(plugin, player);
            if (dug) {
                player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aМогила раскопана после 20 полных циклов! Заберите сокровища из сундука."));
            }
            if (plugin.getActionBarTask() != null) {
                plugin.getActionBarTask().showDigProgress(player, progress);
            }
            return;
        }

        grave.updateHologram(plugin);
        if (plugin.getActionBarTask() != null) {
            plugin.getActionBarTask().showDigProgress(player, progress);
        }
    }

    private boolean isShovel(ItemStack hand) {
        return hand != null && hand.getType().name().endsWith("_SHOVEL");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getGraveManager().refreshHologramsInChunk(event.getChunk());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.getGhostController().handleDeath(event);
    }
}
