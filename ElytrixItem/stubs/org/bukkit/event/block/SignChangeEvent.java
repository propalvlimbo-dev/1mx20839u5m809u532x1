package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
public class SignChangeEvent extends Event implements Cancellable {
    private boolean cancelled;
    public Player getPlayer() { return null; }
    public Block getBlock() { return null; }
    public String[] getLines() { return new String[4]; }
    public String getLine(int index) { return ""; }
    public void setLine(int index, String line) { }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    public boolean isCancelled() { return this.cancelled; }
}
