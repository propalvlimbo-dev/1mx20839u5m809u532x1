package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.OffhandBonusItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Тотем: работает только в левой руке и даёт бонусы к характеристикам.
 *
 * Сами бонусы ставит OffhandBonusListener, поэтому при снятии тотема
 * здоровье пересчитывается правильно.
 */
public final class TotemItem implements OffhandBonusItem {

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
    public List<Stat> getStats() {
        return stats;
    }

    @Override
    public List<Effect> getEffects(ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public boolean isStackable() {
        return false;
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
                if (stat.getAmount() < 0) {
                    lore.add(ColorUtil.color("&#F8BEFB&l┃ &fШтраф: &cминус " + stat.getLabel()));
                } else {
                    lore.add(ColorUtil.color("&#F8BEFB&l┃ &fБонус: &#F8BEFB+" + stat.getLabel()));
                }
            }
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            meta.setLore(lore);

            // Свечение как у ExecutableItems, без лишнего зачарования.
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.showAllDetails(item);
        return item;
    }
}
