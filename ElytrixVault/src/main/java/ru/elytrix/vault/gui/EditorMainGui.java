package ru.elytrix.vault.gui;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.util.TextUtil;

public class EditorMainGui implements InventoryHolder {

    private static final int[] CENTER_5_SLOTS = {11, 12, 13, 14, 15};

    private final ElytrixVaultPlugin plugin;
    private final Inventory inventory;
    private final List<String> slotKeyIds = new ArrayList<>();

    public EditorMainGui(ElytrixVaultPlugin plugin) {
        this.plugin = plugin;
        List<VaultKeyType> allKeys = new ArrayList<>(plugin.getKeyRegistry().getAllKeys());
        int size = allKeys.size() <= 7 ? 27 : 54;
        this.inventory = Bukkit.createInventory(this, size, TextUtil.color("&#F8BEFB&lРедактор ключей"));
        for (int i = 0; i < size; i++) {
            slotKeyIds.add(null);
        }
        render(allKeys);
    }

    private void render(List<VaultKeyType> allKeys) {
        inventory.clear();
        for (int i = 0; i < slotKeyIds.size(); i++) {
            slotKeyIds.set(i, null);
        }

        if (allKeys.size() == 5 && inventory.getSize() == 27) {
            for (int i = 0; i < 5; i++) {
                placeKeyCard(CENTER_5_SLOTS[i], allKeys.get(i));
            }
        } else {
            int slot = 10;
            for (VaultKeyType keyType : allKeys) {
                while (slot < inventory.getSize() - 9 && (slot % 9 == 0 || slot % 9 == 8)) {
                    slot++;
                }
                if (slot >= inventory.getSize() - 9) break;
                placeKeyCard(slot, keyType);
                slot++;
            }
        }

        ItemStack close = new ItemStack(Material.RED_DYE, 1);
        ItemMeta cm = close.getItemMeta();
        if (cm != null) {
            cm.setDisplayName(TextUtil.color("&7« &#F8BEFBЗакрыть &7»"));
            cm.setLore(TextUtil.colorList(List.of(
                    "&#F8BEFB&l┃ ",
                    "&#F8BEFB&l┃ &fЗакрыть меню редактора.",
                    "&#F8BEFB&l┃ ",
                    "&7● &fНажмите для закрытия"
            )));
            cm.addItemFlags(ItemFlag.values());
            close.setItemMeta(cm);
        }
        inventory.setItem(inventory.getSize() - 1, close);
    }

    private void placeKeyCard(int slot, VaultKeyType keyType) {
        ItemStack icon = new ItemStack(keyType.getKeyMaterial(), 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color("&7« " + keyType.getDisplayName() + " &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.color("&#F8BEFB&l┃ "));
            lore.add(TextUtil.color("&#F8BEFB&l┃ &fID: &#F8BEFB" + keyType.getId()));
            lore.add(TextUtil.color("&#F8BEFB&l┃ &fУровень лута: &#F8BEFB" + keyType.getLevel() + "/5 &7(" + keyType.getKeyMaterialTitle() + ")"));
            if (keyType.getMinRolls() == keyType.getMaxRolls()) {
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fВыпадает наград: &#F8BEFB" + keyType.getMinRolls()));
            } else {
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fВыпадает наград: &#F8BEFBот " + keyType.getMinRolls() + " до " + keyType.getMaxRolls()));
            }
            lore.add(TextUtil.color("&#F8BEFB&l┃ &fПредметов в пуле: &#F8BEFB" + keyType.getEntries().size()));
            lore.add(TextUtil.color("&#F8BEFB&l┃ "));
            lore.add(TextUtil.color("&7● &fЛКМ - настроить лут и редкости"));
            lore.add(TextUtil.color("&7● &fПКМ - получить ключ x1"));
            lore.add(TextUtil.color("&7● &fShift + ЛКМ - уровень (1/5-5/5)"));
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            meta.addItemFlags(ItemFlag.values());
            icon.setItemMeta(meta);
        }
        inventory.setItem(slot, icon);
        slotKeyIds.set(slot, keyType.getId());
    }

    public void refresh() {
        render(new ArrayList<>(plugin.getKeyRegistry().getAllKeys()));
    }

    public String getKeyIdAt(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= slotKeyIds.size()) {
            return null;
        }
        return slotKeyIds.get(rawSlot);
    }

    public int getCloseSlot() {
        return inventory.getSize() - 1;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
