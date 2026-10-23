package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public interface Item extends Entity {
    ItemStack getItemStack();
    void setItemStack(ItemStack stack);
    int getPickupDelay();
    void setPickupDelay(int delay);
    void setUnlimitedLifetime(boolean unlimited);
    boolean isUnlimitedLifetime();
    UUID getOwner();
    void setOwner(UUID owner);
    void setThrower(UUID uuid);
    boolean canMobPickup();
    void setCanMobPickup(boolean canMobPickup);
    boolean canPlayerPickup();
    void setCanPlayerPickup(boolean canPlayerPickup);
    boolean willAge();
    void setWillAge(boolean willAge);
}
