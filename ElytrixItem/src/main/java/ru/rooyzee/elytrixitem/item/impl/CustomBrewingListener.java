package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;

/**
 * Установка и работа кастомных зельеварок.
 *
 * Огненный порошок в кастомной зельеварке даёт в fuel-power раз больше
 * топлива, чем в обычной.
 */
public final class CustomBrewingListener implements Listener {

    private static final int VANILLA_FUEL = 20;

    private final Main plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey blockKey;

    public CustomBrewingListener(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item_id");
        this.blockKey = new NamespacedKey(plugin, "custom_block_id");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.BREWING_STAND) {
            return;
        }

        CustomBrewingItem stand = standFrom(event.getItemInHand());
        if (stand == null) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) {
            return;
        }

        tile.getPersistentDataContainer().set(blockKey, PersistentDataType.STRING, stand.getId());
        if (state instanceof Nameable nameable) {
            nameable.setCustomName(stand.getDisplayName());
        }
        state.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFuel(BrewingStandFuelEvent event) {
        CustomBrewingItem stand = standAt(event.getBlock());
        if (stand == null || stand.getFuelPower() <= 1) {
            return;
        }

        event.setFuelPower(VANILLA_FUEL * stand.getFuelPower());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CustomBrewingItem stand = standAt(block);
        if (stand == null) {
            return;
        }

        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }

        event.setDropItems(false);

        World world = block.getWorld();
        Location drop = block.getLocation().add(0.5D, 0.5D, 0.5D);

        world.dropItemNaturally(drop, stand.createItem());

        if (block.getState() instanceof Container container) {
            for (ItemStack content : container.getSnapshotInventory().getContents()) {
                if (content != null && content.getType() != Material.AIR) {
                    world.dropItemNaturally(drop, content);
                }
            }
        }
    }

    private CustomBrewingItem standAt(Block block) {
        if (block.getType() != Material.BREWING_STAND) {
            return null;
        }

        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) {
            return null;
        }

        String id = tile.getPersistentDataContainer().get(blockKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }

        CustomItem custom = plugin.getItemRegistry().get(id);
        return custom instanceof CustomBrewingItem stand ? stand : null;
    }

    private CustomBrewingItem standFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        String id = item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }

        CustomItem custom = plugin.getItemRegistry().get(id);
        return custom instanceof CustomBrewingItem stand ? stand : null;
    }
}
