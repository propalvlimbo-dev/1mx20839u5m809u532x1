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
 * Печка из ShadowFurnaces: при установке становится обычной печкой,
 * но плавит быстрее и/или выдаёт больше предметов.
 */
public final class CustomFurnaceItem implements CustomItem {

    private final NamespacedKey key;
    private final String id;
    private final String name;
    private final String description;
    private final int cookSpeed;
    private final int resultMultiplier;
    private final int fuelEconomy;
    private final boolean glow;

    public CustomFurnaceItem(Main plugin, String id, String name, String description,
                             int cookSpeed, int resultMultiplier, int fuelEconomy, boolean glow) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.name = name;
        this.description = description;
        this.cookSpeed = cookSpeed;
        this.resultMultiplier = resultMultiplier;
        this.fuelEconomy = fuelEconomy;
        this.glow = glow;
    }

    @Override
    public String getId() {
        return id;
    }

    /** Имя блока в интерфейсе печки. */
    public String getDisplayName() {
        return ColorUtil.gradient("« " + name + " »");
    }

    /** Во сколько раз быстрее плавит (1 — как обычная печка). */
    public int getCookSpeed() {
        return cookSpeed;
    }

    /** Во сколько раз больше предметов выдаёт за одну плавку. */
    public int getResultMultiplier() {
        return resultMultiplier;
    }

    /** Во сколько раз дольше горит топливо. */
    public int getFuelEconomy() {
        return fuelEconomy;
    }

    @Override
    public boolean isStackable() {
        return false;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.FURNACE);
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

        ItemStackUtil.hideAllFlags(item);
        return item;
    }
}
