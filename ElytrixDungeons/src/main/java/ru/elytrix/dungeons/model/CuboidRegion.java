package ru.elytrix.dungeons.model;

import org.bukkit.Location;

public record CuboidRegion(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public CuboidRegion normalized() {
        return new CuboidRegion(
                worldName,
                Math.min(minX, maxX),
                Math.min(minY, maxY),
                Math.min(minZ, maxZ),
                Math.max(minX, maxX),
                Math.max(minY, maxY),
                Math.max(minZ, maxZ)
        );
    }

    public CuboidRegion expand(int horizontal, int vertical) {
        return new CuboidRegion(
                worldName,
                minX - horizontal,
                minY - vertical,
                minZ - horizontal,
                maxX + horizontal,
                maxY + vertical,
                maxZ + horizontal
        );
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean containsBlock(String world, int x, int y, int z) {
        return worldName.equals(world)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean intersects2D(String world, int x1, int z1, int x2, int z2) {
        if (!worldName.equals(world)) {
            return false;
        }
        int ax1 = Math.min(x1, x2), ax2 = Math.max(x1, x2);
        int az1 = Math.min(z1, z2), az2 = Math.max(z1, z2);
        return ax1 <= maxX && ax2 >= minX && az1 <= maxZ && az2 >= minZ;
    }

    public boolean intersects3D(CuboidRegion other) {
        return worldName.equals(other.worldName)
                && minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }
}
