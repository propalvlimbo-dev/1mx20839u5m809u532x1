package ru.elytrix.vault.model;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;

public final class BlockPosKey {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public BlockPosKey(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPosKey of(Block block) {
        return new BlockPosKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockPosKey of(Location loc) {
        return new BlockPosKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockPosKey that)) return false;
        return x == that.x && y == that.y && z == that.z && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z);
    }
}
