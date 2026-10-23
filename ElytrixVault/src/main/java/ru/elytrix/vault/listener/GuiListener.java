package ru.elytrix.vault.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.gui.EditorMainGui;
import ru.elytrix.vault.gui.KeyPoolEditorGui;
import ru.elytrix.vault.model.LootEntry;
import ru.elytrix.vault.model.Rarity;
import ru.elytrix.vault.model.VaultKeyType;

public class GuiListener implements Listener {

    private final ElytrixVaultPlugin plugin;

    public GuiListener(ElytrixVaultPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EditorMainGui) && !(holder instanceof KeyPoolEditorGui)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("elytrixvault.admin")) {
            player.closeInventory();
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) {
            return;
        }

        if (holder instanceof EditorMainGui mainGui) {
            if (clickedInv.equals(top)) {
                handleMainClick(player, mainGui, event.getRawSlot(), event.getClick());
            }
            return;
        }

        KeyPoolEditorGui poolGui = (KeyPoolEditorGui) holder;
        if (clickedInv.equals(top)) {
            handlePoolTopClick(player, poolGui, event.getRawSlot(), event.getClick());
        } else if (clickedInv.equals(event.getView().getBottomInventory())) {
            handlePoolAddFromPlayerInventory(player, poolGui, event.getCurrentItem());
        }
    }

    private void handleMainClick(Player player, EditorMainGui gui, int slot, ClickType click) {
        if (slot == gui.getCloseSlot()) {
            player.closeInventory();
            return;
        }
        String keyId = gui.getKeyIdAt(slot);
        if (keyId == null) {
            return;
        }
        VaultKeyType keyType = plugin.getKeyRegistry().getKey(keyId);
        if (keyType == null) {
            return;
        }

        if (click == ClickType.RIGHT) {
            plugin.getKeyRegistry().giveKeyToPlayer(player, player.getName(), keyType, 1);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            return;
        }

        if (click == ClickType.SHIFT_LEFT) {
            int nextLevel = keyType.getLevel() >= 5 ? 1 : keyType.getLevel() + 1;
            keyType.setLevel(nextLevel);
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }

        new KeyPoolEditorGui(plugin, keyType, 0).open(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.15f);
    }

    private void handlePoolTopClick(Player player, KeyPoolEditorGui gui, int slot, ClickType click) {
        VaultKeyType keyType = gui.getKeyType();

        if (slot == KeyPoolEditorGui.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == KeyPoolEditorGui.SLOT_BACK) {
            if (gui.getPage() > 0) {
                gui.setPage(gui.getPage() - 1);
            } else {
                new EditorMainGui(plugin).open(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            return;
        }
        if (slot == KeyPoolEditorGui.SLOT_NEXT_PAGE && gui.hasNextPage()) {
            gui.setPage(gui.getPage() + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.1f);
            return;
        }
        if (slot == KeyPoolEditorGui.SLOT_MIN_ROLLS) {
            int delta = isRightClick(click) ? -1 : 1;
            keyType.setMinRolls(keyType.getMinRolls() + delta);
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }
        if (slot == KeyPoolEditorGui.SLOT_MAX_ROLLS) {
            int delta = isRightClick(click) ? -1 : 1;
            keyType.setMaxRolls(keyType.getMaxRolls() + delta);
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }
        if (slot == KeyPoolEditorGui.SLOT_GIVE_KEY) {
            int amt = isRightClick(click) ? 5 : 1;
            plugin.getKeyRegistry().giveKeyToPlayer(player, player.getName(), keyType, amt);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.25f);
            return;
        }
        if (slot == KeyPoolEditorGui.SLOT_LEVEL) {
            int delta = isRightClick(click) ? -1 : 1;
            int next = keyType.getLevel() + delta;
            if (next > 5) next = 1;
            if (next < 1) next = 5;
            keyType.setLevel(next);
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }

        int entryIndex = gui.getEntryIndexForSlot(slot);
        if (entryIndex < 0 || entryIndex >= keyType.getEntries().size()) {
            return;
        }
        LootEntry entry = keyType.getEntries().get(entryIndex);

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.SHIFT_RIGHT) {
            keyType.getEntries().remove(entryIndex);
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.BLOCK_VAULT_CLOSE_SHUTTER, 0.65f, 1.2f);
            return;
        }

        if (click == ClickType.SHIFT_LEFT) {
            int current = entry.getEffectiveWeight(plugin.getKeyRegistry().getRarityWeights());
            entry.setCustomWeight(Math.min(500, current + 2));
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.25f);
            return;
        }

        if (click == ClickType.RIGHT) {
            int current = entry.getEffectiveWeight(plugin.getKeyRegistry().getRarityWeights());
            entry.setCustomWeight(Math.max(1, current - 1));
            plugin.getKeyRegistry().saveKeys();
            gui.refresh();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.9f);
            return;
        }

        entry.setRarity(entry.getRarity().next());
        entry.setCustomWeight(0);
        plugin.getKeyRegistry().saveKeys();
        gui.refresh();
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.75f, 1.25f);
    }

    private void handlePoolAddFromPlayerInventory(Player player, KeyPoolEditorGui gui, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String newId = "custom_" + System.currentTimeMillis() + "_" + gui.getKeyType().getEntries().size();
        LootEntry added = new LootEntry(newId, clicked.clone(), null, Rarity.RARE, 0);
        gui.getKeyType().getEntries().add(added);
        plugin.getKeyRegistry().saveKeys();
        int lastPage = Math.max(0, (gui.getKeyType().getEntries().size() - 1) / KeyPoolEditorGui.PAGE_SIZE);
        gui.setPage(lastPage);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof EditorMainGui || holder instanceof KeyPoolEditorGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof EditorMainGui || holder instanceof KeyPoolEditorGui) {
            event.setCancelled(true);
        }
    }

    private static boolean isRightClick(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }
}
