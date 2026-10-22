package com.sk89q.worldedit.math;
public class BlockVector3 {
    public int getX() { return 0; }
    public int getY() { return 0; }
    public int getZ() { return 0; }
    public BlockVector3 add(int x, int y, int z) { return this; }
    public static BlockVector3 at(int x, int y, int z) { return new BlockVector3(); }
}
