package org.bukkit;
public interface World {
    String getName();
    java.util.List<org.bukkit.entity.Player> getPlayers();
    <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> type);
    org.bukkit.block.Block getBlockAt(int x, int y, int z);
    int getMaxHeight();
    int getMinHeight();
    java.util.UUID getUID();
    Location getSpawnLocation();
}
