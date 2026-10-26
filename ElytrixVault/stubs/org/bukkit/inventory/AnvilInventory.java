package org.bukkit.inventory;
public interface AnvilInventory extends Inventory {
    void setRepairCost(int cost);

    String getRenameText();
    void setRenameText(String text);
    int getRepairCost();
    int getMaximumRepairCost();
    void setMaximumRepairCost(int levels);
    ItemStack getFirstItem();
    ItemStack getSecondItem();
    void setFirstItem(ItemStack item);
    void setSecondItem(ItemStack item);
    ItemStack getResult();
}
