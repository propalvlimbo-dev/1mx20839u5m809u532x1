package org.bukkit.inventory.meta;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataContainer;

public interface ItemMeta {
    void setDisplayName(String name);
    String getDisplayName();
    boolean hasDisplayName();
    void setLore(List<String> lore);
    List<String> getLore();
    boolean hasLore();
    Map<Enchantment, Integer> getEnchants();
    boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction);
    boolean removeEnchant(Enchantment enchantment);
    boolean hasEnchant(Enchantment enchantment);
    int getEnchantLevel(Enchantment enchantment);
    boolean hasEnchants();
    PersistentDataContainer getPersistentDataContainer();
    void addItemFlags(ItemFlag... itemFlags);
    void removeItemFlags(ItemFlag... itemFlags);
    Set<ItemFlag> getItemFlags();
    boolean hasItemFlag(ItemFlag flag);
    boolean isUnbreakable();
    void setUnbreakable(boolean unbreakable);
    void setCustomModelData(Integer data);
    Integer getCustomModelData();
    boolean addAttributeModifier(Attribute attribute, AttributeModifier modifier);
    Set<AttributeModifier> getAttributeModifiers(Attribute attribute);
}
