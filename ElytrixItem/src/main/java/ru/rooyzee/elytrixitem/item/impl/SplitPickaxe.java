package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Кирка Раскола — железная кирка без зачарований.
 *
 * Единственное, что умеет: один раз добыть спавнер, после чего сразу ломается.
 * Обычные кирки спавнер не добывают.
 */
public final class SplitPickaxe implements CustomItem {

    public static final String ID = "kirkaraskola";

    private final NamespacedKey key;

    public SplitPickaxe(Main plugin) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtil.gradient("« Кирка Раскола »"));

            List<String> lore = Arrays.asList(
                    ColorUtil.color("&#F8BEFB&l┃ "),
                    ColorUtil.color("&#F8BEFB&l┃ &fТип: &#F8BEFBУникальный инструмент"),
                    ColorUtil.color("&#F8BEFB&l┃ &fДобывает: &#F8BEFBспавнер"),
                    ColorUtil.color("&#F8BEFB&l┃ &fИспользований: &#F8BEFB1"),
                    ColorUtil.color("&#F8BEFB&l┃ &fЗачарования: &#F8BEFBнет"),
                    ColorUtil.color("&#F8BEFB&l┃ "),
                    ColorUtil.color("&7● &fМоб в спавнер &cне возвращается"),
                    ColorUtil.color("&7● &fПосле добычи кирка ломается")
            );

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, ID);
            item.setItemMeta(meta);
        }

        ItemStackUtil.hideAllFlags(item);
        return item;
    }
}
