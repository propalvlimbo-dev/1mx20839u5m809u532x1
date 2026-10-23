package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Тотем: работает только в левой руке и даёт бонусы к характеристикам.
 *
 * Раньше это были атрибуты ExecutableItems — теперь модификаторы ставятся
 * прямо на предмет, поэтому тотем работает и без чужого плагина.
 */
public final class TotemItem implements CustomItem {

    private final NamespacedKey key;
    private final String id;
    private final String name;
    private final List<Stat> stats = new ArrayList<>();

    public TotemItem(Main plugin, String id, String name) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.name = name;
    }

    /** Бонус или штраф к характеристике; отрицательное значение выводится как штраф. */
    public TotemItem stat(Attribute attribute, double amount, String label) {
        this.stats.add(new Stat(attribute, amount, label));
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            lore.add(ColorUtil.color("&#F8BEFB&l┃ &fУсловие: &#F8BEFBВ левой руке"));
            for (Stat stat : stats) {
                if (stat.amount < 0) {
                    lore.add(ColorUtil.color("&#F8BEFB&l┃ &fШтраф: &cминус " + stat.label));
                } else {
                    lore.add(ColorUtil.color("&#F8BEFB&l┃ &fБонус: &#F8BEFB+" + stat.label));
                }
            }
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            meta.setLore(lore);

            for (int i = 0; i < stats.size(); i++) {
                Stat stat = stats.get(i);
                NamespacedKey modifierKey = new NamespacedKey(key.getNamespace(), id + "_" + i);
                AttributeModifier modifier = new AttributeModifier(modifierKey, stat.amount,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.OFFHAND);
                meta.addAttributeModifier(stat.attribute, modifier);
            }

            // Свечение как у ExecutableItems, без лишнего зачарования.
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.hideAllFlags(item);
        return item;
    }

    private static String number(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static final class Stat {

        private final Attribute attribute;
        private final double amount;
        private final String label;

        private Stat(Attribute attribute, double amount, String label) {
            this.attribute = attribute;
            this.amount = amount;
            this.label = number(Math.abs(amount)) + " " + label;
        }
    }
}
