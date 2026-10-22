package io.papermc.paper.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
public class AsyncChatEvent extends Event implements Cancellable {
    private boolean cancelled;
    public Player getPlayer() { return null; }
    public net.kyori.adventure.text.Component message() { return null; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    public boolean isCancelled() { return this.cancelled; }
}
