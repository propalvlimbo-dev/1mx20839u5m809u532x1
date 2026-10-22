package org.bukkit;
public interface World {
    String getName();
    java.util.List<org.bukkit.entity.Player> getPlayers();
    <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> type);
    java.util.UUID getUID();
    Location getSpawnLocation();
}
