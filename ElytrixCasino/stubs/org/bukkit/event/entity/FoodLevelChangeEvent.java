package org.bukkit.event.entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
public class FoodLevelChangeEvent extends Event implements Cancellable {
    private boolean cancelled;
    public HumanEntity getEntity() { return null; }
    public int getFoodLevel() { return 20; }
    public void setFoodLevel(int level) { }
    public boolean isCancelled() { return this.cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
