package org.bukkit.entity;
public interface AbstractArrow extends Projectile {
    double getDamage();
    void setDamage(double damage);
    int getPierceLevel();
    void setPierceLevel(int level);
    void setPickupStatus(org.bukkit.entity.Player player);
    boolean isCritical();
    void setCritical(boolean critical);
}
