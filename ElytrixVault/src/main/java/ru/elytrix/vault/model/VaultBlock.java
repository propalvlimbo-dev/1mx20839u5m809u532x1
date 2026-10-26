package ru.elytrix.vault.model;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class VaultBlock {

    private final String id;
    private final BlockPosKey pos;
    private UUID hologramUuid;

    public VaultBlock(String id, BlockPosKey pos) {
        this.id = id;
        this.pos = pos;
    }

    public String getId() {
        return id;
    }

    public BlockPosKey getPos() {
        return pos;
    }

    public UUID getHologramUuid() {
        return hologramUuid;
    }

    public void setHologramUuid(UUID hologramUuid) {
        this.hologramUuid = hologramUuid;
    }

    public World getWorld() {
        return Bukkit.getWorld(pos.getWorldName());
    }

    public Block getBlock() {
        World world = getWorld();
        return world == null ? null : world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
    }

    public Location getCenterLocation() {
        World world = getWorld();
        return world == null ? null : new Location(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
