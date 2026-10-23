package org.bukkit.event.entity;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
public class EntityChangeBlockEvent extends EntityEvent implements org.bukkit.event.Cancellable {
    public EntityChangeBlockEvent(Entity entity, Block block, org.bukkit.Material to) { super(entity); }
    public Block getBlock() { return null; }
    public org.bukkit.Material getTo() { return null; }
    public org.bukkit.block.data.BlockData getBlockData() { return null; }
    public boolean isCancelled() { return false; }
    public void setCancelled(boolean cancel) { }
}
