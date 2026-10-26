package my.reqqpe.rseller.configs.impl;

import my.reqqpe.rseller.Main;
import my.reqqpe.rseller.configs.CustomConfig;
import my.reqqpe.rseller.models.SearchItemSettings;
import my.reqqpe.rseller.models.item.Item;
import my.reqqpe.rseller.models.item.ItemData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

import java.util.*;

public class ItemsConfig extends CustomConfig {

    public ItemsConfig(Main plugin) {
        super(plugin, "items.yml");
    }

    private final List<Item> items = new ArrayList<>();
    private SearchItemSettings searchSettings;

    @Override
    protected void load() {

        items.clear();

        searchSettings = loadSettings();

        loadItems();
    }

    private SearchItemSettings loadSettings() {
        var sec = config.getConfigurationSection("settings-check");
        if (sec == null) return new SearchItemSettings(true, true, true, true, false);

        return new SearchItemSettings(
                sec.getBoolean("display-name", true),
                sec.getBoolean("lore", true),
                sec.getBoolean("enchants", true),
                sec.getBoolean("model-data", true),
                sec.getBoolean("nbt-tags", false)
        );
    }

    private void loadItems() {

        var itemsSec = config.getConfigurationSection("items");
        var customSec = config.getConfigurationSection("custom-items");

        if (itemsSec == null) return;

        for (String id : itemsSec.getKeys(false)) {

            double price = itemsSec.getDouble(id + ".price");
            double points = itemsSec.getDouble(id + ".points");

            Material material = resolveMaterial(id);
            ItemData data = null;

            if (material == null && customSec != null) {

                var c = customSec.getConfigurationSection(id);
                if (c == null) continue;

                material = resolveMaterial(c.getString("material", ""));
                if (material == null) continue;

                var dataSec = c.getConfigurationSection("item-data");
                if (dataSec != null) {
                    data = loadItemData(dataSec);
                }
            }

            if (material == null) continue;

            items.add(new Item(id, material, data, price, points));
        }
    }

    private Material resolveMaterial(String id) {
        if (id == null || id.isBlank()) return null;

        Material material = Material.getMaterial(id);
        if (material != null) return material;

        // Bukkit renamed SCUTE to TURTLE_SCUTE after 1.16.5.
        if ("SCUTE".equalsIgnoreCase(id)) return Material.getMaterial("TURTLE_SCUTE");
        if ("TURTLE_SCUTE".equalsIgnoreCase(id)) return Material.getMaterial("SCUTE");
        return null;
    }

    private ItemData loadItemData(ConfigurationSection sec) {

        String name = sec.getString("name");
        List<String> lore = sec.getStringList("lore");
        Integer model = null;

        if (sec.contains("model-data")) {
            model = sec.getInt("model-data");
        }

        Map<Enchantment, Integer> ench = new HashMap<>();
        var enchSec = sec.getConfigurationSection("enchantments");

        if (enchSec != null) {
            for (String k : enchSec.getKeys(false)) {
                Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(k));
                if (e != null) ench.put(e, enchSec.getInt(k));
            }
        }

        return new ItemData(name, lore, model, ench, new HashMap<>());
    }

    // GETTERS
    public List<Item> getItems() {
        return items;
    }

    public SearchItemSettings getSearchSettings() {
        return searchSettings;
    }
}