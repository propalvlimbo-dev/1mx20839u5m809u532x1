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

import java.util.List;

/**
 * Зелье или стрела с настоящими эффектами в PotionMeta.
 *
 * Базовое зелье — WATER: все эффекты задаются через addCustomEffect и отображаются
 * штатной подсказкой Minecraft. Цвет задаётся так же, как раньше в ExecutableItems.
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

    /** Native potion effect: type, duration in ticks, and amplifier. */
    public static Effect effect(PotionEffectType type, int duration, int amplifier) {
        return new Effect(type, duration, amplifier);
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

            if (material != Material.TIPPED_ARROW) {
                meta.setMaxStackSize(64);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.showAllDetails(item);
        return item;
    }

    public static final class Effect {

        private final PotionEffectType type;
        private final int duration;
        private final int amplifier;

        public Effect(PotionEffectType type, int duration, int amplifier) {
            this.type = type;
            this.duration = duration;
            this.amplifier = amplifier;
        }
    }
}
