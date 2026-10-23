package org.bukkit.event.inventory;

import org.bukkit.inventory.Inventory;

public class InventoryCreativeEvent extends InventoryClickEvent {
    public InventoryCreativeEvent(Inventory inventory) { super(inventory); }
}
