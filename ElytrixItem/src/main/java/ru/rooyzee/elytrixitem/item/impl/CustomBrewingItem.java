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

import java.util.ArrayList;
import java.util.List;

/**
 * Зельеварка из ShadowFurnaces: при установке топливо (огненный порошок)
 * тратится в несколько раз медленнее.
 */
public final class CustomBrewingItem implements CustomItem {

    private final NamespacedKey key;
    private final String id;
    private final String name;
    private final String description;
    private final int fuelPower;
    private final boolean glow;

    public CustomBrewingItem(Main plugin, String id, String name, String description,
                             int fuelPower, boolean glow) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.name = name;
        this.description = description;
        this.fuelPower = fuelPower;
        this.glow = glow;
    }

    @Override
    public String getId() {
        return id;
    }

    /** Имя блока в интерфейсе зельеварки. */
    public String getDisplayName() {
        return ColorUtil.gradient("« " + name + " »");
    }

    /** Во сколько раз дольше хватает одного огненного порошка. */
    public int getFuelPower() {
        return fuelPower;
    }

    @Override
    public boolean isStackable() {
        return false;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BREWING_STAND);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            lore.add(ColorUtil.color("&#F8BEFB&l┃ &f" + description));
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            meta.setLore(lore);

            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.showAllDetails(item);
        return item;
    }
}
