package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
public class ProjectileLaunchEvent extends EntityEvent implements org.bukkit.event.Cancellable {
    public ProjectileLaunchEvent(Entity entity) { super(entity); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
    public org.bukkit.entity.Projectile getEntity() { return null; }
}
