package org.bukkit.event.entity;

import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class EntityPickupItemEvent extends EntityEvent implements Cancellable {
    public EntityPickupItemEvent(LivingEntity entity, Item item, int remaining) { super(entity); }
    public LivingEntity getEntity() { return null; }
    public Item getItem() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
