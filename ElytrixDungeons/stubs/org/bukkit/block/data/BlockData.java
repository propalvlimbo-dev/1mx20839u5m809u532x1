package org.bukkit.block.data;
import org.bukkit.Material;
public interface BlockData extends Cloneable {
    Material getMaterial();
    String getAsString();
    String getAsString(boolean hideUnspecified);
    BlockData clone();
}
