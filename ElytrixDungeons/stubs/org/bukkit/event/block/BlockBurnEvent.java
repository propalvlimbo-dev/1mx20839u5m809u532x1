package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class BlockBurnEvent extends BlockEvent implements Cancellable {
    public BlockBurnEvent(Block block, Block ignitingBlock) { super(block); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
