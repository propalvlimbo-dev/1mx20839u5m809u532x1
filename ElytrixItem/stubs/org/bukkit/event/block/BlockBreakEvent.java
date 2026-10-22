package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
public class BlockBreakEvent extends BlockEvent implements org.bukkit.event.Cancellable {
    private boolean cancelled;
    public BlockBreakEvent(Block block, Player player) { super(block); }
    public Player getPlayer() { return null; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    public int getExpToDrop() { return 0; }
    public void setExpToDrop(int exp) { }
    public boolean isDropItems() { return true; }
    public void setDropItems(boolean drop) { }
}
