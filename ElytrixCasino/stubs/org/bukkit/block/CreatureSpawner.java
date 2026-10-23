package org.bukkit.block;

import org.bukkit.entity.EntityType;

public interface CreatureSpawner extends BlockState {
    EntityType getSpawnedType();
    void setSpawnedType(EntityType type);
    int getDelay();
    void setDelay(int delay);
}
