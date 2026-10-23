package org.bukkit.entity;
import org.bukkit.Location;
public interface Entity {
    Location getLocation();
    java.util.UUID getUniqueId();
    org.bukkit.World getWorld();
    boolean isDead();
    void remove();
    boolean teleport(Location location);
    boolean isValid();
}
