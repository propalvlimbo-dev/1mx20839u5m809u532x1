package org.bukkit.event.entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
public class EntityShootBowEvent extends EntityEvent implements org.bukkit.event.Cancellable {
    public EntityShootBowEvent(LivingEntity entity, ItemStack bow, ItemStack consumable, Entity projectile, float force) { super(entity); }
    public LivingEntity getEntity() { return null; }
    public ItemStack getBow() { return null; }
    public ItemStack getConsumable() { return null; }
    public Entity getProjectile() { return null; }
    public void setProjectile(Entity projectile) { }
    public float getForce() { return 0F; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
