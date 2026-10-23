package org.bukkit.event.inventory;
import org.bukkit.inventory.Inventory;
public class InventoryOpenEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
    public InventoryOpenEvent(Inventory inventory) { super(inventory); }
    public org.bukkit.entity.HumanEntity getPlayer() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
