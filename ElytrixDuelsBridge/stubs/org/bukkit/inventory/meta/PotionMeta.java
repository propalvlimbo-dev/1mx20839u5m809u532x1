package org.bukkit.inventory.meta;

import org.bukkit.potion.PotionEffect;

/** Заглушка PotionMeta — нужна только для компиляции. */
public interface PotionMeta extends ItemMeta {
    boolean addCustomEffect(PotionEffect effect, boolean overwrite);
    boolean clearCustomEffects();
}
