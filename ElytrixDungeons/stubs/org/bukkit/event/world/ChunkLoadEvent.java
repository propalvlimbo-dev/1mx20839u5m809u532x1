package org.bukkit.event.world;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ChunkLoadEvent extends Event {
    public Chunk getChunk() { return null; }
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
