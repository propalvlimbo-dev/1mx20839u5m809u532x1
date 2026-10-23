package org.bukkit.event.block;

import org.bukkit.block.Block;

public class BrewingStartEvent extends BlockEvent {
    public BrewingStartEvent(Block block) { super(block); }
    public int getBrewingTime() { return 400; }
    public void setBrewingTime(int brewTime) { }
}
