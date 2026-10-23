package org.bukkit.block.data.type;
import org.bukkit.block.data.BlockData;
public interface RespawnAnchor extends BlockData {
    int getCharges();
    void setCharges(int charges);
    int getMaximumCharges();
}
