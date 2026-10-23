package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixcasino.util.Text;

public final class GuiGuard {

    private GuiGuard() {
    }

    public static boolean isCasinoHolder(InventoryHolder holder) {
        return holder instanceof RouletteHolder
                || holder instanceof AutomateHolder
                || holder instanceof MinerBetHolder
                || holder instanceof MinerGameHolder;
    }

    public static ItemStack pane(Plugin plugin, Material material) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&r"));
            meta.addItemFlags(ItemFlag.values());
            markMeta(plugin, meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static void applyGlow(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        try {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    public static void markMeta(Plugin plugin, ItemMeta meta) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        }
    }

    public static ItemStack mark(Plugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMarked(Plugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    public static void strip(Plugin plugin, Player player) {
        player.setItemOnCursor(null);
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isMarked(plugin, contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setContents(contents);
        }
        player.updateInventory();
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "gui");
    }
}
