package org.bukkit.event.entity;
import org.bukkit.entity.Item;
public class ItemSpawnEvent extends EntityEvent {
    public ItemSpawnEvent(Item entity) { super(entity); }
    public Item getEntity() { return null; }
}
