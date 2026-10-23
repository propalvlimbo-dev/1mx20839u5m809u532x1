package ru.elytrix.vault.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<Material, String> RU_MATERIALS = new HashMap<>();
    private static final Map<String, String> RU_ENCHANTS = new HashMap<>();

    static {
        RU_MATERIALS.put(Material.GOLDEN_APPLE, "Золотое яблоко");
        RU_MATERIALS.put(Material.ENCHANTED_GOLDEN_APPLE, "Зачарованное золотое яблоко");
        RU_MATERIALS.put(Material.ENDER_PEARL, "Эндер-жемчуг");
        RU_MATERIALS.put(Material.CHORUS_FRUIT, "Плод хоруса");
        RU_MATERIALS.put(Material.GOLDEN_CARROT, "Золотая морковь");
        RU_MATERIALS.put(Material.COOKED_BEEF, "Жареная говядина");
        RU_MATERIALS.put(Material.FIREWORK_ROCKET, "Фейерверк");
        RU_MATERIALS.put(Material.EXPERIENCE_BOTTLE, "Пузырёк опыта");
        RU_MATERIALS.put(Material.TOTEM_OF_UNDYING, "Тотем бессмертия");
        RU_MATERIALS.put(Material.ENCHANTED_BOOK, "Книга зачарования");
        RU_MATERIALS.put(Material.DIAMOND_HELMET, "Алмазный шлем");
        RU_MATERIALS.put(Material.DIAMOND_CHESTPLATE, "Алмазный нагрудник");
        RU_MATERIALS.put(Material.DIAMOND_LEGGINGS, "Алмазные поножи");
        RU_MATERIALS.put(Material.DIAMOND_BOOTS, "Алмазные ботинки");
        RU_MATERIALS.put(Material.DIAMOND_SWORD, "Алмазный меч");
        RU_MATERIALS.put(Material.DIAMOND_PICKAXE, "Алмазная кирка");
        RU_MATERIALS.put(Material.DIAMOND_AXE, "Алмазный топор");
        RU_MATERIALS.put(Material.NETHERITE_HELMET, "Незеритовый шлем");
        RU_MATERIALS.put(Material.NETHERITE_CHESTPLATE, "Незеритовый нагрудник");
        RU_MATERIALS.put(Material.NETHERITE_LEGGINGS, "Незеритовые поножи");
        RU_MATERIALS.put(Material.NETHERITE_BOOTS, "Незеритовые ботинки");
        RU_MATERIALS.put(Material.NETHERITE_SWORD, "Незеритовый меч");
        RU_MATERIALS.put(Material.NETHERITE_PICKAXE, "Незеритовая кирка");
        RU_MATERIALS.put(Material.NETHERITE_AXE, "Незеритовый топор");
        RU_MATERIALS.put(Material.NETHERITE_SHOVEL, "Незеритовая лопата");
        RU_MATERIALS.put(Material.BOW, "Лук");
        RU_MATERIALS.put(Material.CROSSBOW, "Арбалет");
        RU_MATERIALS.put(Material.TRIDENT, "Трезубец");
        RU_MATERIALS.put(Material.MACE, "Булава");
        RU_MATERIALS.put(Material.ELYTRA, "Элитры");
        RU_MATERIALS.put(Material.SHULKER_BOX, "Шалкеровый ящик");
        RU_MATERIALS.put(Material.SPAWNER, "Спавнер");
        RU_MATERIALS.put(Material.ANVIL, "Наковальня");
        RU_MATERIALS.put(Material.POTION, "Зелье");
        RU_MATERIALS.put(Material.SPLASH_POTION, "Взрывное зелье");
        RU_MATERIALS.put(Material.TIPPED_ARROW, "Боевая стрела");

        RU_ENCHANTS.put("protection", "Защита");
        RU_ENCHANTS.put("fire_protection", "Огнеупорность");
        RU_ENCHANTS.put("feather_falling", "Невесомость");
        RU_ENCHANTS.put("blast_protection", "Взрывоустойчивость");
        RU_ENCHANTS.put("projectile_protection", "Защита от снарядов");
        RU_ENCHANTS.put("respiration", "Подводное дыхание");
        RU_ENCHANTS.put("aqua_affinity", "Подводник");
        RU_ENCHANTS.put("thorns", "Шипы");
        RU_ENCHANTS.put("depth_strider", "Подводная ходьба");
        RU_ENCHANTS.put("sharpness", "Острота");
        RU_ENCHANTS.put("smite", "Небесная кара");
        RU_ENCHANTS.put("knockback", "Отдача");
        RU_ENCHANTS.put("fire_aspect", "Заговор огня");
        RU_ENCHANTS.put("looting", "Добыча");
        RU_ENCHANTS.put("sweeping_edge", "Разящий клинок");
        RU_ENCHANTS.put("efficiency", "Эффективность");
        RU_ENCHANTS.put("silk_touch", "Шёлковое касание");
        RU_ENCHANTS.put("unbreaking", "Прочность");
        RU_ENCHANTS.put("fortune", "Удача");
        RU_ENCHANTS.put("power", "Сила");
        RU_ENCHANTS.put("punch", "Откидывание");
        RU_ENCHANTS.put("flame", "Воспламенение");
        RU_ENCHANTS.put("infinity", "Бесконечность");
        RU_ENCHANTS.put("mending", "Починка");
        RU_ENCHANTS.put("wind_burst", "Порыв ветра");
        RU_ENCHANTS.put("breach", "Пробивание");
        RU_ENCHANTS.put("density", "Плотность");
        RU_ENCHANTS.put("soul_speed", "Скорость души");
        RU_ENCHANTS.put("swift_sneak", "Проворство");
    }

    private TextUtil() {}

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer(input.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (int i = 0; i < 6; i++) {
                rep.append('§').append(hex.charAt(i));
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static List<String> colorList(List<String> lines) {
        List<String> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(color(line));
        }
        return result;
    }

    public static String cleanItemTitle(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return "Предмет";
        }
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String stripped = ChatColor.stripColor(meta.getDisplayName());
                if (stripped != null) {
                    stripped = stripped.replace("«", "").replace("»", "").trim();
                    if (!stripped.isEmpty()) {
                        return stripped;
                    }
                }
            }
            if (meta instanceof EnchantmentStorageMeta bookMeta && bookMeta.hasStoredEnchants()) {
                Map.Entry<Enchantment, Integer> first = bookMeta.getStoredEnchants().entrySet().iterator().next();
                String enchKey = first.getKey().getKey().getKey().toLowerCase(Locale.ROOT);
                String ru = RU_ENCHANTS.getOrDefault(enchKey, enchKey);
                int lvl = first.getValue();
                return lvl <= 1 && "mending".equals(enchKey) ? "Книга: " + ru : "Книга: " + ru + " " + roman(lvl);
            }
        }
        String mapped = RU_MATERIALS.get(stack.getType());
        if (mapped != null) {
            return mapped;
        }
        String raw = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(level);
        };
    }
}
