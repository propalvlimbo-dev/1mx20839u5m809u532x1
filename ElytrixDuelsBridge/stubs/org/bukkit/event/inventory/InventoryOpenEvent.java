package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
public class InventoryOpenEvent extends InventoryEvent {
    public HumanEntity getPlayer() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
