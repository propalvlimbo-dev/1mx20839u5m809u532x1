package org.bukkit.entity;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
public interface LivingEntity extends Entity {
    double getHealth();
    void setHealth(double health);
    AttributeInstance getAttribute(Attribute attribute);
    void setNoGravity(boolean gravity);
    boolean isDead();
}
