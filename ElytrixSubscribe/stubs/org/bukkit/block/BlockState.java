package org.bukkit.block;
import org.bukkit.block.data.BlockData;
public interface BlockState {
    Block getBlock();
    BlockData getBlockData();
    void update();
    void update(boolean force, boolean applyPhysics);
}
