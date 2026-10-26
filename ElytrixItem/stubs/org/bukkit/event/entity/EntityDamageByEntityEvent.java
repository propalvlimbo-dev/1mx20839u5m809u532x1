package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
public class EntityDamageByEntityEvent extends EntityDamageEvent {
    public EntityDamageByEntityEvent(Entity damager, Entity damagee, DamageCause cause, double damage) {
        super(damagee, cause, damage);
    }
    public Entity getDamager() { return null; }
}
