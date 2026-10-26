package org.bukkit.inventory;
public interface EntityEquipment {
    ItemStack getItemInMainHand();
    void setItemInMainHand(ItemStack item);
    void setItemInMainHand(ItemStack item, boolean silent);
    ItemStack getItemInOffHand();
    void setItemInOffHand(ItemStack item);
    void setItemInOffHand(ItemStack item, boolean silent);
    ItemStack getHelmet();
    void setHelmet(ItemStack helmet);
    void setHelmet(ItemStack helmet, boolean silent);
    ItemStack getChestplate();
    void setChestplate(ItemStack chestplate);
    void setChestplate(ItemStack chestplate, boolean silent);
    ItemStack getLeggings();
    void setLeggings(ItemStack leggings);
    void setLeggings(ItemStack leggings, boolean silent);
    ItemStack getBoots();
    void setBoots(ItemStack boots);
    void setBoots(ItemStack boots, boolean silent);
    void setHelmetDropChance(float chance);
    void setChestplateDropChance(float chance);
    void setLeggingsDropChance(float chance);
    void setBootsDropChance(float chance);
    void setItemInMainHandDropChance(float chance);
    void setItemInOffHandDropChance(float chance);
    void clear();
}
