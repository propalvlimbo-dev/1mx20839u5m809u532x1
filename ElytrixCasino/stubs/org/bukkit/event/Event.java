package org.bukkit.event;
public abstract class Event {
    public enum Result { DEFAULT, ALLOW, DENY }
    public void setCancelled(boolean cancel) { }
    public boolean isCancelled() { return false; }
    public String getEventName() { return ""; }
}
