package ru.rooyzee.elytrixitem.util;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackUtil {

    private ItemStackUtil() {
    }

    public static List<ItemStack> split(ItemStack base, int amount) {
        List<ItemStack> result = new ArrayList<>();
        int maxStackSize = Math.max(1, base.getMaxStackSize());
        int left = amount;

        while (left > 0) {
            int current = Math.min(left, maxStackSize);
            ItemStack clone = base.clone();
            clone.setAmount(current);
            result.add(clone);
            left -= current;
        }

        return result;
    }

    /** Keep vanilla enchantments, attributes, potion effects, and special tooltips visible. */
    public static void showAllDetails(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Old builds hid every tooltip flag, concealing real enchants and potion effects.
        meta.removeItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
    }
}