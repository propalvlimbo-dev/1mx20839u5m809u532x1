package org.bukkit.event.world;

import org.bukkit.World;
import org.bukkit.event.Event;

public class WorldUnloadEvent extends Event {
    public World getWorld() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
