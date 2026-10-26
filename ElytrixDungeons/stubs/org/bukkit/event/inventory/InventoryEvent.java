package org.bukkit.event.inventory;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
public class InventoryEvent extends Event {
    private final Inventory inventory;
    public InventoryEvent(Inventory inventory) { this.inventory = inventory; }
    public Inventory getInventory() { return inventory; }
    public InventoryView getView() { return null; }
}
