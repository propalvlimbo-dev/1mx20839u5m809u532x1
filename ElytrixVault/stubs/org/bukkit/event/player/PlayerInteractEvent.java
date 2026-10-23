package org.bukkit.event.player;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
public class PlayerInteractEvent extends PlayerEvent implements org.bukkit.event.Cancellable {
    private boolean cancelled;
    public PlayerInteractEvent(Player player, Action action, ItemStack item, Block clickedBlock, org.bukkit.block.BlockFace face) { super(player); }
    public Action getAction() { return null; }
    public ItemStack getItem() { return null; }
    public Block getClickedBlock() { return null; }
    public org.bukkit.block.BlockFace getBlockFace() { return null; }
    public EquipmentSlot getHand() { return null; }
    public void setUseItemInHand(org.bukkit.event.Event.Result result) { }
    public boolean useItemInHand() { return true; }
    public void setUseInteractedBlock(org.bukkit.event.Event.Result result) { }
    public boolean useInteractedBlock() { return true; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
