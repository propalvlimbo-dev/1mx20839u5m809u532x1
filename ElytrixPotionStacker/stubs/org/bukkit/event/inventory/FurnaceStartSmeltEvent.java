package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.Event;

public class FurnaceStartSmeltEvent extends Event {
    public Block getBlock() { return null; }
    public int getTotalCookTime() { return 0; }
    public void setTotalCookTime(int cookTime) { }
}
