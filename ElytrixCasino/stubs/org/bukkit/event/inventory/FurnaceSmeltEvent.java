package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

public class FurnaceSmeltEvent extends Event {
    public Block getBlock() { return null; }
    public ItemStack getSource() { return null; }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) { }
}
