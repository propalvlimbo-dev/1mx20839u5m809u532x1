package org.bukkit.event.inventory;

import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class PrepareGrindstoneEvent extends InventoryEvent {
    public PrepareGrindstoneEvent(InventoryView view) { super(null); }
    public void setResult(ItemStack result) {}
    public ItemStack getResult() { return null; }
}
