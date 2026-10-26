package org.bukkit.inventory.meta;

import org.bukkit.block.BlockState;

public interface BlockStateMeta extends ItemMeta {
    BlockState getBlockState();
    void setBlockState(BlockState state);
}
