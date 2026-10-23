package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
public class PlayerEvent extends Event {
    protected PlayerEvent() { }
    protected PlayerEvent(Player player) { }
    public Player getPlayer() { return null; }
}
