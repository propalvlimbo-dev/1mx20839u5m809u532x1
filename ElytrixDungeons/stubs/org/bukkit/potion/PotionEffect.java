package org.bukkit.potion;
public class PotionEffect {
    public PotionEffect(PotionEffectType type, int duration, int amplifier) {}
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient) {}
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles) {}
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {}
    public PotionEffectType getType() { return null; }
    public int getDuration() { return 0; }
    public int getAmplifier() { return 0; }
}
