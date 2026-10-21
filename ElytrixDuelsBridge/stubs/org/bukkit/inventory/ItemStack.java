package org.bukkit.inventory;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack {
    public ItemStack(Material type) { }
    public ItemStack(Material type, int amount) { }
    public ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(ItemMeta meta) { return true; }
    public Material getType() { return null; }
    public int getAmount() { return 0; }
    public void setAmount(int amount) { }
    public void addUnsafeEnchantment(Enchantment enchantment, int level) { }
}
