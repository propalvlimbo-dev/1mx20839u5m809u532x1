package org.bukkit.inventory;

/** Заглушка инвентаря игрока — нужна только для компиляции. */
public interface PlayerInventory extends Inventory {
    ItemStack[] getContents();
    ItemStack[] getArmorContents();
    ItemStack getItemInOffHand();
    ItemStack getItemInMainHand();
}
