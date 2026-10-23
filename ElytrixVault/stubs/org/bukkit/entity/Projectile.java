package org.bukkit.entity;
public interface Projectile extends Entity {
    org.bukkit.projectiles.ProjectileSource getShooter();
    void setShooter(org.bukkit.projectiles.ProjectileSource shooter);
    boolean doesBounce();
    void setBounce(boolean bounce);
}
