package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class PlayerAnimationEvent extends PlayerEvent implements Cancellable {
    public PlayerAnimationEvent(Player who) { super(who); }
    public PlayerAnimationType getAnimationType() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
}
