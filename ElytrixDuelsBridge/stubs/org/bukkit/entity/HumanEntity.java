package org.bukkit.entity;
import org.bukkit.inventory.InventoryView;
public interface HumanEntity {
    String getName();
    java.util.UUID getUniqueId();
    org.bukkit.inventory.PlayerInventory getInventory();
    InventoryView getOpenInventory();
    InventoryView openInventory(org.bukkit.inventory.Inventory inventory);
    void closeInventory();
}
