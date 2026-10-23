package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class BlockFadeEvent extends BlockEvent implements Cancellable {
    public BlockFadeEvent(Block block, BlockState newState) { super(block); }
    public BlockState getNewState() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
