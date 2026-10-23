package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Зелье или стрела с эффектами.
 *
 * Базовое зелье — WATER: своих эффектов у него нет, все эффекты накладываются
 * при использовании и перечислены в лоре. Цвет задаётся так же, как раньше
 * в ExecutableItems (potionColor).
 */
public final class BrewItem implements CustomItem {

    private final NamespacedKey key;
    private final String id;
    private final Material material;
    private final String name;
    private final int rgb;
    private final List<Effect> effects;

    public BrewItem(Main plugin, String id, Material material, String name, int rgb, Effect... effects) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.material = material;
        this.name = name;
        this.rgb = rgb;
        this.effects = List.of(effects);
    }

    /** Описание одного эффекта: тип, длительность в тиках, усилитель и русское название. */
    public static Effect effect(PotionEffectType type, int duration, int amplifier, String label) {
        return new Effect(type, duration, amplifier, label);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta raw = item.getItemMeta();

        if (raw instanceof PotionMeta meta) {
            meta.setBasePotionType(PotionType.WATER);
            meta.setColor(Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));

            for (Effect effect : effects) {
                meta.addCustomEffect(new PotionEffect(effect.type, effect.duration, effect.amplifier), true);
            }

            meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            for (Effect effect : effects) {
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &f" + effect.label + " "
                        + roman(effect.amplifier + 1) + " &7(" + time(effect.duration) + ")"));
            }
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.hideAllFlags(item);
        return item;
    }

    private static String time(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private static String roman(int value) {
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (value >= 1 && value <= 10) {
            return ones[value];
        }
        return String.valueOf(value);
    }

    public static final class Effect {

        private final PotionEffectType type;
        private final int duration;
        private final int amplifier;
        private final String label;

        public Effect(PotionEffectType type, int duration, int amplifier, String label) {
            this.type = type;
            this.duration = duration;
            this.amplifier = amplifier;
            this.label = label;
        }
    }
}
