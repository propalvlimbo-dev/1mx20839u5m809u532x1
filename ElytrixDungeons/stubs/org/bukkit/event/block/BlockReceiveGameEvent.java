package org.bukkit.event.block;

import org.bukkit.GameEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class BlockReceiveGameEvent extends BlockEvent implements Cancellable {
    public BlockReceiveGameEvent(GameEvent event, Block block, Entity entity) { super(block); }
    public GameEvent getEvent() { return null; }
    public Entity getEntity() { return null; }
    public void setCancelled(boolean cancel) {}
    public boolean isCancelled() { return false; }
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
