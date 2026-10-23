package org.bukkit.event.block;
import org.bukkit.block.Block;
public class BlockPistonRetractEvent extends BlockEvent {
    public BlockPistonRetractEvent(Block block) { super(block); }
    public java.util.List<Block> getBlocks() { return new java.util.ArrayList<>(); }
    public org.bukkit.block.BlockFace getDirection() { return null; }
    public boolean isSticky() { return false; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
