package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerSwapHandItemsEvent extends PlayerEvent implements Cancellable {
    public PlayerSwapHandItemsEvent(Player player) { super(player); }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
