package org.bukkit.event.player;
import org.bukkit.entity.Player;
public class PlayerCommandPreprocessEvent {
    public Player getPlayer() { return null; }
    public String getMessage() { return ""; }
    public void setMessage(String message) { }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
