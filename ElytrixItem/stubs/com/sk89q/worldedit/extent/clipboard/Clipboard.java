package com.sk89q.worldedit.extent.clipboard;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
public interface Clipboard extends Extent {
    Region getRegion();
    BlockVector3 getOrigin();
}
