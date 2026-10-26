package org.bukkit.inventory;

public interface BrewerInventory extends Inventory {
    ItemStack getIngredient();
    void setIngredient(ItemStack ingredient);
    ItemStack getFuel();
    void setFuel(ItemStack fuel);
}
