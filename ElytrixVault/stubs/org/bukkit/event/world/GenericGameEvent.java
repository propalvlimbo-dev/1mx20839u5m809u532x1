package org.bukkit.event.world;

import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GenericGameEvent extends Event implements Cancellable {
    public GameEvent getEvent() { return null; }
    public Entity getEntity() { return null; }
    public Location getLocation() { return null; }
    public int getRadius() { return 0; }
    public void setCancelled(boolean cancel) {}
    public boolean isCancelled() { return false; }
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
