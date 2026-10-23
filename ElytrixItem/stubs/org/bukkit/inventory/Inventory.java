package org.bukkit.inventory;

import java.util.HashMap;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;

public interface Inventory {
    InventoryHolder getHolder(boolean useSnapshot);
    InventoryHolder getHolder();
    int getSize();
    void setItem(int slot, ItemStack item);
    ItemStack getItem(int slot);
    ItemStack[] getContents();
    void setContents(ItemStack[] items);
    HashMap<Integer, ItemStack> addItem(ItemStack... items);
    HashMap<Integer, ItemStack> removeItem(ItemStack... items);
    void clear();
    void clear(int index);
    boolean contains(ItemStack item);
    boolean contains(org.bukkit.Material material);
    int firstEmpty();
    List<HumanEntity> getViewers();
    Location getLocation();
    int getMaxStackSize();
}
