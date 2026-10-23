package org.bukkit;
public interface Chunk {
    org.bukkit.entity.Entity[] getEntities();
    int getX();
    int getZ();
    org.bukkit.World getWorld();
    boolean load();
    boolean unload();
    org.bukkit.block.Block getBlock(int x, int y, int z);
    org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer();
}
