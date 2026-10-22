package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обычный предмет с именем и зачарованиями: броня, мечи, кирки и булава.
 *
 * Раньше такие вещи лежали в ExecutableItems — здесь они свои, поэтому
 * магазин выдаёт их командой ElytrixItem, а не чужим плагином.
 */
public final class GearItem implements CustomItem {

    private final NamespacedKey key;
    private final String id;
    private final Material material;
    private final String name;
    private final Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
    private final List<String> enchantmentLore = new ArrayList<>();

    public GearItem(Main plugin, String id, Material material, String name) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.material = material;
        this.name = name;
    }

    /** Добавить зачарование (уровни выше ванильных разрешены). */
    public GearItem enchant(Enchantment enchantment, int level, String russianName) {
        this.enchantments.put(enchantment, level);
        this.enchantmentLore.add(level > 1 ? russianName + " " + roman(level) : russianName);
        return this;
    }

    private static String roman(int value) {
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (value >= 1 && value <= 10) {
            return ones[value];
        }
        return String.valueOf(value);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isStackable() {
        return false;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            for (String line : enchantmentLore) {
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &f" + line));
            }
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            meta.setLore(lore);

            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.hideAllFlags(item);
        return item;
    }
}
