package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.inventory.InventoryHolder;

public interface Allay extends Creature, InventoryHolder {
    boolean canDuplicate();
    void setCanDuplicate(boolean canDuplicate);
    long getDuplicationCooldown();
    void setDuplicationCooldown(long cooldown);
    void resetDuplicationCooldown();
    boolean isDancing();
    void startDancing(Location location);
    void startDancing();
    void stopDancing();
    Allay duplicateAllay();
    Location getJukebox();
}
