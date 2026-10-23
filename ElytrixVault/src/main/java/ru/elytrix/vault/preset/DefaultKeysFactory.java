package ru.elytrix.vault.preset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import ru.elytrix.vault.model.LootEntry;
import ru.elytrix.vault.model.Rarity;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.util.HeadUtil;
import ru.elytrix.vault.util.TextUtil;

public final class DefaultKeysFactory {

    private static final String SHAR_1_B64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTQ1ZmQyYzM3MzZhZjRiZDI4MTEyOTY2NjFlMGNiNDliYWIyY2ZhNjVmNWM5ZTU5OGFhNDNiZWJmYTFlYTM2OCJ9fX0=";
    private static final String SHAR_2_B64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFiNGIyMDE4OWQ5MDJiYzZjM2ZhYmUyM2Q5ZjU3ODY5N2Y1NDRjZjQxNTMxNzc4NzM3NDExODM5M2RhNCJ9fX0=";
    private static final String SHAR_3_B64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjg5MDQyMDgyYmI3YTc2MThiNzg0ZWU3NjA1YTEzNGM1ODgzNGUyMWUzNzRjODg4OTM3MTYxMDU3ZjZjNyJ9fX0=";
    private static final String SHAR_4_B64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjVjNGQyNGFmZmRkNDgxMDI2MjAzNjE1MjdkMjE1NmUxOGMyMjNiYWU1MTg5YWM0Mzk4MTU2NDNmM2NmZjlkIn19fQ==";

    private DefaultKeysFactory() {}

    public static Map<String, VaultKeyType> createDefaults() {
        Map<String, VaultKeyType> map = new LinkedHashMap<>();
        map.put("rusty", buildRusty());
        map.put("knowledge", buildKnowledge());
        map.put("royal", buildRoyal());
        map.put("void", buildVoid());
        map.put("unique", buildUnique());
        return map;
    }

