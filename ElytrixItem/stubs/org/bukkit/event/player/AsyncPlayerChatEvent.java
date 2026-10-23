package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
public class AsyncPlayerChatEvent extends PlayerEvent implements Cancellable {
    private boolean cancelled;
    private String message = "";
    public String getMessage() { return this.message; }
    public void setMessage(String message) { this.message = message; }
    public Player getPlayer() { return null; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    public boolean isCancelled() { return this.cancelled; }
}
