package ru.elytrix.duelsbridge;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Десять готовых наборов EpicDuels.
 *
 * Наборы собираются кодом, а не руками в игре: мост вызывает
 * KitManager#createKit(name, contents, armor, offHand) и сам сохраняет kits.yml.
 * Уже существующие киты не трогаются, если не передан overwrite = true.
 *
 * Правило подбора: набор должен работать без установки блоков — на аренах
 * ставить блоки запрещено, поэтому кристаллов и «обсидиановых» китов тут нет.
 */
final class Kits {

    static final String[] NAMES = {
            "sword", "uhc", "gapple", "sumo", "archer",
            "mace", "axe", "trident", "crossbow", "netherite"
    };

    static final String[] LABELS = {
            "Классика", "УХК", "Гаппли", "Сумо", "Лучник",
            "Булава", "Топор и щит", "Тризуб", "Арбалет", "Незерит"
    };

    private static final Material[] ICONS = {
            Material.NETHERITE_SWORD,
            Material.GOLDEN_APPLE,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.FISHING_ROD,
            Material.BOW,
            Material.MACE,
            Material.NETHERITE_AXE,
            Material.TRIDENT,
            Material.CROSSBOW,
            Material.NETHERITE_CHESTPLATE
    };

    private final ElytrixDuelsBridge plugin;

