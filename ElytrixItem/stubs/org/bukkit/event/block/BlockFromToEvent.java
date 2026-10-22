package org.bukkit.event.block;
import org.bukkit.block.Block;
public class BlockFromToEvent extends BlockEvent {
    public BlockFromToEvent(Block block) { super(block); }
    public Block getToBlock() { return null; }
    public Block getSource() { return null; }
    public org.bukkit.block.BlockState getNewState() { return null; }
}
