package org.bukkit.potion;

public class PotionEffect {
    public static final int INFINITE_DURATION = -1;

    public PotionEffect(PotionEffectType type, int duration, int amplifier) { }
    public PotionEffect(PotionEffectType type, int duration, int amplifier,
                        boolean ambient, boolean particles, boolean icon) { }

    public PotionEffectType getType() { return null; }
    public int getAmplifier() { return 0; }
    public int getDuration() { return 0; }
    public boolean hasParticles() { return true; }
    public boolean isAmbient() { return false; }
    public boolean hasIcon() { return true; }
    public boolean isInfinite() { return getDuration() == INFINITE_DURATION; }
}
