package org.bukkit.inventory.meta;

import org.bukkit.Color;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/** Заглушка PotionMeta — нужна только для компиляции. */
public interface PotionMeta extends ItemMeta {
    void setBasePotionType(PotionType type);
    void setColor(Color color);
    boolean addCustomEffect(PotionEffect effect, boolean overwrite);
    boolean clearCustomEffects();
}
