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
    private final String type;
    private final String set;
    private final Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();

    public GearItem(Main plugin, String id, Material material, String name, String type, String set) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.material = material;
        this.name = name;
        this.type = type;
        this.set = set;
    }

    /** Добавить зачарование (уровни выше ванильных разрешены). */
    public GearItem enchant(Enchantment enchantment, int level) {
        this.enchantments.put(enchantment, level);
        return this;
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
            meta.setDisplayName(ColorUtil.color("&7« &f" + name + " &7»"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            lore.add(ColorUtil.color("&#F8BEFB&l┃ &fНабор: &#F8BEFB" + set));
            lore.add(ColorUtil.color("&#F8BEFB&l┃ &fТип: &#F8BEFB" + type));
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            lore.add(ColorUtil.color("&7● &fЗачарования уже вложены"));
            lore.add(ColorUtil.color("&7● &fРемонт не требуется"));
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
