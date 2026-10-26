package org.bukkit.damage;

public interface DamageSource {
    static Builder builder(DamageType type) { return null; }

    interface Builder {
        DamageSource build();
    }
}
