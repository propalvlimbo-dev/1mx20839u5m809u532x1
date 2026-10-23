package org.bukkit;

public enum Particle {
    CLOUD,
    DUST,
    EXPLOSION,
    EXPLOSION_EMITTER,
    FIREWORK,
    LARGE_SMOKE,
    WITCH,
    BLOCK,
    BLOCK_CRUMBLE,
    CRIT,
    END_ROD,
    SOUL_FIRE_FLAME,
    SOUL,
    ELECTRIC_SPARK,
    FLASH,
    SWEEP_ATTACK,
    TOTEM_OF_UNDYING,
    SMOKE,
    POOF,
    ENCHANT,
    GLOW,
    SCULK_SOUL;

    public static final class DustOptions {
        public DustOptions(Color color, float size) { }
        public Color getColor() { return null; }
        public float getSize() { return 0F; }
    }
}
