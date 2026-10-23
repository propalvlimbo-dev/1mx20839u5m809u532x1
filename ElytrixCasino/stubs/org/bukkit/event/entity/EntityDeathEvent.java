package org.bukkit.event.entity;
import org.bukkit.entity.LivingEntity;
public class EntityDeathEvent extends EntityEvent {
    public EntityDeathEvent(LivingEntity entity, java.util.List<org.bukkit.inventory.ItemStack> drops) { super(entity); }
    public LivingEntity getEntity() { return null; }
    public java.util.List<org.bukkit.inventory.ItemStack> getDrops() { return new java.util.ArrayList<>(); }
    public int getDroppedExp() { return 0; }
    public void setDroppedExp(int exp) { }
}
