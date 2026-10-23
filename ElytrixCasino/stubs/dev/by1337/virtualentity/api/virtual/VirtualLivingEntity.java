package dev.by1337.virtualentity.api.virtual;

import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public interface VirtualLivingEntity extends VirtualEntity {
    void setEquipment(EquipmentSlot slot, ItemStack item);
}
