package org.bukkit.event.inventory;
import org.bukkit.inventory.Inventory;
public class InventoryCloseEvent extends InventoryEvent {
    public InventoryCloseEvent(Inventory inventory) { super(inventory); }
    public org.bukkit.entity.HumanEntity getPlayer() { return null; }
}
