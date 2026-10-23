package org.bukkit.block;

import org.bukkit.inventory.Inventory;

public interface Container extends TileState {
    Inventory getInventory();
    Inventory getSnapshotInventory();
}
