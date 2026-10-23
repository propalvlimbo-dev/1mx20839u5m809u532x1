package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

public class FurnaceBurnEvent extends Event {
    public Block getBlock() { return null; }
    public ItemStack getFuel() { return null; }
    public int getBurnTime() { return 0; }
    public void setBurnTime(int burnTime) { }
    public void setBurning(boolean burning) { }
}