    private static VaultKeyType buildRusty() {
        VaultKeyType key = new VaultKeyType(
                "rusty",
                "&7Ржавый ключ",
                1,
                2,
                4,
                List.of("Расходники и ресурсы для PvP")
        );
        List<LootEntry> e = key.getEntries();
        e.add(item("rusty_gap_8", new ItemStack(Material.GOLDEN_APPLE, 8), Rarity.COMMON, 0));
        e.add(item("rusty_pearl_8", new ItemStack(Material.ENDER_PEARL, 8), Rarity.COMMON, 0));
        e.add(item("rusty_chorus_24", new ItemStack(Material.CHORUS_FRUIT, 24), Rarity.COMMON, 0));
        e.add(item("rusty_carrot_32", new ItemStack(Material.GOLDEN_CARROT, 32), Rarity.COMMON, 0));
        e.add(item("rusty_beef_32", new ItemStack(Material.COOKED_BEEF, 32), Rarity.COMMON, 0));
        e.add(item("rusty_fw_24", new ItemStack(Material.FIREWORK_ROCKET, 24), Rarity.COMMON, 0));
        e.add(item("rusty_xp_32", new ItemStack(Material.EXPERIENCE_BOTTLE, 32), Rarity.UNCOMMON, 0));
        e.add(item("rusty_gap_16", new ItemStack(Material.GOLDEN_APPLE, 16), Rarity.UNCOMMON, 0));
        e.add(item("rusty_pearl_16", new ItemStack(Material.ENDER_PEARL, 16), Rarity.UNCOMMON, 0));
        e.add(item("rusty_pot_str", potion(Material.POTION, PotionType.STRONG_STRENGTH, "&cЗелье силы II", 2), Rarity.UNCOMMON, 0));
        e.add(item("rusty_pot_spd", potion(Material.POTION, PotionType.STRONG_SWIFTNESS, "&bЗелье скорости II", 2), Rarity.UNCOMMON, 0));
        e.add(item("rusty_pot_heal", potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, "&dВзрывное зелье исцеления II", 3), Rarity.UNCOMMON, 0));
        e.add(item("rusty_pot_fire", potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, "&6Зелье огнестойкости", 2), Rarity.UNCOMMON, 0));
        e.add(item("rusty_totem", new ItemStack(Material.TOTEM_OF_UNDYING, 1), Rarity.RARE, 0));
        e.add(shop("rusty_arrow1", Material.TIPPED_ARROW, 8, "&7« &#F8BEFBСтрела поражения &7»",
                "eitems give %player% arrow1 8", Rarity.RARE, 0));
        e.add(shop("rusty_arrow2", Material.TIPPED_ARROW, 8, "&7« &#F8BEFBСтрела негатива &7»",
                "eitems give %player% arrow2 8", Rarity.RARE, 0));
        e.add(shop("rusty_arrow5", Material.TIPPED_ARROW, 8, "&7« &#F8BEFBСтрела черепахи &7»",
                "eitems give %player% arrow5 8", Rarity.EPIC, 0));
        return key;
    }

    private static VaultKeyType buildKnowledge() {
        VaultKeyType key = new VaultKeyType(
                "knowledge",
                "&bКлюч знаний",
                3,
                1,
                3,
                List.of("Книги зачарования (от 1 до 3 шт.)")
        );
        List<LootEntry> e = key.getEntries();
        e.add(item("know_unb3", book(Enchantment.UNBREAKING, 3), Rarity.COMMON, 0));
        e.add(item("know_eff5", book(Enchantment.EFFICIENCY, 5), Rarity.COMMON, 0));
        e.add(item("know_pow5", book(Enchantment.POWER, 5), Rarity.COMMON, 0));
        e.add(item("know_ff4", book(Enchantment.FEATHER_FALLING, 4), Rarity.COMMON, 0));
        e.add(item("know_ds3", book(Enchantment.DEPTH_STRIDER, 3), Rarity.COMMON, 0));
        e.add(item("know_prot4", book(Enchantment.PROTECTION, 4), Rarity.UNCOMMON, 0));
        e.add(item("know_sharp5", book(Enchantment.SHARPNESS, 5), Rarity.UNCOMMON, 0));
        e.add(item("know_fire2", book(Enchantment.FIRE_ASPECT, 2), Rarity.UNCOMMON, 0));
        e.add(item("know_silk", book(Enchantment.SILK_TOUCH, 1), Rarity.UNCOMMON, 0));
        e.add(item("know_fort3", book(Enchantment.FORTUNE, 3), Rarity.RARE, 0));
        e.add(item("know_loot3", book(Enchantment.LOOTING, 3), Rarity.RARE, 0));
        e.add(item("know_mend", book(Enchantment.MENDING, 1), Rarity.RARE, 0));
        e.add(item("know_breach4", book(Enchantment.BREACH, 4), Rarity.EPIC, 0));
        e.add(item("know_dens5", book(Enchantment.DENSITY, 5), Rarity.EPIC, 0));
        e.add(item("know_wind2", book(Enchantment.WIND_BURST, 2), Rarity.EPIC, 0));
        e.add(shop("know_sun", Material.ENCHANTED_BOOK, 1, "&7« &6Солнечные оковы &7»",
                "eitems give %player% enchant_sun_shackles 1", Rarity.EPIC, 0));
        e.add(shop("know_exp_arrow", Material.ENCHANTED_BOOK, 1, "&7« &cВзрывная стрела &7»",
                "eitems give %player% enchant_explosive_arrow 1", Rarity.EPIC, 0));
        e.add(shop("know_bur", Material.ENCHANTED_BOOK, 1, "&7« &aБур &7»",
                "eitems give %player% enchant_byr 1", Rarity.LEGENDARY, 0));
        return key;
    }

    private static VaultKeyType buildRoyal() {
        VaultKeyType key = new VaultKeyType(
                "royal",
                "&6Королевский ключ",
                4,
                1,
                3,
                List.of("Броня Защита IV и редкие предметы из /shop")
        );
        List<LootEntry> e = key.getEntries();
        e.add(item("roy_d_helm", gear(Material.DIAMOND_HELMET, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.RESPIRATION, 3), Rarity.COMMON, 0));
        e.add(item("roy_d_chest", gear(Material.DIAMOND_CHESTPLATE, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), Rarity.COMMON, 0));
        e.add(item("roy_d_legs", gear(Material.DIAMOND_LEGGINGS, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3), Rarity.COMMON, 0));
        e.add(item("roy_d_boots", gear(Material.DIAMOND_BOOTS, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4), Rarity.COMMON, 0));

        e.add(item("roy_n_helm", gear(Material.NETHERITE_HELMET, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.RESPIRATION, 3, Enchantment.MENDING, 1), Rarity.UNCOMMON, 0));
        e.add(item("roy_n_chest", gear(Material.NETHERITE_CHESTPLATE, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.UNCOMMON, 0));
        e.add(item("roy_n_legs", gear(Material.NETHERITE_LEGGINGS, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.UNCOMMON, 0));
        e.add(item("roy_n_boots", gear(Material.NETHERITE_BOOTS, null,
                Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3, Enchantment.MENDING, 1), Rarity.RARE, 0));

        e.add(shop("roy_voina", Material.POTION, 3, "&7« &#F8BEFBЗелье Войны &7»",
                "eitems give %player% potion_voina 3", Rarity.EPIC, 0));
        e.add(shop("roy_salv", Material.POTION, 1, "&7« &#F8BEFBЗелье Спасения &7»",
                "eitems give %player% potion_salvation 1", Rarity.EPIC, 0));
        e.add(headShop("roy_shar1", SHAR_1_B64, "&7« &#F8BEFBШар Новичка &7»",
                "eitems give %player% shar1 1", Rarity.EPIC, 0));
        e.add(shop("roy_totem2", Material.TOTEM_OF_UNDYING, 1, "&7« &#F8BEFBТотем доктора &7»",
                "eitems give %player% totem2 1", Rarity.LEGENDARY, 0));
        e.add(headShop("roy_shar2", SHAR_2_B64, "&7« &#F8BEFBШар Змеи &7»",
                "eitems give %player% shar2 1", Rarity.LEGENDARY, 0));
        e.add(shop("roy_shalk1", Material.NETHERITE_HELMET, 1, "&7« &#F8BEFBШлем шалкера &7»",
                "eitems give %player% shalker1 1", Rarity.LEGENDARY, 1));
        return key;
    }

    private static VaultKeyType buildVoid() {
        VaultKeyType key = new VaultKeyType(
                "void",
                "&5Пустотный ключ",
                5,
                2,
                7,
                List.of("Предметы из /shop и топовых китов (от 2 до 7 шт.)")
        );
        List<LootEntry> e = key.getEntries();
        e.add(item("void_kit_sword", gear(Material.NETHERITE_SWORD, null,
                Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.COMMON, 0));
        e.add(item("void_kit_pick", gear(Material.NETHERITE_PICKAXE, null,
                Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.COMMON, 0));
        e.add(item("void_kit_axe", gear(Material.NETHERITE_AXE, null,
                Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3), Rarity.COMMON, 0));
        e.add(item("void_kit_bow", gear(Material.BOW, null,
                Enchantment.POWER, 5, Enchantment.FLAME, 1, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.COMMON, 0));
        e.add(item("void_kit_gaps", new ItemStack(Material.GOLDEN_APPLE, 48), Rarity.COMMON, 0));

        e.add(item("void_kit_chest", gear(Material.NETHERITE_CHESTPLATE, null,
                Enchantment.PROTECTION, 4, Enchantment.FIRE_PROTECTION, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.UNCOMMON, 0));
        e.add(item("void_kit_legs", gear(Material.NETHERITE_LEGGINGS, null,
                Enchantment.PROTECTION, 4, Enchantment.FIRE_PROTECTION, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), Rarity.UNCOMMON, 0));
        e.add(item("void_kit_egap", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2), Rarity.UNCOMMON, 0));
        e.add(shop("void_pot_war", Material.POTION, 3, "&7« &#F8BEFBЗелье Войны &7»",
                "eitems give %player% potion_voina 3", Rarity.UNCOMMON, 0));
        e.add(shop("void_arrow_lev", Material.TIPPED_ARROW, 8, "&7« &#F8BEFBСтрела фантома &7»",
                "eitems give %player% arrow_levit 8", Rarity.UNCOMMON, 0));

        e.add(shop("void_totem1", Material.TOTEM_OF_UNDYING, 1, "&7« &#F8BEFBТотем киллера &7»",
                "eitems give %player% totem1 1", Rarity.RARE, 0));
        e.add(shop("void_totem3", Material.TOTEM_OF_UNDYING, 1, "&7« &#F8BEFBТотем богатыря &7»",
                "eitems give %player% totem3 1", Rarity.RARE, 0));
        e.add(headShop("void_shar2", SHAR_2_B64, "&7« &#F8BEFBШар Змеи &7»",
                "eitems give %player% shar2 1", Rarity.RARE, 0));
        e.add(headShop("void_shar3", SHAR_3_B64, "&7« &#F8BEFBШар Солнца &7»",
                "eitems give %player% shar3 1", Rarity.RARE, 0));
        e.add(shop("void_exp_trap", Material.FIRE_CHARGE, 1, "&7« &#FF6B6BВзрывная ловушка &7»",
                "eitems give %player% explosive_trap 1", Rarity.RARE, 0));
        e.add(shop("void_anti_pearl", Material.ENDER_EYE, 1, "&7« &#8C5196Анти-Пёрл &7»",
                "eitems give %player% anti_pearl 1", Rarity.RARE, 0));

        e.add(shop("void_shalk2", Material.NETHERITE_CHESTPLATE, 1, "&7« &#F8BEFBНагрудник шалкера &7»",
                "eitems give %player% shalker2 1", Rarity.EPIC, 0));
        e.add(shop("void_shalk5", Material.NETHERITE_SWORD, 1, "&7« &#F8BEFBМеч шалкера &7»",
                "eitems give %player% shalker5 1", Rarity.EPIC, 0));
        e.add(shop("void_inf_fw", Material.FIREWORK_ROCKET, 1, "&7« &#D8A6FFБесконечный фейерверк &7»",
                "eitems give %player% infinite_firework 1", Rarity.EPIC, 0));
        e.add(headShop("void_shar4", SHAR_4_B64, "&7« &#F8BEFBШар Шалкера &7»",
                "eitems give %player% shar4 1", Rarity.EPIC, 0));

        e.add(shop("void_tyagun", Material.TRIDENT, 1, "&7« &bТягун &7»",
                "eitems give %player% tyagun 1", Rarity.LEGENDARY, 0));
        e.add(shop("void_raskol", Material.IRON_PICKAXE, 1, "&7« &#F8BEFBКирка Раскола &7»",
                "eitems give %player% kirkaraskola 1", Rarity.LEGENDARY, 0));
        e.add(shop("void_elytra", Material.ELYTRA, 1, "&7« &#F8BEFBЭлитра Шалкера &7»",
                "eitems give %player% elytra 1", Rarity.LEGENDARY, 0));
        return key;
    }

    private static VaultKeyType buildUnique() {
        VaultKeyType key = new VaultKeyType(
                "unique",
                "&#F8BEFBУникальный ключ",
                5,
                2,
                4,
                List.of("Лучший уникальный лут из /shop")
        );
        List<LootEntry> e = key.getEntries();
        e.add(shop("uni_anti_pearl", Material.ENDER_EYE, 1, "&7« &#8C5196Анти-Пёрл &7»",
                "eitems give %player% anti_pearl 1", Rarity.COMMON, 0));
        e.add(shop("uni_exp_pot", Material.FLOWER_POT, 1, "&7« &#8B7D6BГоршочек с опытом &7»",
                "eitems give %player% experience_pot 1", Rarity.COMMON, 0));
        e.add(shop("uni_exp_trap", Material.FIRE_CHARGE, 1, "&7« &#FF6B6BВзрывная ловушка &7»",
                "eitems give %player% explosive_trap 1", Rarity.COMMON, 0));
        e.add(shop("uni_totem3", Material.TOTEM_OF_UNDYING, 1, "&7« &#F8BEFBТотем богатыря &7»",
                "eitems give %player% totem3 1", Rarity.COMMON, 0));
        e.add(shop("uni_totem4", Material.TOTEM_OF_UNDYING, 1, "&7« &#F8BEFBТотем вардена &7»",
                "eitems give %player% totem4 1", Rarity.COMMON, 0));

        e.add(shop("uni_inf_fw", Material.FIREWORK_ROCKET, 1, "&7« &#D8A6FFБесконечный фейерверк &7»",
                "eitems give %player% infinite_firework 1", Rarity.UNCOMMON, 0));
        e.add(shop("uni_aura_fall", Material.LIGHT_GRAY_DYE, 1, "&7« &#EAEAEAАура защиты от падения &7»",
                "eitems give %player% fall_protection_aura 1", Rarity.UNCOMMON, 0));
        e.add(shop("uni_aura_miner", Material.YELLOW_DYE, 1, "&7« &#FFD98CАура шахтёра &7»",
                "eitems give %player% miner_aura 1", Rarity.UNCOMMON, 0));
        e.add(shop("uni_totem5", Material.TOTEM_OF_UNDYING, 1, "&7« &#F8BEFBТотем шалкера &7»",
                "eitems give %player% totem5 1", Rarity.UNCOMMON, 0));
        e.add(headShop("uni_shar3", SHAR_3_B64, "&7« &#F8BEFBШар Солнца &7»",
                "eitems give %player% shar3 1", Rarity.UNCOMMON, 0));
        e.add(shop("uni_brew2", Material.BREWING_STAND, 1, "&7« &#F8BEFBСкоростная зельеварка &7»",
                "eitems give %player% twobrew 1", Rarity.UNCOMMON, 0));

        e.add(headShop("uni_shar4", SHAR_4_B64, "&7« &#F8BEFBШар Шалкера &7»",
                "eitems give %player% shar4 1", Rarity.RARE, 0));
        e.add(shop("uni_aura_cryst", Material.BLACK_DYE, 1, "&7« &#5B2C6FАура защиты от кристалла &7»",
                "eitems give %player% crystal_protection_aura 1", Rarity.RARE, 0));
        e.add(shop("uni_shalk1", Material.NETHERITE_HELMET, 1, "&7« &#F8BEFBШлем шалкера &7»",
                "eitems give %player% shalker1 1", Rarity.RARE, 0));
        e.add(shop("uni_shalk2", Material.NETHERITE_CHESTPLATE, 1, "&7« &#F8BEFBНагрудник шалкера &7»",
                "eitems give %player% shalker2 1", Rarity.RARE, 0));
        e.add(shop("uni_shalk3", Material.NETHERITE_LEGGINGS, 1, "&7« &#F8BEFBПоножи шалкера &7»",
                "eitems give %player% shalker3 1", Rarity.RARE, 0));
        e.add(shop("uni_shalk4", Material.NETHERITE_BOOTS, 1, "&7« &#F8BEFBБотинки шалкера &7»",
                "eitems give %player% shalker4 1", Rarity.RARE, 0));
        e.add(shop("uni_shalk5", Material.NETHERITE_SWORD, 1, "&7« &#F8BEFBМеч шалкера &7»",
                "eitems give %player% shalker5 1", Rarity.RARE, 0));
        e.add(shop("uni_shalk6", Material.NETHERITE_PICKAXE, 1, "&7« &#F8BEFBКирка шалкера &7»",
                "eitems give %player% shalker6 1", Rarity.RARE, 0));
        e.add(shop("uni_bur", Material.ENCHANTED_BOOK, 1, "&7« &aБур &7»",
                "eitems give %player% enchant_byr 1", Rarity.RARE, 0));
        e.add(shop("uni_spawner", Material.SPAWNER, 1, "&7« &#F8BEFBСпавнер &7»",
                "minecraft:give %player% minecraft:spawner 1", Rarity.RARE, 0));

        e.add(shop("uni_farmila", Material.NETHERITE_SWORD, 1, "&7« &#F8BEFBМеч фармила &7»",
                "eitems give %player% farmila 1", Rarity.EPIC, 0));
        e.add(shop("uni_raskol", Material.IRON_PICKAXE, 1, "&7« &#F8BEFBКирка Раскола &7»",
                "eitems give %player% kirkaraskola 1", Rarity.EPIC, 0));
        e.add(shop("uni_duper_furn", Material.FURNACE, 1, "&7« &#F8BEFBПечка дюпера &7»",
                "eitems give %player% threeFurnace 1", Rarity.EPIC, 0));
        e.add(shop("uni_tyagun", Material.TRIDENT, 1, "&7« &bТягун &7»",
                "eitems give %player% tyagun 1", Rarity.EPIC, 0));
        e.add(shop("uni_elytra", Material.ELYTRA, 1, "&7« &#F8BEFBЭлитра Шалкера &7»",
                "eitems give %player% elytra 1", Rarity.EPIC, 0));

        e.add(shop("uni_collector", Material.NETHERITE_SWORD, 1, "&7« &#A8FF78Меч Сбора &7»",
                "eitems give %player% collector_sword 1", Rarity.LEGENDARY, 0));
        e.add(shop("uni_bulava", Material.MACE, 1, "&7« &#F8BEFBБулава Титана &7»",
                "eitems give %player% bulava 1", Rarity.LEGENDARY, 0));
        e.add(shop("uni_elytra_bog", Material.ELYTRA, 1, "&7« &#F8BEFBЭлитра Богатыря &7»",
                "eitems give %player% elytra1 1", Rarity.LEGENDARY, 0));
        return key;
    }

    private static LootEntry item(String id, ItemStack stack, Rarity rarity, int customWeight) {
        return new LootEntry(id, stack, null, rarity, customWeight);
    }

    private static LootEntry shop(String id, Material icon, int amount, String displayName, String cmd, Rarity rarity, int customWeight) {
        ItemStack stack = new ItemStack(icon, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(displayName));
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return new LootEntry(id, stack, cmd, rarity, customWeight);
    }

    private static LootEntry headShop(String id, String base64, String displayName, String cmd, Rarity rarity, int customWeight) {
        ItemStack stack = HeadUtil.createTexturedHead(base64);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(displayName));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return new LootEntry(id, stack, cmd, rarity, customWeight);
    }

    private static ItemStack book(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(enchantment, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    private static ItemStack potion(Material type, PotionType potionType, String name, int amount) {
        ItemStack stack = new ItemStack(type, amount);
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(potionType);
            meta.setDisplayName(TextUtil.color(name));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack gear(Material mat, String name, Object... enchantsAndLevels) {
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(TextUtil.color(name));
            }
            for (int i = 0; i + 1 < enchantsAndLevels.length; i += 2) {
                Enchantment ench = (Enchantment) enchantsAndLevels[i];
                int lvl = (Integer) enchantsAndLevels[i + 1];
                if (ench != null) {
                    meta.addEnchant(ench, lvl, true);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
