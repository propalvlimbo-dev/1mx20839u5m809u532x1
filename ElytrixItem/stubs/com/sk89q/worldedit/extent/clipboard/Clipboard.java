package com.sk89q.worldedit.extent.clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
public interface Clipboard {
    BlockVector3 getOrigin();
    BlockVector3 getMinimumPoint();
    BlockVector3 getMaximumPoint();
    Iterable<BlockVector3> getRegion();
    BlockState getBlock(BlockVector3 position);
}
