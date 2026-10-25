package org.bukkit.block;
import org.bukkit.inventory.InventoryHolder;
public interface Container extends TileState, InventoryHolder {
    org.bukkit.inventory.Inventory getSnapshotInventory();
}
