package org.bukkit.entity;

import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public interface LivingEntity extends Entity {
    double getHealth();
    void setHealth(double health);
    double getMaxHealth();
    AttributeInstance getAttribute(Attribute attribute);
    void setNoGravity(boolean gravity);
    boolean isDead();
    void damage(double amount);
    void damage(double amount, Entity source);
    Player getKiller();
    Location getEyeLocation();
    Vector getVelocity();
    void setVelocity(Vector vector);
    boolean addPotionEffect(PotionEffect effect);
    boolean addPotionEffect(PotionEffect effect, boolean force);
    PotionEffect getPotionEffect(PotionEffectType type);
    void removePotionEffect(PotionEffectType type);
    Collection<PotionEffect> getActivePotionEffects();
    EntityEquipment getEquipment();
    float getFallDistance();
    void setFallDistance(float distance);
    boolean isGliding();
    void setGliding(boolean gliding);
    boolean isSneaking();
    int getNoDamageTicks();
    void setNoDamageTicks(int ticks);
    void setAI(boolean ai);
    boolean hasAI();
    void setCollidable(boolean collidable);
    boolean isCollidable();
}
