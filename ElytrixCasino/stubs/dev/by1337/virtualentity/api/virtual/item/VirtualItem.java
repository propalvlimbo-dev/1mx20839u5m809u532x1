package dev.by1337.virtualentity.api.virtual.item;

import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import org.bukkit.inventory.ItemStack;

public interface VirtualItem extends VirtualEntity {
    static VirtualItem create() { return null; }
    void setItem(ItemStack item);
    ItemStack getItem();
}
