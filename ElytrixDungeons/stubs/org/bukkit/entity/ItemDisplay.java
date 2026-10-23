package org.bukkit.entity;

import org.bukkit.inventory.ItemStack;

public interface ItemDisplay extends Display {
    ItemStack getItemStack();
    void setItemStack(ItemStack item);
    ItemDisplayTransform getItemDisplayTransform();
    void setItemDisplayTransform(ItemDisplayTransform display);

    enum ItemDisplayTransform {
        NONE,
        THIRDPERSON_LEFTHAND,
        THIRDPERSON_RIGHTHAND,
        FIRSTPERSON_LEFTHAND,
        FIRSTPERSON_RIGHTHAND,
        HEAD,
        GUI,
        GROUND,
        FIXED
    }
}
