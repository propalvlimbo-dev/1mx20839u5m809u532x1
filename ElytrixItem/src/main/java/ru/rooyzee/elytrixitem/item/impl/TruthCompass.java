package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;

import java.util.Arrays;

/** Reusable artifact that points to a random live dungeon. */
public final class TruthCompass implements CustomItem {

    public static final String ID = "truth_compass";

    private final NamespacedKey itemIdKey;

    public TruthCompass(Main plugin) {
        this.itemIdKey = new NamespacedKey(plugin, "custom_item_id");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isStackable() {
        return false;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&7« &#F8BEFBКомпас Правды &7»"));
            meta.setLore(Arrays.asList(
                    ColorUtil.color("&#F8BEFB&l┃"),
                    ColorUtil.color("&#F8BEFB&l┃ &fПоказывает координаты случайного данжа"),
                    ColorUtil.color("&#F8BEFB&l┃ &fОтслеживание: &#F8BEFB2 минуты"),
                    ColorUtil.color("&#F8BEFB&l┃ &fПерезарядка: &#F8BEFB2 часа"),
                    ColorUtil.color("&#F8BEFB&l┃"),
                    ColorUtil.color("&7● &fНажмите ПКМ, чтобы активировать")
            ));
            meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, ID);
            item.setItemMeta(meta);
        }
        return item;
    }
}
