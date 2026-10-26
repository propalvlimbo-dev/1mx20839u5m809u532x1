package org.bukkit.inventory;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
    public boolean hasItemMeta() { return false; }
    public boolean isSimilar(ItemStack other) { return false; }
    public int getMaxStackSize() { return 64; }
    public java.util.Map<Enchantment, Integer> getEnchantments() { return new java.util.LinkedHashMap<>(); }
    public int getEnchantmentLevel(Enchantment enchantment) { return 0; }
    public void addEnchantment(Enchantment enchantment, int level) { }
    public void setType(Material material) { }
    public short getDurability() { return 0; }
    public void setDurability(short durability) { }
    public boolean containsEnchantment(Enchantment enchantment) { return false; }
    public org.bukkit.inventory.meta.ItemMeta getItemMeta(boolean copy) { return null; }

    public ItemStack(Material type) { }
    public ItemStack(Material type, int amount) { }
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
    public Material getType() { return null; }
    public int getAmount() { return 0; }
    public void setAmount(int amount) { }
    public void addUnsafeEnchantment(Enchantment enchantment, int level) { }
    @Override
    public ItemStack clone() { return this; }
}
