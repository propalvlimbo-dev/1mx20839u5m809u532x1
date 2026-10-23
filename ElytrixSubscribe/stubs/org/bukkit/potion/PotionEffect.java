package org.bukkit.potion;

/** Заглушка PotionEffect — нужна только для компиляции. */
public class PotionEffect {

    public static final int INFINITE_DURATION = -1;

    public PotionEffect(PotionEffectType type, int duration, int amplifier) {
    }

    public PotionEffect(PotionEffectType type, int duration, int amplifier,
                        boolean ambient, boolean particles, boolean icon) {
    }

    public PotionEffectType getType() {
        return null;
    }
}
