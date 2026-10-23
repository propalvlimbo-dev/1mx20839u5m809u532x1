package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
public class ProjectileHitEvent extends EntityEvent implements org.bukkit.event.Cancellable {
    public ProjectileHitEvent(Entity entity) { super(entity); }
    public Entity getHitEntity() { return null; }
    public org.bukkit.block.Block getHitBlock() { return null; }
    public org.bukkit.block.BlockFace getHitBlockFace() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
    public org.bukkit.entity.Projectile getEntity() { return null; }
}
