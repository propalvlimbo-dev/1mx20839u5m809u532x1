package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.Event;

public class BrewingStandFuelEvent extends Event {
    public Block getBlock() { return null; }
    public int getFuelPower() { return 0; }
    public void setFuelPower(int fuelPower) { }
    public boolean isConsuming() { return true; }
    public void setConsuming(boolean consuming) { }
}
