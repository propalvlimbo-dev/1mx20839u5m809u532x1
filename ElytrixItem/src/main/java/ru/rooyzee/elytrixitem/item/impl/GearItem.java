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

import java.util.LinkedHashMap;
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

    public GearItem(Main plugin, String id, Material material, String name) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.material = material;
        this.name = name;
    }

    /** Add a real vanilla enchantment; levels above the vanilla maximum are allowed. */
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

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);

        // Verify the metadata survived ItemMeta round-tripping, and force-add only if a server rejects it.
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (item.getEnchantmentLevel(entry.getKey()) != entry.getValue()) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
        }

        ItemStackUtil.showAllDetails(item);
        return item;
    }
}
