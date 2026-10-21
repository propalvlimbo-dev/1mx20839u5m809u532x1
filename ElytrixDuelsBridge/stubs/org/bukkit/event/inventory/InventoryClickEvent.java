package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
public class InventoryClickEvent {
    public Inventory getClickedInventory() { return null; }
    public int getSlot() { return 0; }
    public HumanEntity getWhoClicked() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
