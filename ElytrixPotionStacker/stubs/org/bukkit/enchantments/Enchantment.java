package org.bukkit.enchantments;

public interface Enchantment {
    String getKey();
    String getName();
    int getMaxLevel();
    boolean isCursed();
    static Enchantment getByKey(org.bukkit.NamespacedKey key) { return null; }
    static Enchantment getByName(String name) { return null; }

    Enchantment PROTECTION = null;
    Enchantment FIRE_PROTECTION = null;
    Enchantment FEATHER_FALLING = null;
    Enchantment BLAST_PROTECTION = null;
    Enchantment PROJECTILE_PROTECTION = null;
    Enchantment RESPIRATION = null;
    Enchantment AQUA_AFFINITY = null;
    Enchantment THORNS = null;
    Enchantment DEPTH_STRIDER = null;
    Enchantment FROST_WALKER = null;
    Enchantment BINDING_CURSE = null;
    Enchantment SHARPNESS = null;
    Enchantment SMITE = null;
    Enchantment BANE_OF_ARTHROPODS = null;
    Enchantment KNOCKBACK = null;
    Enchantment FIRE_ASPECT = null;
    Enchantment LOOTING = null;
    Enchantment SWEEPING_EDGE = null;
    Enchantment EFFICIENCY = null;
    Enchantment SILK_TOUCH = null;
    Enchantment UNBREAKING = null;
    Enchantment FORTUNE = null;
    Enchantment POWER = null;
    Enchantment PUNCH = null;
    Enchantment FLAME = null;
    Enchantment INFINITY = null;
    Enchantment LUCK_OF_THE_SEA = null;
    Enchantment LURE = null;
    Enchantment LOYALTY = null;
    Enchantment IMPALING = null;
    Enchantment RIPTIDE = null;
    Enchantment CHANNELING = null;
    Enchantment MULTISHOT = null;
    Enchantment QUICK_CHARGE = null;
    Enchantment PIERCING = null;
    Enchantment MENDING = null;
    Enchantment VANISHING_CURSE = null;
    Enchantment SOUL_SPEED = null;
    Enchantment SWIFT_SNEAK = null;
    Enchantment DENSITY = null;
    Enchantment BREACH = null;
    Enchantment WIND_BURST = null;
}
