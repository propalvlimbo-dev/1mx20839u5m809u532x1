package org.bukkit.block;

public interface BrewingStand extends Container {
    int getFuelLevel();
    void setFuelLevel(int level);
}
