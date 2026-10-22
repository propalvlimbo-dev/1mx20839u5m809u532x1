package org.bukkit;

public enum Particle {
    CLOUD,
    DUST,
    EXPLOSION,
    EXPLOSION_EMITTER,
    FIREWORK,
    LARGE_SMOKE,
    WITCH;

    public static final class DustOptions {
        public DustOptions(Color color, float size) { }
        public Color getColor() { return null; }
        public float getSize() { return 0F; }
    }
}
