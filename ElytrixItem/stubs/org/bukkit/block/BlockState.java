package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

public interface BlockState {
    World getWorld();
    int getX();
    int getY();
    int getZ();
    Block getBlock();
    Material getType();
    void setType(Material type);
    BlockData getBlockData();
    void setBlockData(BlockData data);
    boolean update(boolean force, boolean applyPhysics);
    boolean update(boolean force);
    boolean update();
}
