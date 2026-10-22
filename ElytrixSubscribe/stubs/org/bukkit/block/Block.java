package org.bukkit.block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
public interface Block {
    World getWorld();
    int getX();
    int getY();
    int getZ();
    Location getLocation();
    Material getType();
    void setType(Material type);
    void setType(Material type, boolean applyPhysics);
    BlockData getBlockData();
    void setBlockData(BlockData data);
    void setBlockData(BlockData data, boolean applyPhysics);
    boolean isEmpty();
    BlockState getState();
}
