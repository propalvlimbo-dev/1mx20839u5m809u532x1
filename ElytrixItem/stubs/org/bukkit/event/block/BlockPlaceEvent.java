package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
public class BlockPlaceEvent extends org.bukkit.event.Event implements Cancellable {
    public Block getBlock() { return null; }
    public Player getPlayer() { return null; }
    public org.bukkit.inventory.ItemStack getItemInHand() { return null; }
}
