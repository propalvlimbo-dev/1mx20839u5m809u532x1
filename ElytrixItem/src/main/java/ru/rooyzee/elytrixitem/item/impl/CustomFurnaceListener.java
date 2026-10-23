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
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;

/**
 * Установка и работа кастомных печек.
 *
 * Id печки хранится прямо в блоке (PersistentDataContainer), поэтому
 * после перезапуска сервера печка остаётся кастомной.
 */
public final class CustomFurnaceListener implements Listener {

    private final Main plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey blockKey;

    public CustomFurnaceListener(Main plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item_id");
        this.blockKey = new NamespacedKey(plugin, "custom_block_id");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.FURNACE) {
            return;
        }

        ItemStack item = event.getItemInHand();
        CustomFurnaceItem furnace = furnaceFrom(item);
        if (furnace == null) {
            return;
        }

        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) {
            return;
        }

        tile.getPersistentDataContainer().set(blockKey, PersistentDataType.STRING, furnace.getId());
        if (state instanceof Nameable nameable) {
            nameable.setCustomName(furnace.getDisplayName());
        }
        state.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        CustomFurnaceItem furnace = furnaceAt(event.getBlock());
        if (furnace == null || furnace.getFuelEconomy() <= 1) {
            return;
        }

        // Топливо горит дольше — тратится меньше.
        event.setBurnTime(event.getBurnTime() * furnace.getFuelEconomy());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStartSmelt(FurnaceStartSmeltEvent event) {
        CustomFurnaceItem furnace = furnaceAt(event.getBlock());
        if (furnace == null || furnace.getCookSpeed() <= 1) {
            return;
        }

        int cookTime = Math.max(1, event.getTotalCookTime() / furnace.getCookSpeed());
        event.setTotalCookTime(cookTime);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        CustomFurnaceItem furnace = furnaceAt(event.getBlock());
        if (furnace == null || furnace.getResultMultiplier() <= 1) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }

        int base = result.getAmount();
        int amount = base * furnace.getResultMultiplier();

        // Не переполняем слот выхода: в нём может уже лежать стопка.
        int existing = 0;
        BlockState state = event.getBlock().getState();
        if (state instanceof Container container) {
            ItemStack output = container.getSnapshotInventory().getItem(2);
            if (output != null && output.isSimilar(result)) {
                existing = output.getAmount();
            }
        }

        amount = Math.max(base, Math.min(amount, result.getMaxStackSize() - existing));
        if (amount > base) {
            result.setAmount(amount);
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CustomFurnaceItem furnace = furnaceAt(block);
        if (furnace == null) {
            return;
        }

        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }

        event.setDropItems(false);

        World world = block.getWorld();
        Location drop = block.getLocation().add(0.5D, 0.5D, 0.5D);

        world.dropItemNaturally(drop, furnace.createItem());

        if (block.getState() instanceof Container container) {
            for (ItemStack content : container.getSnapshotInventory().getContents()) {
                if (content != null && content.getType() != Material.AIR) {
                    world.dropItemNaturally(drop, content);
                }
            }
        }
    }

    private CustomFurnaceItem furnaceAt(Block block) {
        if (block.getType() != Material.FURNACE) {
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
        return custom instanceof CustomFurnaceItem furnace ? furnace : null;
    }

    private CustomFurnaceItem furnaceFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        String id = item.getItemMeta().getPersistentDataContainer()
                .get(itemKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }

        CustomItem custom = plugin.getItemRegistry().get(id);
        return custom instanceof CustomFurnaceItem furnace ? furnace : null;
    }
}
