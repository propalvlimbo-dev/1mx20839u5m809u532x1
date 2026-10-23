package org.bukkit.block.data;
import org.bukkit.Material;
public interface BlockData {
    Material getMaterial();
    BlockData clone();
}
