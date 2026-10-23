package org.bukkit.inventory;
public interface Inventory {
    InventoryHolder getHolder(boolean useSnapshot);
    InventoryHolder getHolder();
    int getSize();
    void setItem(int slot, ItemStack item);
    ItemStack getItem(int slot);
}
