package dev.by1337.virtualentity.api.tracker;

import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import org.bukkit.World;
import org.by1337.blib.geom.Vec3d;

import java.util.List;

public class PlayerTracker {
    public PlayerTracker(World world, List<VirtualEntity> entities, Vec3d pos, int viewDistance) {}
    public void addEntity(VirtualEntity entity) {}
    public void removeEntity(VirtualEntity entity) {}
    public void removeAll() {}
    public void tick() {}
}
