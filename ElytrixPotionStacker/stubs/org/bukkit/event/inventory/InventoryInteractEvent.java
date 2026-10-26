package org.bukkit.event.inventory;
import org.bukkit.inventory.Inventory;
public class InventoryInteractEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
    public InventoryInteractEvent(Inventory inventory) { super(inventory); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
    public org.bukkit.entity.HumanEntity getWhoClicked() { return null; }
}
