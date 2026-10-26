package org.bukkit.event;
public abstract class Event {
    public void setCancelled(boolean cancel) { }
    public boolean isCancelled() { return false; }
}
