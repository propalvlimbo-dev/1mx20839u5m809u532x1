package ru.rooyzee.elytrixitem.item;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.impl.BrewItem;
import ru.rooyzee.elytrixitem.item.impl.TotemItem;

/**
 * Зелья, стрелы и тотемы — всё, что раньше выдавал ExecutableItems.
 *
 * Id оставлены прежними (potion_*, arrow*, totem*), чтобы магазин и старые
 * выдачи продолжали работать.
 */
public final class ConsumableItems {

    private ConsumableItems() {
    }

    public static void register(Main plugin, ItemRegistry registry) {
        registerPotions(plugin, registry);
        registerArrows(plugin, registry);
        registerTotems(plugin, registry);
    }

    private static void registerPotions(Main plugin, ItemRegistry registry) {
        registry.register(new BrewItem(plugin, "potion_healt", Material.POTION,
                "Зелье Оздоровления", 11534374,
                BrewItem.effect(PotionEffectType.REGENERATION, 1200, 3),
                BrewItem.effect(PotionEffectType.RESISTANCE, 1800, 1)));

        registry.register(new BrewItem(plugin, "potion_lightness", Material.POTION,
                "Зелье Лёгкости", 13513912,
                BrewItem.effect(PotionEffectType.SPEED, 900, 2),
                BrewItem.effect(PotionEffectType.HASTE, 3600, 0)));

        registry.register(new BrewItem(plugin, "potion_voina", Material.POTION,
                "Зелье Войны", 13103616,
                BrewItem.effect(PotionEffectType.STRENGTH, 1800, 1),
                BrewItem.effect(PotionEffectType.SPEED, 1800, 1),
                BrewItem.effect(PotionEffectType.FIRE_RESISTANCE, 9600, 0)));

        registry.register(new BrewItem(plugin, "potion_disappearances", Material.POTION,
                "Зелье Исчезновения", 8453888,
                BrewItem.effect(PotionEffectType.INVISIBILITY, 9600, 1),
                BrewItem.effect(PotionEffectType.SPEED, 3600, 1),
                BrewItem.effect(PotionEffectType.RESISTANCE, 3600, 0)));

        registry.register(new BrewItem(plugin, "potion_sportsmena", Material.POTION,
                "Зелье Спортсмена", 3846578,
                BrewItem.effect(PotionEffectType.REGENERATION, 2100, 1),
                BrewItem.effect(PotionEffectType.HASTE, 900, 1),
                BrewItem.effect(PotionEffectType.FIRE_RESISTANCE, 9600, 0)));

        registry.register(new BrewItem(plugin, "potion_salvation", Material.POTION,
                "Зелье Спасения", 16383486,
                BrewItem.effect(PotionEffectType.LEVITATION, 100, 3),
                BrewItem.effect(PotionEffectType.LUCK, 1800, 1)));
    }

    private static void registerArrows(Main plugin, ItemRegistry registry) {
        registry.register(new BrewItem(plugin, "arrow1", Material.TIPPED_ARROW,
                "Стрела поражения", 9768414,
                BrewItem.effect(PotionEffectType.MINING_FATIGUE, 200, 0)));

        registry.register(new BrewItem(plugin, "arrow2", Material.TIPPED_ARROW,
                "Стрела негатива", 7194613,
                BrewItem.effect(PotionEffectType.NAUSEA, 200, 1),
                BrewItem.effect(PotionEffectType.BLINDNESS, 200, 1)));

        registry.register(new BrewItem(plugin, "arrow3", Material.TIPPED_ARROW,
                "Стрела психа", 6194613,
                BrewItem.effect(PotionEffectType.NAUSEA, 200, 1),
                BrewItem.effect(PotionEffectType.POISON, 200, 1)));

        registry.register(new BrewItem(plugin, "arrow4", Material.TIPPED_ARROW,
                "Стрела мотылька", 139,
                BrewItem.effect(PotionEffectType.SLOWNESS, 200, 1),
                BrewItem.effect(PotionEffectType.GLOWING, 200, 1)));

        registry.register(new BrewItem(plugin, "arrow5", Material.TIPPED_ARROW,
                "Стрела черепахи", 16744704,
                BrewItem.effect(PotionEffectType.WEAKNESS, 200, 1),
                BrewItem.effect(PotionEffectType.HUNGER, 200, 1)));

        registry.register(new BrewItem(plugin, "arrow_levit", Material.TIPPED_ARROW,
                "Стрела фантома", 16382196,
                BrewItem.effect(PotionEffectType.LEVITATION, 100, 1)));
    }

    private static void registerTotems(Main plugin, ItemRegistry registry) {
        registry.register(new TotemItem(plugin, "totem1", "Тотем киллера")
                .stat(Attribute.ATTACK_DAMAGE, 4.0D, "к урону"));

        registry.register(new TotemItem(plugin, "totem2", "Тотем доктора")
                .stat(Attribute.MAX_HEALTH, 4.0D, "к макс. здоровью"));

        registry.register(new TotemItem(plugin, "totem3", "Тотем богатыря")
                .stat(Attribute.ARMOR, 3.0D, "к броне"));

        registry.register(new TotemItem(plugin, "totem4", "Тотем вардена")
                .stat(Attribute.ARMOR, 3.0D, "к броне")
                .stat(Attribute.ATTACK_DAMAGE, 2.0D, "к урону"));

        registry.register(new TotemItem(plugin, "totem5", "Тотем шалкера")
                .stat(Attribute.ARMOR, 2.0D, "к броне")
                .stat(Attribute.ATTACK_DAMAGE, 4.0D, "к урону")
                .stat(Attribute.MAX_HEALTH, -4.0D, "к макс. здоровью"));
    }
}
