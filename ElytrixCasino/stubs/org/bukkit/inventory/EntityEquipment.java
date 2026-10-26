package org.bukkit.inventory;
public interface EntityEquipment {
    ItemStack getItemInMainHand();
    void setItemInMainHand(ItemStack item);
    ItemStack getItemInOffHand();
    void setItemInOffHand(ItemStack item);
    ItemStack getHelmet();
    ItemStack getChestplate();
    ItemStack getLeggings();
    ItemStack getBoots();
    void setHelmet(ItemStack item);
    void setChestplate(ItemStack item);
    void setLeggings(ItemStack item);
    void setBoots(ItemStack item);
}
