package ru.rooyzee.elytrixitem.item;

import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.impl.CustomBrewingItem;
import ru.rooyzee.elytrixitem.item.impl.CustomFurnaceItem;
import ru.rooyzee.elytrixitem.item.impl.RuneItem;

/**
 * Шары из ShadowCustomItems и печки/зельеварки из ShadowFurnaces.
 *
 * Id оставлены прежними (shar1…donshar, twoFurnace…, onebrew/twobrew),
 * чтобы старые выдачи и команды продолжали работать.
 */
public final class MechanicItems {

    private MechanicItems() {
    }

    public static void register(Main plugin, ItemRegistry registry) {
        registerRunes(plugin, registry);
        registerFurnaces(plugin, registry);
        registerBrewingStands(plugin, registry);
    }

    private static void registerRunes(Main plugin, ItemRegistry registry) {
        registry.register(new RuneItem(plugin, "shar1", "Шар Новичка",
                "145fd2c3736af4bd2811296661e0cb49bab2cfa65f5c9e598aa43bebfa1ea368",
                RuneItem.effect(PotionEffectType.STRENGTH, 2, "Сила"),
                RuneItem.effect(PotionEffectType.RESISTANCE, 1, "Сопротивление"),
                RuneItem.effect(PotionEffectType.FIRE_RESISTANCE, 1, "Огнестойкость")));

        registry.register(new RuneItem(plugin, "shar2", "Шар Змеи",
                "51b4b20189d902bc6c3fabe23d9f578697f544cf415317787374118393da4",
                RuneItem.effect(PotionEffectType.STRENGTH, 2, "Сила"),
                RuneItem.effect(PotionEffectType.SPEED, 1, "Скорость"),
                RuneItem.effect(PotionEffectType.RESISTANCE, 1, "Сопротивление"),
                RuneItem.effect(PotionEffectType.FIRE_RESISTANCE, 1, "Огнестойкость")));

        registry.register(new RuneItem(plugin, "shar3", "Шар Солнца",
                "b89042082bb7a7618b784ee7605a134c58834e21e374c888937161057f6c7",
                RuneItem.effect(PotionEffectType.STRENGTH, 2, "Сила"),
                RuneItem.effect(PotionEffectType.SPEED, 2, "Скорость"),
                RuneItem.effect(PotionEffectType.RESISTANCE, 1, "Сопротивление"),
                RuneItem.effect(PotionEffectType.FIRE_RESISTANCE, 1, "Огнестойкость")));

        registry.register(new RuneItem(plugin, "shar4", "Шар Шалкера",
                "25c4d24affdd48102620361527d2156e18c223bae5189ac439815643f3cff9d",
                RuneItem.effect(PotionEffectType.STRENGTH, 2, "Сила"),
                RuneItem.effect(PotionEffectType.SPEED, 2, "Скорость"),
                RuneItem.effect(PotionEffectType.RESISTANCE, 2, "Сопротивление"),
                RuneItem.effect(PotionEffectType.FIRE_RESISTANCE, 1, "Огнестойкость")));

        registry.register(new RuneItem(plugin, "donshar", "Шар Повелителя",
                "c84757ec72612eeac19ccaf80b31c861c791f25d32dda3fb37f55205755a04",
                RuneItem.effect(PotionEffectType.STRENGTH, 3, "Сила"),
                RuneItem.effect(PotionEffectType.SPEED, 2, "Скорость"),
                RuneItem.effect(PotionEffectType.REGENERATION, 1, "Регенерация"),
                RuneItem.effect(PotionEffectType.RESISTANCE, 1, "Сопротивление"),
                RuneItem.effect(PotionEffectType.FIRE_RESISTANCE, 1, "Огнестойкость")));
    }

    private static void registerFurnaces(Main plugin, ItemRegistry registry) {
        registry.register(new CustomFurnaceItem(plugin, "twoFurnace", "Быстрая печка",
                "Скорость плавки: &#F8BEFBВ 2 раза быстрее", 2, 1, false));

        registry.register(new CustomFurnaceItem(plugin, "threeFurnace", "Печка дупера",
                "Эффективность: &#F8BEFBВ 2 раза больше руды", 1, 2, false));

        registry.register(new CustomFurnaceItem(plugin, "fourFurnace", "Печка экономиста",
                "Экономия: &#F8BEFBВ 2 раза меньше топлива", 2, 3, false));
    }

    private static void registerBrewingStands(Main plugin, ItemRegistry registry) {
        registry.register(new CustomBrewingItem(plugin, "onebrew", "Быстрая зельеварка",
                "Экономия: &#F8BEFBВ 2 раза меньше топлива", 2, false));

        registry.register(new CustomBrewingItem(plugin, "twobrew", "Скоростная зельеварка",
                "Экономия: &#F8BEFBВ 4 раза меньше топлива", 4, false));
    }
}
