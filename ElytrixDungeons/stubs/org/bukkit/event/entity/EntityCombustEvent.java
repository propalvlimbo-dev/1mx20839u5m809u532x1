package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
public class EntityCombustEvent extends EntityEvent implements Cancellable {
    public EntityCombustEvent(Entity combustee, float duration) { super(combustee); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
