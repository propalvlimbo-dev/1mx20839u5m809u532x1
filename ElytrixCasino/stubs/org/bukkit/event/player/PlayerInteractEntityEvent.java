package org.bukkit.event.player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.EquipmentSlot;
public class PlayerInteractEntityEvent extends PlayerEvent implements Cancellable {
    private boolean cancelled;
    public PlayerInteractEntityEvent() { }
    public Entity getRightClicked() { return null; }
    public EquipmentSlot getHand() { return EquipmentSlot.HAND; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    public boolean isCancelled() { return this.cancelled; }
}