    Kits(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    /** @return сколько наборов создано; -1 — EpicDuels не отдал KitManager. */
    int install(boolean overwrite) {
        Plugin epic = Bukkit.getPluginManager().getPlugin("EpicDuels");
        if (epic == null) return -1;

        Object manager;
        try {
            manager = epic.getClass().getMethod("getKitManager").invoke(epic);
        } catch (Throwable error) {
            plugin.getLogger().warning("Наборы: EpicDuels не отдал KitManager: " + error);
            return -1;
        }
        if (manager == null) return -1;

        Method getKit;
        Method createKit;
        Method deleteKit;
        Method saveKits;
        try {
            Class<?> type = manager.getClass();
            getKit = type.getMethod("getKit", String.class);
            createKit = type.getMethod("createKit", String.class,
                    ItemStack[].class, ItemStack[].class, ItemStack.class);
            deleteKit = type.getMethod("deleteKit", String.class);
            saveKits = type.getMethod("saveKits");
        } catch (Throwable error) {
            plugin.getLogger().warning("Наборы: не совпал API KitManager: " + error);
            return -1;
        }

        int created = 0;
        for (int index = 0; index < NAMES.length; index++) {
            String name = NAMES[index];
            try {
                Object existing = getKit.invoke(manager, name);
                if (existing != null) {
                    if (!overwrite) continue;
                    deleteKit.invoke(manager, name);
                }
                Object kit = createKit.invoke(manager, name, contents(name), armor(name), offhand(name));
                if (kit == null) continue;
                try {
                    kit.getClass().getMethod("setIcon", Material.class).invoke(kit, ICONS[index]);
                } catch (Throwable ignored) {
                    // иконка нужна только родному меню EpicDuels — не критично
                }
                created++;
            } catch (Throwable error) {
                plugin.getLogger().warning("Набор " + name + ": " + error);
            }
        }
        if (created > 0) {
            try {
                saveKits.invoke(manager);
            } catch (Throwable error) {
                plugin.getLogger().warning("Наборы: не удалось сохранить kits.yml: " + error);
            }
        }
        return created;
    }

    // ------------------------------------------------------------------ вещи

    private static ItemStack item(Material type, int amount) {
        ItemStack stack = new ItemStack(type);
        if (amount > 1) stack.setAmount(amount);
        return stack;
    }

    private static ItemStack boost(ItemStack stack, String name, int level) {
        Enchantment enchantment = enchantment(name);
        if (enchantment != null) stack.addUnsafeEnchantment(enchantment, level);
        return stack;
    }

    /** Названия зачарований 1.21; если сервер старый — предмет просто без него. */
    private static Enchantment enchantment(String name) {
        try {
            return (Enchantment) Enchantment.class.getField(name).get(null);
        } catch (Throwable error) {
            return null;
        }
    }

    private static ItemStack[] armor(Material boots, Material leggings, Material chestplate,
                                     Material helmet, String protection, int level) {
        return new ItemStack[]{
                boost(item(boots, 1), protection, level),
                boost(item(leggings, 1), protection, level),
                boost(item(chestplate, 1), protection, level),
                boost(item(helmet, 1), protection, level)
        };
    }

    private static ItemStack[] contents(String name) {
        if (name.equals("sword")) {
            return new ItemStack[]{
                    boost(boost(item(Material.NETHERITE_SWORD, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                    item(Material.GOLDEN_APPLE, 3)};
        }
        if (name.equals("uhc")) {
            return new ItemStack[]{
                    boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 2),
                    boost(item(Material.BOW, 1), "POWER", 2),
                    item(Material.ARROW, 16),
                    item(Material.GOLDEN_APPLE, 3)};
        }
        if (name.equals("gapple")) {
            return new ItemStack[]{
                    boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 2),
                    item(Material.GOLDEN_APPLE, 8)};
        }
        if (name.equals("sumo")) {
            return new ItemStack[0];
        }
        if (name.equals("archer")) {
            return new ItemStack[]{
                    boost(boost(item(Material.BOW, 1), "POWER", 3), "PUNCH", 1),
                    item(Material.ARROW, 32),
                    boost(item(Material.STONE_SWORD, 1), "SHARPNESS", 1)};
        }
        if (name.equals("mace")) {
            return new ItemStack[]{
                    boost(boost(item(Material.MACE, 1), "DENSITY", 3), "UNBREAKING", 2),
                    item(Material.WIND_CHARGE, 8),
                    item(Material.GOLDEN_APPLE, 2)};
        }
        if (name.equals("axe")) {
            return new ItemStack[]{
                    boost(item(Material.NETHERITE_AXE, 1), "SHARPNESS", 2),
                    item(Material.GOLDEN_APPLE, 2)};
        }
        if (name.equals("trident")) {
            return new ItemStack[]{
                    boost(boost(item(Material.TRIDENT, 1), "IMPALING", 3), "UNBREAKING", 2),
                    item(Material.GOLDEN_APPLE, 2)};
        }
        if (name.equals("crossbow")) {
            return new ItemStack[]{
                    boost(boost(item(Material.CROSSBOW, 1), "QUICK_CHARGE", 2), "MULTISHOT", 1),
                    item(Material.ARROW, 32),
                    boost(item(Material.STONE_SWORD, 1), "SHARPNESS", 1)};
        }
        return new ItemStack[]{
                boost(boost(item(Material.NETHERITE_SWORD, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                item(Material.GOLDEN_APPLE, 4)};
    }

    private static ItemStack[] armor(String name) {
        if (name.equals("sumo")) {
            return new ItemStack[0];
        }
        if (name.equals("gapple")) {
            return armor(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                    Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, "PROTECTION", 2);
        }
        if (name.equals("archer")) {
            return armor(Material.LEATHER_BOOTS, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_HELMET, "PROTECTION", 1);
        }
        if (name.equals("crossbow")) {
            return armor(Material.CHAINMAIL_BOOTS, Material.CHAINMAIL_LEGGINGS,
                    Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_HELMET, "PROTECTION", 1);
        }
        if (name.equals("netherite")) {
            return armor(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
                    Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, "PROTECTION", 2);
        }
        return armor(Material.IRON_BOOTS, Material.IRON_LEGGINGS,
                Material.IRON_CHESTPLATE, Material.IRON_HELMET, "PROTECTION", 1);
    }

    private static ItemStack offhand(String name) {
        if (name.equals("axe")) return item(Material.SHIELD, 1);
        return null;
    }
}
