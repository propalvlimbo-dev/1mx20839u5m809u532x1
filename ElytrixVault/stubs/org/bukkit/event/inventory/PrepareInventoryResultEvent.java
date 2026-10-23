package org.bukkit.event.inventory;

import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class PrepareInventoryResultEvent extends InventoryEvent {
    public PrepareInventoryResultEvent(InventoryView inventory, ItemStack result) { super(null); }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) {}
}
