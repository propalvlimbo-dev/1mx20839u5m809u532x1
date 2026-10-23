package org.bukkit.block;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;

public interface TileState extends BlockState, PersistentDataHolder {
    PersistentDataContainer getPersistentDataContainer();
}
