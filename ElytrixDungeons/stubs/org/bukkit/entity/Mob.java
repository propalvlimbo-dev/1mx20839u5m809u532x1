package org.bukkit.entity;
public interface Mob extends LivingEntity {
    void setTarget(LivingEntity target);
    LivingEntity getTarget();
}
