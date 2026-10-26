package org.bukkit.inventory.meta;

import java.util.Map;
import org.bukkit.enchantments.Enchantment;

public interface EnchantmentStorageMeta extends ItemMeta {
    boolean hasStoredEnchants();
    boolean hasStoredEnchant(Enchantment enchant);
    int getStoredEnchantLevel(Enchantment enchant);
    Map<Enchantment, Integer> getStoredEnchants();
    boolean addStoredEnchant(Enchantment enchant, int level, boolean ignoreLevelRestriction);
    boolean removeStoredEnchant(Enchantment enchant);
}
