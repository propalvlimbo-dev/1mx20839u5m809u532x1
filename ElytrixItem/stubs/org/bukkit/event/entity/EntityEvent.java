package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
public class EntityEvent extends Event {
    public org.bukkit.entity.EntityType getEntityType() { return null; }

    private final Entity entity;
    public EntityEvent(Entity entity) { this.entity = entity; }
    public Entity getEntity() { return entity; }
}
