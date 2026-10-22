package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldedit.math.BlockVector3;
public abstract class ProtectedRegion {
    public String getId() { return ""; }
    public BlockVector3 getMinimumPoint() { return BlockVector3.ZERO; }
    public BlockVector3 getMaximumPoint() { return BlockVector3.ZERO; }
}
