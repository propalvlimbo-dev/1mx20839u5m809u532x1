package org.bukkit.inventory;

public interface PlayerInventory extends Inventory {
    java.util.HashMap<Integer, ItemStack> addItem(ItemStack... items);
    void setItemInMainHand(ItemStack item);
    void setItemInOffHand(ItemStack item);
    ItemStack getItemInMainHand();
    ItemStack getItemInOffHand();
    ItemStack getItemInHand();
    void setItemInHand(ItemStack item);
    int getHeldItemSlot();
    ItemStack getHelmet();
    ItemStack getChestplate();
    ItemStack getLeggings();
    ItemStack getBoots();
    void setHelmet(ItemStack item);
    void setChestplate(ItemStack item);
    void setLeggings(ItemStack item);
    void setBoots(ItemStack item);
    ItemStack[] getArmorContents();
    void setArmorContents(ItemStack[] items);
}
