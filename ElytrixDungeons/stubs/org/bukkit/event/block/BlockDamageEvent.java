package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class BlockDamageEvent extends BlockEvent implements Cancellable {
    private final Player player;
    private boolean cancelled;
    private boolean instaBreak;

    public BlockDamageEvent(Player player, Block block) {
        super(block);
        this.player = player;
    }

    public Player getPlayer() { return player; }
    public boolean getInstaBreak() { return instaBreak; }
    public void setInstaBreak(boolean instaBreak) { this.instaBreak = instaBreak; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
