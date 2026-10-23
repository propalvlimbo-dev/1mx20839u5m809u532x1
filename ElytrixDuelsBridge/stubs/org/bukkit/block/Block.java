package org.bukkit.block;
import org.bukkit.World;
public interface Block {
    World getWorld();
    int getX();
    int getY();
    int getZ();
}
