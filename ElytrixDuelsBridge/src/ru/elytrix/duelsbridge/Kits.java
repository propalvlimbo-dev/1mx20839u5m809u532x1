package ru.elytrix.duelsbridge;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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
    static final int REVISION = 3;

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

    /** Питьевое зелье с одним эффектом: strength II = 90 секунд, уровень 2. */
    private static ItemStack potion(Material type, int amount,
                                    org.bukkit.potion.PotionEffectType effect, int seconds, int level) {
        ItemStack stack = item(type, amount);
        try {
            org.bukkit.inventory.meta.ItemMeta raw = stack.getItemMeta();
            if (!(raw instanceof org.bukkit.inventory.meta.PotionMeta)) return stack;
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) raw;
            meta.addCustomEffect(new org.bukkit.potion.PotionEffect(
                    effect, Math.max(20, seconds * 20), Math.max(0, level - 1)), true);
            stack.setItemMeta(meta);
        } catch (Throwable ignored) {
            // зелье без эффекта — не повод не выдать кит
        }
        return stack;
    }

    private static ItemStack drink(int amount, org.bukkit.potion.PotionEffectType effect,
                                   int seconds, int level) {
        return potion(Material.POTION, amount, effect, seconds, level);
    }

    /**
     * Состав наборов 3.0: полные комплекты топ-уровня — незерит и алмаз
     * с защитой 3–4, острота 4–5, тотемы, зелья и лук/арбалет почти везде.
     * Сервер с лёгким развитием: в железе тут никто не воюет.
     */
    private static ItemStack[] contents(String name) {
        if (name.equals("sword")) {
            return new ItemStack[]{
                    boost(boost(item(Material.NETHERITE_SWORD, 1), "SHARPNESS", 5), "UNBREAKING", 3),
                    boost(item(Material.BOW, 1), "POWER", 3),
                    item(Material.ARROW, 16),
                    item(Material.GOLDEN_APPLE, 8),
                    item(Material.ENDER_PEARL, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(2, org.bukkit.potion.PotionEffectType.STRENGTH, 90, 2),
                    drink(2, org.bukkit.potion.PotionEffectType.SPEED, 120, 2)};
        }
        if (name.equals("uhc")) {
            return new ItemStack[]{
                    boost(boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 4), "UNBREAKING", 3),
                    boost(boost(item(Material.BOW, 1), "POWER", 4), "PUNCH", 1),
                    boost(item(Material.CROSSBOW, 1), "QUICK_CHARGE", 2),
                    item(Material.ARROW, 32),
                    item(Material.GOLDEN_APPLE, 6),
                    item(Material.ENDER_PEARL, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(1, org.bukkit.potion.PotionEffectType.REGENERATION, 45, 2)};
        }
        if (name.equals("gapple")) {
            return new ItemStack[]{
                    boost(boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 4), "UNBREAKING", 3),
                    boost(boost(item(Material.CROSSBOW, 1), "QUICK_CHARGE", 3), "MULTISHOT", 1),
                    item(Material.ARROW, 16),
                    item(Material.GOLDEN_APPLE, 32),
                    item(Material.ENDER_PEARL, 6),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(2, org.bukkit.potion.PotionEffectType.STRENGTH, 90, 2)};
        }
        if (name.equals("sumo")) {
            // без брони, один только отбрасывающий удар
            return new ItemStack[]{
                    boost(item(Material.NETHERITE_SWORD, 1), "KNOCKBACK", 2),
                    item(Material.ENDER_PEARL, 4),
                    drink(2, org.bukkit.potion.PotionEffectType.SPEED, 120, 2),
                    drink(2, org.bukkit.potion.PotionEffectType.JUMP_BOOST, 120, 2)};
        }
        if (name.equals("archer")) {
            return new ItemStack[]{
                    boost(boost(boost(item(Material.BOW, 1), "POWER", 5), "PUNCH", 2), "FLAME", 1),
                    item(Material.ARROW, 48),
                    boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 3),
                    item(Material.ENDER_PEARL, 6),
                    item(Material.GOLDEN_APPLE, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(2, org.bukkit.potion.PotionEffectType.SPEED, 120, 2)};
        }
        if (name.equals("mace")) {
            return new ItemStack[]{
                    boost(boost(item(Material.MACE, 1), "DENSITY", 5), "UNBREAKING", 3),
                    item(Material.WIND_CHARGE, 16),
                    boost(item(Material.BOW, 1), "POWER", 3),
                    item(Material.ARROW, 16),
                    item(Material.GOLDEN_APPLE, 6),
                    item(Material.ENDER_PEARL, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(1, org.bukkit.potion.PotionEffectType.STRENGTH, 90, 2)};
        }
        if (name.equals("axe")) {
            return new ItemStack[]{
                    boost(boost(item(Material.NETHERITE_AXE, 1), "SHARPNESS", 5), "UNBREAKING", 3),
                    boost(item(Material.CROSSBOW, 1), "QUICK_CHARGE", 3),
                    item(Material.ARROW, 16),
                    item(Material.GOLDEN_APPLE, 6),
                    item(Material.ENDER_PEARL, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(1, org.bukkit.potion.PotionEffectType.SPEED, 120, 2)};
        }
        if (name.equals("trident")) {
            return new ItemStack[]{
                    boost(boost(boost(item(Material.TRIDENT, 1), "IMPALING", 5), "LOYALTY", 3),
                            "UNBREAKING", 3),
                    boost(item(Material.BOW, 1), "POWER", 3),
                    item(Material.ARROW, 16),
                    item(Material.ENDER_PEARL, 6),
                    item(Material.GOLDEN_APPLE, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(2, org.bukkit.potion.PotionEffectType.SPEED, 120, 2)};
        }
        if (name.equals("crossbow")) {
            return new ItemStack[]{
                    boost(boost(boost(item(Material.CROSSBOW, 1), "QUICK_CHARGE", 3), "MULTISHOT", 1),
                            "PIERCING", 4),
                    item(Material.ARROW, 32),
                    boost(item(Material.DIAMOND_SWORD, 1), "SHARPNESS", 3),
                    item(Material.ENDER_PEARL, 6),
                    item(Material.GOLDEN_APPLE, 4),
                    item(Material.TOTEM_OF_UNDYING, 1),
                    drink(2, org.bukkit.potion.PotionEffectType.SPEED, 120, 2)};
        }
        // Незерит: танк
        return new ItemStack[]{
                boost(boost(item(Material.NETHERITE_SWORD, 1), "SHARPNESS", 4), "UNBREAKING", 3),
                boost(item(Material.BOW, 1), "POWER", 3),
                item(Material.ARROW, 16),
                item(Material.GOLDEN_APPLE, 8),
                item(Material.ENDER_PEARL, 4),
                item(Material.TOTEM_OF_UNDYING, 1),
                drink(2, org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 180, 1),
                drink(1, org.bukkit.potion.PotionEffectType.REGENERATION, 45, 2)};
    }

    private static ItemStack[] armor(String name) {
        if (name.equals("sumo")) {
            return new ItemStack[0];
        }
        if (name.equals("netherite")) {
            return suit(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
                    Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, "PROTECTION", 4);
        }
        if (name.equals("sword") || name.equals("axe") || name.equals("mace")) {
            return boot(suit(Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS,
                    Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, "PROTECTION", 3),
                    "FEATHER_FALLING", name.equals("mace") ? 4 : 3);
        }
        if (name.equals("archer")) {
            return suit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                    Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, "PROTECTION", 3);
        }
        if (name.equals("trident")) {
            return boot(suit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                    Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, "PROTECTION", 4),
                    "DEPTH_STRIDER", 3);
        }
        if (name.equals("uhc")) {
            return boot(suit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                    Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, "PROTECTION", 4),
                    "FEATHER_FALLING", 4);
        }
        return suit(Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, "PROTECTION", 4);
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

    // ----------------------------------------------------------- редактор китов

    /** Все киты сервера списком: имя и сколько предметов в наборе. */
    void list(Player player) {
        Object manager = plugin.duels().manager("getKitManager");
        if (manager == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cEpicDuels не отдал KitManager.");
            return;
        }
        Object names = Duels.call(manager, "getKitNames");
        if (!(names instanceof java.util.List) || ((java.util.List<?>) names).isEmpty()) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cКитов нет: /duelx kits reset");
            return;
        }
        plugin.msg(player, "&#F8BEFBКиты &8» &fвсего &d" + ((java.util.List<?>) names).size());
        for (Object row : (java.util.List<?>) names) {
            if (row == null) continue;
            String name = String.valueOf(row);
            Object kit = Duels.call(manager, "getKit", name);
            ItemStack[] contents = kit == null ? null : (ItemStack[]) Duels.call(kit, "getContents");
            int count = 0;
            if (contents != null) {
                for (ItemStack stack : contents) {
                    if (stack != null) count++;
                }
            }
            plugin.msg(player, "&7● &f" + name + " &7— предметов: &f" + count);
        }
        plugin.msg(player, "&7ред.: &f/duelx kit edit <кит> &7· сохранить из инвентаря: &f/duelx kit save <кит>");
    }

    /** Полноценный редактор EpicDuels: перетащить вещи и нажать «Save Kit». */
    void edit(Player player, String name) {
        Object kit = kitOf(name);
        if (kit == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cКита &f" + name + "&c нет.");
            return;
        }
        if (!openedBoolean(plugin.duels().gui(), player, kit)) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cРедактор EpicDuels недоступен: обнови EpicDuels-ElyTriX.");
        }
    }

    /** Список китов в родном редакторе EpicDuels (страницы). */
    void editMenu(Player player) {
        Object gui = plugin.duels().gui();
        Duels.call(gui, "openKitEditorList", player, Integer.valueOf(0));
    }

    /** Сохранить кит из своего инвентаря: 36 слотов, броня и левая рука. */
    void save(Player player, String name) {
        Object manager = plugin.duels().manager("getKitManager");
        if (manager == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cEpicDuels не отдал KitManager.");
            return;
        }
        String kitName = canonical(name);
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        if (inventory == null) return;
        ItemStack[] contents = inventory.getContents();
        ItemStack[] armor = inventory.getArmorContents();
        ItemStack offHand = inventory.getItemInOffHand();
        boolean empty = true;
        if (contents != null) {
            for (ItemStack stack : contents) {
                if (stack != null && stack.getType() != Material.AIR) empty = false;
            }
        }
        if (empty) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cВозьми в инвентарь вещи для набора и повтори: &f/duelx kit save " + kitName);
            return;
        }
        Object existing = Duels.call(manager, "getKit", kitName);
        if (existing == null) {
            Duels.call(manager, "createKit", kitName, (Object) contents, (Object) armor, offHand);
        } else {
            Duels.call(existing, "setContents", (Object) contents);
            Duels.call(existing, "setArmorContents", (Object) armor);
            Duels.call(existing, "setOffHand", offHand);
            Duels.call(manager, "updateKit", existing);
        }
        Duels.call(manager, "saveKits");
        plugin.msg(player, "&#F8BEFBДуэли &8» &fКит &d" + kitName + "&f сохранён из инвентаря.");
    }

    /** Удалить кит. */
    void remove(Player player, String name) {
        Object manager = plugin.duels().manager("getKitManager");
        String kitName = canonical(name);
        if (manager == null || Duels.call(manager, "getKit", kitName) == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cКита &f" + name + "&c нет.");
            return;
        }
        Duels.call(manager, "deleteKit", kitName);
        Duels.call(manager, "saveKits");
        plugin.msg(player, "&#F8BEFBДуэли &8» &fКит &d" + kitName + "&f удалён.");
    }

    /** Переименовать кит (имя — это и есть id). */
    void rename(Player player, String from, String to) {
        if (to == null || to.trim().isEmpty()) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cУкажи новое имя: &f/duelx kit rename <старое> <новое>");
            return;
        }
        Object manager = plugin.duels().manager("getKitManager");
        Object error = Duels.call(manager, "renameKit", canonical(from), to.trim());
        if (error != null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНе вышло: &f" + error);
            return;
        }
        plugin.msg(player, "&#F8BEFBДуэли &8» &fКит &d" + canonical(from) + "&f теперь &d" + to.trim());
    }

    /** Иконка кита — предмет из руки (видно только родному меню EpicDuels). */
    void icon(Player player, String name) {
        Object kit = kitOf(name);
        if (kit == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cКита &f" + name + "&c нет.");
            return;
        }
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory == null ? null : inventory.getItemInMainHand();
        if (hand == null || hand.getType() == null || hand.getType() == Material.AIR) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cВозьми предмет в руку.");
            return;
        }
        Duels.call(kit, "setIcon", hand.getType());
        Duels.call(plugin.duels().manager("getKitManager"), "saveKits");
        plugin.msg(player, "&#F8BEFBДуэли &8» &fИконка кита &d" + canonical(name) + "&f: &d" + hand.getType());
    }

    /** Вернуть стандартный состав одного кита из этой версии моста. */
    void reset(Player player, String name) {
        Object manager = plugin.duels().manager("getKitManager");
        String kitName = canonical(name);
        String legacy = legacyOf(kitName);
        if (legacy == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cСтандартный состав есть только у десяти базовых: &f"
                    + String.join(", ", NAMES));
            return;
        }
        Duels.call(manager, "deleteKit", kitName);
        Duels.call(manager, "createKit", kitName, (Object) contents(legacy), (Object) armor(legacy), offhand(legacy));
        Object created = Duels.call(manager, "getKit", kitName);
        if (created != null) {
            for (int index = 0; index < NAMES.length; index++) {
                if (NAMES[index].equalsIgnoreCase(kitName)) Duels.call(created, "setIcon", ICONS[index]);
            }
        }
        Duels.call(manager, "saveKits");
        plugin.msg(player, "&#F8BEFBДуэли &8» &fКит &d" + kitName + "&f снова стандартный.");
    }

    /** Кит по русскому или английскому имени. */
    Object kitOf(String name) {
        Object manager = plugin.duels().manager("getKitManager");
        if (manager == null || name == null) return null;
        Object kit = Duels.call(manager, "getKit", canonical(name));
        if (kit == null) kit = Duels.call(manager, "getKit", name.trim());
        return kit;
    }

    /** Родной openKitEdit ничего не возвращает — отличаем «нет метода» от «открыл». */
    private boolean openedBoolean(Object gui, Player player, Object kit) {
        try {
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method candidate : gui.getClass().getMethods()) {
                if (candidate.getName().equals("openKitEdit") && candidate.getParameterCount() == 2) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) return false;
            method.setAccessible(true);
            method.invoke(gui, player, kit);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
