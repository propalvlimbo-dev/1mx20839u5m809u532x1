package org.bukkit.event.player;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class PlayerBucketEmptyEvent extends PlayerEvent implements Cancellable {
    public PlayerBucketEmptyEvent(Player who) { super(who); }
    public Block getBlock() { return null; }
    public Block getBlockClicked() { return null; }
    public BlockFace getBlockFace() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) {}
    public HandlerList getHandlers() { return null; }
    public static HandlerList getHandlerList() { return null; }
}
