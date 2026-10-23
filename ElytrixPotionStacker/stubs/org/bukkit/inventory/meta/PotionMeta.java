package org.bukkit.inventory.meta;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

public interface PotionMeta extends ItemMeta {
    void setBasePotionType(PotionType type);
    PotionType getBasePotionType();
    void setColor(Color color);
    boolean hasCustomEffects();
    List<PotionEffect> getCustomEffects();
    boolean addCustomEffect(PotionEffect effect, boolean overwrite);
    boolean clearCustomEffects();
}
