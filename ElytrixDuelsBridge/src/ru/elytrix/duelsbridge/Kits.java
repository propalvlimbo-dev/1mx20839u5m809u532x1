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

    /**
     * Имена наборов, которые видит игрок (они же id в kits.yml EpicDuels).
     * Русские, потому что EpicDuels показывает имя набора как есть —
     * и в меню, и в сообщениях, и на скорборде.
     */
    /** Версия состава: при изменении киты пересоздаются сами (см. config kits.revision). */
    static final int REVISION = 2;

    static final String[] NAMES = {
            "Классика", "УХК", "Гаппли", "Сумо", "Лучник",
            "Булава", "Топор", "Тризуб", "Арбалет", "Незерит"
    };

    /** Прежние английские id — для перехода и для старых меню/команд. */
    static final String[] LEGACY = {
            "sword", "uhc", "gapple", "sumo", "archer",
            "mace", "axe", "trident", "crossbow", "netherite"
    };

    /** Подписи в меню: совпадают с именами. */
    static final String[] LABELS = NAMES;

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
            String legacy = LEGACY[index];
            try {
                Object existing = getKit.invoke(manager, name);
                Object old = getKit.invoke(manager, legacy);
                if (existing == null) {
                    if (old != null && !overwrite) {
                        // переносим прежний набор под русское имя, вещи не теряются
                        create(manager, createKit, name, old, ICONS[index], true);
                    } else {
                        createKit.invoke(manager, name, contents(legacy), armor(legacy), offhand(legacy));
                        setIcon(manager, getKit, name, ICONS[index]);
                    }
                    created++;
                } else if (overwrite) {
                    deleteKit.invoke(manager, name);
                    createKit.invoke(manager, name, contents(legacy), armor(legacy), offhand(legacy));
                    setIcon(manager, getKit, name, ICONS[index]);
                    created++;
                }
                // английский дубль больше не нужен
                if (getKit.invoke(manager, legacy) != null && getKit.invoke(manager, name) != null) {
                    deleteKit.invoke(manager, legacy);
                }
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

    /** Копия старого набора под новым именем. */
    private void create(Object manager, Method createKit, String name, Object old,
                        Material icon, boolean copy) throws Exception {
        ItemStack[] contents = copy ? (ItemStack[]) Duels.call(old, "getContents") : null;
        ItemStack[] armor = copy ? (ItemStack[]) Duels.call(old, "getArmorContents") : null;
        ItemStack offHand = copy ? (ItemStack) Duels.call(old, "getOffHand") : null;
        if (contents == null) contents = new ItemStack[0];
        createKit.invoke(manager, name, contents, armor, offHand);
        Object created = Duels.call(manager, "getKit", name);
        if (created != null) {
            Object oldIcon = Duels.call(old, "getIcon");
            Material use = oldIcon instanceof Material ? (Material) oldIcon : icon;
            try {
                created.getClass().getMethod("setIcon", Material.class).invoke(created, use);
            } catch (Throwable ignored) {
                // иконка нужна только родному меню EpicDuels — не критично
            }
        }
    }

    private void setIcon(Object manager, Method getKit, String name, Material icon) {
        try {
            Object kit = getKit.invoke(manager, name);
            if (kit == null) return;
            kit.getClass().getMethod("setIcon", Material.class).invoke(kit, icon);
        } catch (Throwable ignored) {
            // иконка нужна только родному меню EpicDuels — не критично
        }
    }

    /** Английский id или подпись → настоящее имя набора. */
    static String canonical(String name) {
        if (name == null) return null;
        String clean = name.trim();
        for (int index = 0; index < NAMES.length; index++) {
            if (clean.equalsIgnoreCase(LEGACY[index])) return NAMES[index];
            if (clean.equalsIgnoreCase(NAMES[index])) return NAMES[index];
        }
        return clean;
    }

    /** Русское имя → английский id EpicDuels (для старых сборок). */
    static String legacyOf(String name) {
        String canonical = canonical(name);
        if (canonical == null) return null;
        for (int index = 0; index < NAMES.length; index++) {
            if (canonical.equalsIgnoreCase(NAMES[index])) return LEGACY[index];
        }
        return null;
    }

    static boolean known(String name) {
        String canonical = canonical(name);
        if (canonical == null) return false;
        for (String value : NAMES) {
            if (value.equalsIgnoreCase(canonical)) return true;
        }
        return false;
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

    private static ItemStack[] suit(Material boots, Material leggings, Material chestplate,
                                    Material helmet, String protection, int level) {
        return new ItemStack[]{
                boost(item(boots, 1), protection, level),
                boost(item(leggings, 1), protection, level),
                boost(item(chestplate, 1), protection, level),
                boost(item(helmet, 1), protection, level)
        };
    }

    /**
     * Состав наборов 2.0: 3–5 предметов вместо трёх стаков.
     * Каждый кит решает одну задачу, поэтому в руках нет ничего «на всякий случай».
     */
    private static ItemStack[] contents(String name) {
        if (name.equals("sword")) {
            // Классика: чистый бой на мечах — меч, яблоки, пёрлы
            return new ItemStack[]{
                    boost(boost(item(Material.NETHERITE_SWORD, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                    item(Material.GOLDEN_APPLE, 6),
                    item(Material.ENDER_PEARL, 3)};
        }
        if (name.equals("uhc")) {
            // УХК: меч + лук, как в выживании
            return new ItemStack[]{
                    boost(boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                    boost(item(Material.BOW, 1), "POWER", 3),
                    item(Material.ARROW, 12),
                    item(Material.GOLDEN_APPLE, 3),
                    item(Material.ENDER_PEARL, 3)};
        }
        if (name.equals("gapple")) {
            // Гаппли: один стак яблок решает бой
            return new ItemStack[]{
                    boost(boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                    item(Material.GOLDEN_APPLE, 24),
                    item(Material.ENDER_PEARL, 3)};
        }
        if (name.equals("sumo")) {
            // Сумо: без брони, только отбрасывание
            return new ItemStack[]{
                    boost(item(Material.STONE_SWORD, 1), "KNOCKBACK", 2),
                    item(Material.ENDER_PEARL, 2)};
        }
        if (name.equals("archer")) {
            // Лучник: держит дистанцию, вблизи слабый меч
            return new ItemStack[]{
                    boost(boost(item(Material.BOW, 1), "POWER", 4), "PUNCH", 1),
                    item(Material.ARROW, 16),
                    boost(item(Material.STONE_SWORD, 1), "SHARPNESS", 2),
                    item(Material.ENDER_PEARL, 4)};
        }
        if (name.equals("mace")) {
            // Булава: прыжок сверху и ветряные заряды
            return new ItemStack[]{
                    boost(boost(item(Material.MACE, 1), "DENSITY", 4), "UNBREAKING", 3),
                    item(Material.WIND_CHARGE, 8),
                    item(Material.GOLDEN_APPLE, 3),
                    item(Material.ENDER_PEARL, 3)};
        }
        if (name.equals("axe")) {
            // Топор: щит в левой руке и пробивающий удар
            return new ItemStack[]{
                    boost(boost(item(Material.NETHERITE_AXE, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                    item(Material.GOLDEN_APPLE, 4),
                    item(Material.ENDER_PEARL, 3)};
        }
        if (name.equals("trident")) {
            // Тризуб: дальний удар и возврат копья
            return new ItemStack[]{
                    boost(boost(boost(item(Material.TRIDENT, 1), "IMPALING", 5), "LOYALTY", 3),
                            "UNBREAKING", 3),
                    item(Material.ENDER_PEARL, 4),
                    item(Material.GOLDEN_APPLE, 3)};
        }
        if (name.equals("crossbow")) {
            // Арбалет: три болта за выстрел на быстрой перезарядке
            return new ItemStack[]{
                    boost(boost(boost(item(Material.CROSSBOW, 1), "QUICK_CHARGE", 3), "MULTISHOT", 1),
                            "UNBREAKING", 3),
                    item(Material.ARROW, 12),
                    boost(item(Material.STONE_SWORD, 1), "SHARPNESS", 2),
                    item(Material.GOLDEN_APPLE, 2)};
        }
        // Незерит: танк — сет, тотем и яблоки
        return new ItemStack[]{
                boost(boost(item(Material.NETHERITE_SWORD, 1), "SHARPNESS", 3), "UNBREAKING", 3),
                item(Material.GOLDEN_APPLE, 4),
                item(Material.ENDER_PEARL, 2)};
    }

    private static ItemStack[] armor(String name) {
        if (name.equals("sumo")) {
            return new ItemStack[0];
        }
        if (name.equals("gapple") || name.equals("axe")) {
            return suit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                    Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, "PROTECTION", 2);
        }
        if (name.equals("netherite")) {
            return suit(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
                    Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, "PROTECTION", 3);
        }
        if (name.equals("archer")) {
            return suit(Material.LEATHER_BOOTS, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_HELMET, "PROTECTION", 2);
        }
        if (name.equals("crossbow")) {
            return suit(Material.CHAINMAIL_BOOTS, Material.CHAINMAIL_LEGGINGS,
                    Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_HELMET, "PROTECTION", 2);
        }
        if (name.equals("mace")) {
            // падать сверху безопасно: ботинки с перьевым падением
            return boot(suit(Material.IRON_BOOTS, Material.IRON_LEGGINGS,
                    Material.IRON_CHESTPLATE, Material.IRON_HELMET, "PROTECTION", 2),
                    "FEATHER_FALLING", 4);
        }
        if (name.equals("uhc")) {
            return boot(suit(Material.IRON_BOOTS, Material.IRON_LEGGINGS,
                    Material.IRON_CHESTPLATE, Material.IRON_HELMET, "PROTECTION", 2),
                    "FEATHER_FALLING", 3);
        }
        if (name.equals("trident")) {
            return boot(suit(Material.IRON_BOOTS, Material.IRON_LEGGINGS,
                    Material.IRON_CHESTPLATE, Material.IRON_HELMET, "PROTECTION", 2),
                    "DEPTH_STRIDER", 3);
        }
        return suit(Material.IRON_BOOTS, Material.IRON_LEGGINGS,
                Material.IRON_CHESTPLATE, Material.IRON_HELMET, "PROTECTION", 2);
    }

    private static ItemStack[] boot(ItemStack[] suit, String enchant, int level) {
        suit[0] = boost(suit[0], enchant, level);
        return suit;
    }

    private static ItemStack offhand(String name) {
        if (name.equals("axe")) return item(Material.SHIELD, 1);
        if (name.equals("netherite")) return item(Material.TOTEM_OF_UNDYING, 1);
        return null;
    }
}
