package ru.rooyzee.elytrixcasino.roulette;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixcasino.Main;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RouletteManager {

    private static final double NPC_HALF_WIDTH = 0.35D;
    private static final double NPC_HALF_HEIGHT = 1.0D;

    private final Main plugin;
    private final Map<String, Roulette> roulettes = new LinkedHashMap<>();
    private final Set<UUID> removeMode = new HashSet<>();

    public RouletteManager(Main plugin) {
        this.plugin = plugin;
    }

    public Roulette spawn(String id, Location npcLocation, Location wheelLocation) {
        Roulette existing = roulettes.remove(id);
        if (existing != null) {
            existing.despawn();
        }
        Roulette roulette = new Roulette(plugin, id, npcLocation, wheelLocation);
        if (!roulette.spawn()) {
            return null;
        }
        roulettes.put(id, roulette);
        return roulette;
    }

    public Roulette create(Player player) {
        Location npcLocation = player.getLocation().clone();
        Location wheelLocation = npcLocation.clone()
                .add(npcLocation.getDirection().setY(0.0D).normalize().multiply(plugin.getSettings().getWheelDistance()))
                .add(0.0D, plugin.getSettings().getWheelHeight(), 0.0D);

        String id = plugin.getDataManager().newId();
        Roulette roulette = spawn(id, npcLocation, wheelLocation);
        if (roulette == null) {
            return null;
        }
        plugin.getDataManager().saveRoulette(id, npcLocation, wheelLocation);
        return roulette;
    }

    public boolean remove(String id) {
        Roulette roulette = roulettes.remove(id);
        if (roulette != null) {
            roulette.despawn();
        }
        plugin.getDataManager().removeRoulette(id);
        return roulette != null;
    }

    /**
     * Снять рулетку, не трогая data.yml (например, при выгрузке мира):
     * запись останется и поднимется снова, когда мир загрузится.
     */
    public Roulette unload(String id) {
        Roulette roulette = roulettes.remove(id);
        if (roulette != null) {
            roulette.despawn();
        }
        return roulette;
    }

    public void removeAll() {
        for (Roulette roulette : roulettes.values()) {
            roulette.despawn();
        }
        roulettes.clear();
        removeMode.clear();
    }

    public void applySkin(String value, String signature) {
        for (Roulette roulette : roulettes.values()) {
            roulette.applySkin(value, signature);
        }
    }

    public Roulette get(String id) {
        return roulettes.get(id);
    }

    public Collection<Roulette> getAll() {
        return Collections.unmodifiableCollection(roulettes.values());
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(roulettes.keySet());
    }

    public Roulette findByEntityId(int entityId) {
        if (entityId < 0) {
            return null;
        }
        for (Roulette roulette : roulettes.values()) {
            if (roulette.getNpcEntityId() == entityId) {
                return roulette;
            }
        }
        return null;
    }

    public Roulette findLookingAt(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector start = eye.toVector();
        Vector direction = eye.getDirection();
        Roulette best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Roulette roulette : roulettes.values()) {
            Location npc = roulette.getNpcLocation();
            if (npc.getWorld() == null || !npc.getWorld().equals(eye.getWorld())) {
                continue;
            }
            if (npc.distanceSquared(eye) > (maxDistance + 2.0D) * (maxDistance + 2.0D)) {
                continue;
            }
            BoundingBox box = BoundingBox.of(npc.clone().add(0.0D, NPC_HALF_HEIGHT, 0.0D),
                    NPC_HALF_WIDTH, NPC_HALF_HEIGHT, NPC_HALF_WIDTH);
            RayTraceResult result = box.rayTrace(start, direction, maxDistance);
            if (result == null) {
                continue;
            }
            double distance = result.getHitPosition().distanceSquared(start);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = roulette;
            }
        }
        return best;
    }

    public boolean toggleRemoveMode(Player player) {
        if (removeMode.remove(player.getUniqueId())) {
            return false;
        }
        removeMode.add(player.getUniqueId());
        return true;
    }

    public boolean isRemoveMode(Player player) {
        return removeMode.contains(player.getUniqueId());
    }

    public boolean hasRemoveMode() {
        return !removeMode.isEmpty();
    }

    public void clearRemoveMode(Player player) {
        removeMode.remove(player.getUniqueId());
    }
}
