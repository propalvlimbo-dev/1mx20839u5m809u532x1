package org.bukkit.event.inventory;

import java.util.Collections;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

public class BrewEvent extends BlockEvent implements Cancellable {
    public BrewEvent(Block block) { super(block); }
    public BrewerInventory getContents() { return null; }
    public List<ItemStack> getResults() { return Collections.emptyList(); }
    public int getFuelLevel() { return 0; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
