package org.bukkit.entity;

import org.bukkit.block.BlockFace;

public interface Hanging extends Entity {
    BlockFace getAttachedFace();
}
