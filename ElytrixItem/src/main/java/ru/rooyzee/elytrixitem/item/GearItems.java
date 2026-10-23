package ru.rooyzee.elytrixitem.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.impl.GearItem;

/**
 * Броня, мечи, кирки и булава — то, что раньше выдавал ExecutableItems.
 *
 * Id оставлены прежними (shalker1…shalker6, farmila, elytra, elytra1),
 * чтобы магазин и старые выдачи продолжали работать.
 */
public final class GearItems {

    private GearItems() {
    }

    public static void register(Main plugin, ItemRegistry registry) {
        // --- броня шалкера
        registry.register(new GearItem(plugin, "shalker1", Material.NETHERITE_HELMET,
                "Шлем шалкера")
                .enchant(Enchantment.PROTECTION, 4, "Защита")
                .enchant(Enchantment.RESPIRATION, 2, "Подводное дыхание")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        registry.register(new GearItem(plugin, "shalker2", Material.NETHERITE_CHESTPLATE,
                "Нагрудник шалкера")
                .enchant(Enchantment.PROTECTION, 4, "Защита")
                .enchant(Enchantment.BLAST_PROTECTION, 4, "Взрывоустойчивость")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        registry.register(new GearItem(plugin, "shalker3", Material.NETHERITE_LEGGINGS,
                "Поножи шалкера")
                .enchant(Enchantment.PROTECTION, 4, "Защита")
                .enchant(Enchantment.BLAST_PROTECTION, 4, "Взрывоустойчивость")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        registry.register(new GearItem(plugin, "shalker4", Material.NETHERITE_BOOTS,
                "Ботинки шалкера")
                .enchant(Enchantment.PROTECTION, 4, "Защита")
                .enchant(Enchantment.BLAST_PROTECTION, 4, "Взрывоустойчивость")
                .enchant(Enchantment.FEATHER_FALLING, 4, "Невесомость")
                .enchant(Enchantment.DEPTH_STRIDER, 3, "Подводная ходьба")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        // --- элитры
        registry.register(new GearItem(plugin, "elytra", Material.ELYTRA,
                "Элитра Шалкера")
                .enchant(Enchantment.UNBREAKING, 5, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        registry.register(new GearItem(plugin, "elytra1", Material.ELYTRA,
                "Элитра Богатыря")
                .enchant(Enchantment.UNBREAKING, 3, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        // --- мечи
        registry.register(new GearItem(plugin, "shalker5", Material.NETHERITE_SWORD,
                "Меч шалкера")
                .enchant(Enchantment.SHARPNESS, 6, "Острота")
                .enchant(Enchantment.FIRE_ASPECT, 2, "Заговор огня")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        registry.register(new GearItem(plugin, "farmila", Material.NETHERITE_SWORD,
                "Меч фамилии")
                .enchant(Enchantment.SHARPNESS, 5, "Острота")
                .enchant(Enchantment.LOOTING, 4, "Добыча")
                .enchant(Enchantment.FIRE_ASPECT, 2, "Заговор огня")
                .enchant(Enchantment.UNBREAKING, 3, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        // --- кирки
        registry.register(new GearItem(plugin, "shalker6", Material.NETHERITE_PICKAXE,
                "Кирка шалкера")
                .enchant(Enchantment.EFFICIENCY, 6, "Эффективность")
                .enchant(Enchantment.FORTUNE, 3, "Удача")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));

        // --- булава (в ExecutableItems её не было — придумали свою)
        registry.register(new GearItem(plugin, "bulava", Material.MACE,
                "Булава Титана")
                .enchant(Enchantment.DENSITY, 5, "Плотность")
                .enchant(Enchantment.BREACH, 4, "Пробой")
                .enchant(Enchantment.WIND_BURST, 2, "Вихрь")
                .enchant(Enchantment.UNBREAKING, 4, "Прочность")
                .enchant(Enchantment.MENDING, 1, "Починка"));
    }
}
