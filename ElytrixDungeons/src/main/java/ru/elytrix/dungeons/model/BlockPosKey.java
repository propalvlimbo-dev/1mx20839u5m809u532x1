package ru.elytrix.dungeons.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record BlockPosKey(String worldName, int x, int y, int z) {

    public static BlockPosKey of(Block b) {
        return new BlockPosKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public static BlockPosKey of(Location loc) {
        return new BlockPosKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public BlockPosKey add(int dx, int dy, int dz) {
        return new BlockPosKey(worldName, x + dx, y + dy, z + dz);
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public Location centerLocation() {
        World w = world();
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public Location floorLocation() {
        World w = world();
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    public String serialize() {
        return worldName + ";" + x + ";" + y + ";" + z;
    }

    public static BlockPosKey deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new BlockPosKey(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
