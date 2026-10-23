package ru.rooyzee.elytrixcasino.miner;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixcasino.Main;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Менеджер всех пакетных NPC «Минёр» на сервере.
 */
public final class MinerManager {

    private final Main plugin;
    private final Map<String, Miner> miners = new LinkedHashMap<>();

    public MinerManager(Main plugin) {
        this.plugin = plugin;
    }

    public Miner create(Player creator) {
        String id = plugin.getDataManager().newId();
        Location loc = creator.getLocation().clone();
        loc.setPitch(0.0F);
        Miner miner = spawn(id, loc);
        if (miner != null) {
            plugin.getDataManager().saveMiner(id, loc);
        }
        return miner;
    }

    public Miner spawn(String id, Location location) {
        Miner existing = miners.remove(id);
        if (existing != null) {
            existing.despawn();
        }
        Miner miner = new Miner(plugin, id, location);
        if (!miner.spawn()) {
            return null;
        }
        miners.put(id, miner);
        return miner;
    }

    public boolean remove(String id) {
        Miner removed = miners.remove(id);
        if (removed == null) {
            return false;
        }
        removed.despawn();
        plugin.getDataManager().removeMiner(id);
        return true;
    }

    public void unload(String id) {
        Miner removed = miners.remove(id);
        if (removed != null) {
            removed.despawn();
        }
    }

    public void removeAll() {
        for (Miner miner : miners.values()) {
            miner.despawn();
        }
        miners.clear();
    }

    public Miner get(String id) {
        return miners.get(id);
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(miners.keySet());
    }

    public Collection<Miner> getAll() {
        return Collections.unmodifiableCollection(miners.values());
    }

    public Miner findByEntityId(int entityId) {
        if (entityId < 0) {
            return null;
        }
        for (Miner miner : miners.values()) {
            if (miner.isSpawned() && miner.getNpcEntityId() == entityId) {
                return miner;
            }
        }
        return null;
    }

    public Miner findLookingAt(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        Vector start = eye.toVector();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < 1.0E-6D) {
            return null;
        }
        Miner best = null;
        double bestDistSq = maxDistance * maxDistance;
        for (Miner miner : miners.values()) {
            if (!miner.isSpawned()) {
                continue;
            }
            Location loc = miner.getLocation();
            if (!world.equals(loc.getWorld())) {
                continue;
            }
            Location boxCenter = loc.clone().add(0.0D, 0.92D, 0.0D);
            BoundingBox box = BoundingBox.of(boxCenter, 0.45D, 0.95D, 0.45D);
            RayTraceResult hit = box.rayTrace(start, direction, maxDistance);
            if (hit != null) {
                double distSq = start.distanceSquared(hit.getHitPosition());
                if (distSq <= bestDistSq) {
                    bestDistSq = distSq;
                    best = miner;
                }
            }
        }
        return best;
    }

    public void onChunkLoad(Chunk chunk) {
        World world = chunk.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        for (Miner miner : miners.values()) {
            Location loc = miner.getLocation();
            if (world.equals(loc.getWorld())
                    && (loc.getBlockX() >> 4) == cx
                    && (loc.getBlockZ() >> 4) == cz
                    && !miner.isSpawned()) {
                miner.spawn();
            }
        }
    }

    public void onChunkUnload(Chunk chunk) {
        // Пакетный NPC живёт в памяти и сам управляется PlayerTracker'ом, как Roulette.
    }
}
