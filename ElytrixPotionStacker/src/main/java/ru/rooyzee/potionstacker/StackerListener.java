package ru.rooyzee.potionstacker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * Мгновенная нормализация зелий во всех точках входа и тройная защита
 * варочной стойки от дюпа/потери зелий (включая spigot hopper-amount > 1,
 * боковые воронки с колбами + порошком ифрита и paper hopper.disable-move-event: true).
 */
public final class StackerListener implements Listener {

    private final Main plugin;
    private final Stacker stacker;

    public StackerListener(Main plugin) {
        this.plugin = plugin;
        this.stacker = plugin.getStacker();
    }

    // ---------- мгновенная нормализация зелий ----------

    /**
     * До обработки любого клика сервером приводим оба предмета к единому
     * max_stack_size — два одинаковых зелья всегда стакаются с первого клика,
     * даже если одно из них только что выдали командой, китом или в магазине.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current != null && stacker.normalize(current)) {
            event.setCurrentItem(current);
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && stacker.normalize(cursor)) {
            event.setCursor(cursor);
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PlayerInventory playerInv = player.getInventory();
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            if (button >= 0 && button < 9) {
                ItemStack hotbar = playerInv.getItem(button);
                if (hotbar != null && stacker.normalize(hotbar)) {
                    playerInv.setItem(button, hotbar);
                }
            }
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = playerInv.getItemInOffHand();
            if (offhand != null && stacker.normalize(offhand)) {
                playerInv.setItemInOffHand(offhand);
            }
        }
    }

    /** Подбор с пола: зелье сразу сливается с уже лежащим стаком в хотбаре. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        if (entity == null) {
            return;
        }
        ItemStack stack = entity.getItemStack();
        if (stacker.normalize(stack)) {
            entity.setItemStack(stack);
        }
    }

    /** Дропы на земле (ведьмы, пиглины, сломанные сундуки, выброс из рук). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        if (entity == null) {
            return;
        }
        ItemStack stack = entity.getItemStack();
        if (stacker.normalize(stack)) {
            entity.setItemStack(stack);
        }
    }

    /** Вход игрока: инвентарь и эндер-сундук. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stacker.normalize(player.getInventory());
        stacker.normalize(player.getEnderChest());
    }

    /** Открытие любого внешнего контейнера. */
    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        stacker.normalize(event.getInventory());
        if (event.getPlayer() instanceof Player player) {
            stacker.normalize(player.getInventory());
        }
    }

    /** Крафт. */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result != null && stacker.normalize(result)) {
            event.setCurrentItem(result);
        }
    }

    /** Набор воды пустой стеклянной бутылочкой из источника или котла. */
    @EventHandler
    public void onBottleFill(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GLASS_BOTTLE) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                stacker.normalize(player.getInventory());
            }
        });
    }

    // ---------- тройная защита варочной стойки ----------

    /**
     * Рубеж 1: воронки и выбрасыватели.
     *
     * В Paper HopperBlockEntity#hopperPush при spigot hopper-amount > 1
     * подмена event.setItem(amount=1) уничтожает (hopperAmount - 1) предметов.
     * Поэтому:
     *  - если источник сверху над стойкой — туда колбы в ванили не входят (только ингредиент),
     *    не мешаем циклу воронки перейти к слоту с наростом;
     *  - если все 3 слота колб уже заняты — тоже не отменяем, чтобы боковая воронка
     *    прошла дальше к слоту с огненным порошком;
     *  - если источник сбоку, есть пустой слот 0..2 и пачка > 1 — отменяем событие
     *    (все колбы в воронке целы) и переносим ровно 1 колбу.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Inventory destination = event.getDestination();
        if (destination.getType() == InventoryType.BREWING) {
            if (!stacker.isStackedPotion(item)) {
                return;
            }
            Inventory source = event.getSource();
            Location srcLoc = source.getLocation();
            Location dstLoc = destination.getLocation();
            if (srcLoc != null && dstLoc != null && srcLoc.getBlockY() > dstLoc.getBlockY()) {
                return;
            }
            if (firstEmptyBottleSlot(destination) == -1) {
                return;
            }
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> moveSinglePotionToBrewer(source, destination));
            return;
        }

        // Мутируем на месте, сохраняя ссылку event.getItem() == oitemstack (важно для DropperBlock).
        stacker.normalize(item);
    }

    /**
     * Рубеж 2: старт варки.
     *
     * Срабатывает даже если на сервере включён paper hopper.disable-move-event: true.
     * Если в слотах 0..2 оказалось больше 1 колбы — раскладывает по 1 в свободные
     * слоты 0..2, а лишние аккуратно возвращает дропом над стойкой.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewStart(BrewingStartEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Container container) {
            splitStackedBrewerSlots(block, container.getInventory());
        }
    }

    /**
     * Рубеж 3: завершение варки.
     *
     * Гарантирует строго 1 сваренное зелье на слот, возвращает любые лишние
     * несваренные колбы (если их успели подложить во время варки) и сразу
     * нормализует готовые зелья, чтобы нижняя воронка и Shift-клик складывали их в стак.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        BrewerInventory contents = event.getContents();
        if (contents != null) {
            for (int slot = 0; slot < 3; slot++) {
                ItemStack before = contents.getItem(slot);
                if (before != null && before.getType() != Material.AIR && before.getAmount() > 1) {
                    ItemStack refund = before.clone();
                    refund.setAmount(before.getAmount() - 1);
                    stacker.normalize(refund);
                    before.setAmount(1);
                    contents.setItem(slot, before);
                    dropAboveStand(block, refund);
                }
            }
        }

        List<ItemStack> results = event.getResults();
        for (ItemStack result : results) {
            if (result == null || result.getType() == Material.AIR) {
                continue;
            }
            if (result.getAmount() > 1) {
                result.setAmount(1);
            }
            stacker.normalize(result);
        }
    }

    private static int firstEmptyBottleSlot(Inventory brewer) {
        for (int slot = 0; slot < 3 && slot < brewer.getSize(); slot++) {
            ItemStack current = brewer.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    private void moveSinglePotionToBrewer(Inventory source, Inventory brewer) {
        int freeSlot = firstEmptyBottleSlot(brewer);
        if (freeSlot == -1) {
            return;
        }

        ItemStack[] contents = source.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !isBrewBottle(item)) {
                continue;
            }
            ItemStack single = item.clone();
            single.setAmount(1);
            stacker.normalize(single);
            brewer.setItem(freeSlot, single);

            if (item.getAmount() <= 1) {
                source.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                source.setItem(i, item);
            }
            return;
        }
    }

    private void splitStackedBrewerSlots(Block block, Inventory brewer) {
        for (int slot = 0; slot < 3 && slot < brewer.getSize(); slot++) {
            ItemStack item = brewer.getItem(slot);
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 1) {
                continue;
            }

            int extra = item.getAmount() - 1;
            item.setAmount(1);
            brewer.setItem(slot, item);

            for (int target = 0; target < 3 && target < brewer.getSize() && extra > 0; target++) {
                ItemStack existing = brewer.getItem(target);
                if (existing == null || existing.getType() == Material.AIR) {
                    ItemStack one = item.clone();
                    one.setAmount(1);
                    brewer.setItem(target, one);
                    extra--;
                }
            }

            if (extra > 0) {
                ItemStack refund = item.clone();
                refund.setAmount(extra);
                stacker.normalize(refund);
                dropAboveStand(block, refund);
            }
        }
    }

    private static boolean isBrewBottle(ItemStack item) {
        Material type = item.getType();
        return type == Material.POTION
                || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION
                || type == Material.GLASS_BOTTLE;
    }

    private static void dropAboveStand(Block block, ItemStack stack) {
        Location dropLocation = block.getLocation().add(0.5D, 1.05D, 0.5D);
        block.getWorld().dropItemNaturally(dropLocation, stack);
    }
}
