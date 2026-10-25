package ru.elytrix.dungeons.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.hook.VaultAndItemsHook;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.util.TextUtil;

/**
 * Manages Surface House Ghosts (EntityType.ALLAY):
 *  - Spawns a separated wave on the first two floors, then lets Allays roam naturally
 *  - Lets ghosts roam naturally inside the house and prevents vanilla Allay duplication / item-handing exploits
 *  - After the whole wave is defeated, waits for the configured cooldown before spawning another
 *  - On kill: 100% drops Призрачный осколок (amethyst_shard) + 35% weighted Vault Key
 */
public final class GhostController {

    private final ElytrixDungeonsPlugin plugin;
    private final NamespacedKey ghostPdc;
    private final NamespacedKey ghostHologramPdc;
    private final Map<String, Set<UUID>> houseGhosts = new HashMap<>();
    private final Map<UUID, String> ghostToHouse = new HashMap<>();

    public GhostController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.ghostPdc = new NamespacedKey(plugin, "house_ghost");
        this.ghostHologramPdc = new NamespacedKey(plugin, "ghost_hologram");
    }

    public boolean isGhost(Entity entity) {
        return entity != null && (ghostToHouse.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(ghostPdc, PersistentDataType.STRING));
    }

    public int countAliveGhosts(String dungeonId) {
        Set<UUID> set = houseGhosts.get(dungeonId);
        return set == null ? 0 : set.size();
    }

    public void populateInitialGhosts(DungeonInstance house) {
        if (house.getCategory() != DungeonCategory.SURFACE) return;
        World world = house.getOrigin().world();
        BlockPosKey anchor = house.getEntrancePos() != null ? house.getEntrancePos() : house.getOrigin();
        if (world == null || !world.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) return;

        Set<UUID> set = houseGhosts.computeIfAbsent(house.getId(), key -> new HashSet<>());
        trackExistingGhosts(world, house, set, null);
        if (house.getGhostRespawnAtMs() > System.currentTimeMillis() || !set.isEmpty()) return;

        int initial = Math.max(1, plugin.getConfig().getInt("surface-dungeon.ghosts.initial-count", 6));
        if (spawnUpTo(world, house, initial) > 0) {
            house.setGhostRespawnAtMs(0L);
            plugin.getDungeonManager().saveDungeons();
        }
    }

    public void tickHousesOncePerSecond(Iterable<DungeonInstance> allDungeons) {
        long now = System.currentTimeMillis();
        int initialCount = Math.max(1, plugin.getConfig().getInt("surface-dungeon.ghosts.initial-count", 6));

        for (DungeonInstance house : allDungeons) {
            if (house.getCategory() != DungeonCategory.SURFACE || house.getStatus() == DungeonStatus.REGENERATING) {
                continue;
            }
            World world = house.getOrigin().world();
            BlockPosKey anchor = house.getEntrancePos() != null ? house.getEntrancePos() : house.getOrigin();
            if (world == null || !world.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) continue;

            Set<UUID> set = houseGhosts.computeIfAbsent(house.getId(), key -> new HashSet<>());
            CuboidRegion bounds = house.getBounds();
            CuboidRegion recoveryBounds = bounds.expand(3, 3);
            Iterator<UUID> it = set.iterator();
            while (it.hasNext()) {
                UUID uid = it.next();
                Entity entity = world.getEntity(uid);
                // A ghost in an unloaded chunk is still part of this wave; keep it counted.
                if (entity == null) continue;
                if (!(entity instanceof LivingEntity ghost) || ghost.isDead() || !ghost.isValid()) {
                    it.remove();
                    ghostToHouse.remove(uid);
                    continue;
                }
                if (ghost.isInsideVehicle()) ghost.eject();
                if (!recoveryBounds.contains(ghost.getLocation())) {
                    Location back = randomInteriorPoint(house);
                    if (back != null) {
                        ghost.setFallDistance(0f);
                        ghost.teleport(back);
                        world.spawnParticle(Particle.SOUL, back.clone().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }

            long respawnAt = house.getGhostRespawnAtMs();
            if (set.isEmpty() && (respawnAt == 0L || now >= respawnAt)) {
                int spawned = spawnUpTo(world, house, initialCount);
                if (spawned > 0) {
                    if (respawnAt > 0L) house.setGhostRespawnAtMs(0L);
                    plugin.getDungeonManager().saveDungeons();
                } else if (respawnAt == 0L) {
                    // If this is a fresh house whose chunks were unavailable during finalize,
                    // retry initial population on the next loaded tick.
                    continue;
                }
            }
        }
    }

    private void removeTaggedGhostsNearHouse(World world, DungeonInstance house) {
        CuboidRegion bounds = house.getBounds();
        Location center = new Location(world, (bounds.minX() + bounds.maxX() + 1) / 2.0,
                (bounds.minY() + bounds.maxY() + 1) / 2.0, (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
        if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return;
        double rx = bounds.sizeX() / 2.0 + 4.0;
        double ry = bounds.sizeY() / 2.0 + 4.0;
        double rz = bounds.sizeZ() / 2.0 + 4.0;
        for (Entity entity : world.getNearbyEntities(center, rx, ry, rz)) {
            if (house.getId().equals(entity.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING))) {
                ghostToHouse.remove(entity.getUniqueId());
                entity.remove();
            }
        }
    }

    private void trackExistingGhosts(World world, DungeonInstance house, Set<UUID> tracked, UUID excluded) {
        CuboidRegion bounds = house.getBounds();
        for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
                    if (excluded != null && excluded.equals(entity.getUniqueId())) continue;
                    if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) continue;
                    if (!house.getId().equals(entity.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING))) {
                        continue;
                    }
                    living.setCustomName(TextUtil.color("Призрак"));
                    living.setCustomNameVisible(true);
                    tracked.add(entity.getUniqueId());
                    ghostToHouse.put(entity.getUniqueId(), house.getId());
                }
            }
        }
    }

    private int spawnUpTo(World w, DungeonInstance house, int targetTotal) {
        Set<UUID> set = houseGhosts.computeIfAbsent(house.getId(), k -> new HashSet<>());
        double hp = Math.max(4.0, plugin.getConfig().getDouble("surface-dungeon.ghosts.health", 16.0));
        int spawned = 0;
        while (set.size() < targetTotal) {
            Location loc = chooseSpreadSpawnPoint(w, house, set, spawned % 2);
            if (loc == null) break;
            Entity raw = w.spawnEntity(loc.clone().add(0, 0.05, 0), EntityType.ALLAY);
            if (!(raw instanceof LivingEntity le)) {
                raw.remove();
                break;
            }
            le.getPersistentDataContainer().set(ghostPdc, PersistentDataType.STRING, house.getId());
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            le.setCustomName(TextUtil.color("Призрак"));
            le.setCustomNameVisible(true);

            AttributeInstance maxHp = ((Attributable) le).getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(hp);
            le.setHealth(hp);

            if (le instanceof Allay allay) {
                allay.setCanDuplicate(false);
                allay.setDuplicationCooldown(Long.MAX_VALUE);
            }

            set.add(le.getUniqueId());
            ghostToHouse.put(le.getUniqueId(), house.getId());
            spawned++;
        }
        return spawned;
    }

    public void handleGhostDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        String dungeonId = ghostToHouse.remove(dead.getUniqueId());
        if (dungeonId == null) {
            dungeonId = dead.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING);
        }
        if (dungeonId != null) {
            DungeonInstance house = plugin.getDungeonManager().getDungeon(dungeonId);
            if (house != null && house.getCategory() == DungeonCategory.SURFACE) {
                Set<UUID> set = houseGhosts.computeIfAbsent(dungeonId, key -> new HashSet<>());
                set.remove(dead.getUniqueId());
                World world = house.getOrigin().world();
                if (world != null && set.isEmpty()) {
                    trackExistingGhosts(world, house, set, dead.getUniqueId());
                }
                if (set.isEmpty() && house.getGhostRespawnAtMs() <= System.currentTimeMillis()) {
                    long cooldownSeconds = Math.max(60L,
                            plugin.getConfig().getLong("surface-dungeon.ghosts.respawn-cooldown-seconds", 7200L));
                    house.setGhostRespawnAtMs(System.currentTimeMillis() + cooldownSeconds * 1000L);
                    plugin.getDungeonManager().saveDungeons();
                }
            }
        }

        event.getDrops().clear();
        event.setDroppedExp(12);

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1. 100% chance: Призрачный осколок (amethyst_shard for upcoming Trader)
        double shardChance = plugin.getConfig().getDouble("surface-dungeon.ghosts.shard-drop-chance", 100.0);
        if (rng.nextDouble(100.0) < shardChance) {
            int minAmt = Math.max(1, plugin.getConfig().getInt("surface-dungeon.ghosts.shard-min-amount", 1));
            int maxAmt = Math.max(minAmt, plugin.getConfig().getInt("surface-dungeon.ghosts.shard-max-amount", 1));
            int amt = minAmt == maxAmt ? minAmt : rng.nextInt(minAmt, maxAmt + 1);
            event.getDrops().add(plugin.getItemsHook().createGhostShard(amt));
        }

        // 2. 35% chance: Vault Key of any rarity (weighted — common drops most frequently!)
        double keyChance = plugin.getConfig().getDouble("surface-dungeon.ghosts.key-drop-chance", 35.0);
        if (rng.nextDouble(100.0) < keyChance) {
            String keyId = rollWeightedKeyId(rng);
            ItemStack keyItem = plugin.getItemsHook().createVaultKey(keyId, 1);
            event.getDrops().add(keyItem);

            Location deathLoc = dead.getLocation();
            dead.getWorld().playSound(deathLoc, Sound.BLOCK_VAULT_ACTIVATE, 1.0f, 1.15f);
            dead.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, deathLoc.clone().add(0, 0.5, 0), 22, 0.3, 0.3, 0.3, 0.1);
            Player killer = dead.getKiller();
            if (killer != null) {
                killer.sendMessage(TextUtil.color(plugin.msg("ghost-key-dropped")
                        .replace("{key}", VaultAndItemsHook.vaultKeyDisplayName(keyId))));
            }
        }

    }

    public String rollWeightedKeyId(ThreadLocalRandom rng) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("surface-dungeon.ghosts.key-weights");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                int w = sec.getInt(k, 0);
                if (w > 0) weights.put(k, w);
            }
        }
        if (weights.isEmpty()) {
            weights.put("rusty", 52);
            weights.put("knowledge", 25);
            weights.put("royal", 13);
            weights.put("void", 7);
            weights.put("unique", 3);
        }
        int total = 0;
        for (int w : weights.values()) total += w;
        int roll = rng.nextInt(Math.max(1, total));
        int acc = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            acc += e.getValue();
            if (roll < acc) return e.getKey();
        }
        return "rusty";
    }

    public void handleChunkLoad(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            String tag = entity.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING);
            if (tag == null) continue;
            if (plugin.getDungeonManager().getDungeon(tag) == null) {
                entity.remove();
                continue;
            }
            if (entity instanceof LivingEntity ghost) {
                ghost.setCustomName(TextUtil.color("Призрак"));
                ghost.setCustomNameVisible(true);
                houseGhosts.computeIfAbsent(tag, key -> new HashSet<>()).add(entity.getUniqueId());
                ghostToHouse.put(entity.getUniqueId(), tag);
            }
        }

        // Remove ghost TextDisplays from previous versions; ghosts now use only their entity name.
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay display
                    && display.getPersistentDataContainer().has(ghostHologramPdc, PersistentDataType.STRING)) {
                display.remove();
            }
        }
    }

    public void clearHouseGhosts(String dungeonId) {
        Set<UUID> set = houseGhosts.remove(dungeonId);
        // The persisted two-hour deadline belongs to the dungeon and survives resets/reloads.
        DungeonInstance house = plugin.getDungeonManager().getDungeon(dungeonId);
        World world = house == null ? null : house.getOrigin().world();
        if (set != null) {
            for (UUID uid : set) {
                ghostToHouse.remove(uid);
                if (world != null) {
                    Entity entity = world.getEntity(uid);
                    if (entity != null && entity.isValid()) entity.remove();
                }
            }
        }
        if (world != null && house != null) {
            removeTaggedGhostsNearHouse(world, house);
        }
    }

    public void shutdownAll(Iterable<DungeonInstance> dungeons) {
        List<String> ids = new ArrayList<>(houseGhosts.keySet());
        for (DungeonInstance d : dungeons) {
            if (!ids.contains(d.getId())) ids.add(d.getId());
        }
        for (String id : ids) {
            clearHouseGhosts(id);
        }
    }

    private static Location chooseSpreadSpawnPoint(
            World world, DungeonInstance house, Set<UUID> aliveGhosts, int preferredFloor
    ) {
        List<BlockPosKey> candidates = safeSpawnPoints(world, house);
        if (candidates.isEmpty()) return randomInteriorPoint(house);
        List<BlockPosKey> floorCandidates = floorBandCandidates(candidates, preferredFloor);
        if (!floorCandidates.isEmpty()) candidates = floorCandidates;

        List<Location> occupied = new ArrayList<>();
        for (UUID uid : aliveGhosts) {
            Entity entity = world.getEntity(uid);
            if (entity instanceof LivingEntity ghost && ghost.isValid() && !ghost.isDead()) {
                occupied.add(ghost.getLocation());
            }
        }
        if (occupied.isEmpty()) {
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).floorLocation();
        }

        double bestDistance = -1.0;
        List<BlockPosKey> best = new ArrayList<>();
        for (BlockPosKey candidate : candidates) {
            Location location = candidate.floorLocation();
            double nearest = Double.POSITIVE_INFINITY;
            for (Location other : occupied) {
                nearest = Math.min(nearest, location.distanceSquared(other));
            }
            if (nearest > bestDistance) {
                bestDistance = nearest;
                best.clear();
                best.add(candidate);
            } else if (nearest == bestDistance) {
                best.add(candidate);
            }
        }
        return best.get(ThreadLocalRandom.current().nextInt(best.size())).floorLocation();
    }

    private static List<BlockPosKey> floorBandCandidates(List<BlockPosKey> candidates, int floorIndex) {
        if (candidates.isEmpty()) return List.of();
        int firstFloorY = Integer.MAX_VALUE;
        for (BlockPosKey point : candidates) firstFloorY = Math.min(firstFloorY, point.y());
        int targetFloorY = firstFloorY;
        if (floorIndex > 0) {
            targetFloorY = Integer.MAX_VALUE;
            for (BlockPosKey point : candidates) {
                if (point.y() >= firstFloorY + 3) targetFloorY = Math.min(targetFloorY, point.y());
            }
            if (targetFloorY == Integer.MAX_VALUE) return List.of();
        }
        List<BlockPosKey> band = new ArrayList<>();
        for (BlockPosKey point : candidates) {
            if (point.y() >= targetFloorY && point.y() <= targetFloorY + 1) band.add(point);
        }
        return band;
    }

    private static List<BlockPosKey> safeSpawnPoints(World world, DungeonInstance house) {
        Set<BlockPosKey> allSafe = new HashSet<>();
        Set<BlockPosKey> awayFromEntrance = new HashSet<>();
        BlockPosKey entrance = house.getEntrancePos();
        CuboidRegion bounds = house.getBounds();

        for (BlockPosKey candidate : house.getMobSpawns()) {
            // Use only analyzed corridor markers. Expanding a marker through a wall can put an
            // Allay into a sealed room even when the original marker was reachable.
            if (!bounds.containsBlock(candidate.worldName(), candidate.x(), candidate.y(), candidate.z())
                    || !isSafeSpawnPoint(world, candidate)) {
                continue;
            }
            allSafe.add(candidate);
            if (entrance == null || horizontalDistanceSquared(candidate, entrance) >= 9) {
                awayFromEntrance.add(candidate);
            }
        }

        Set<BlockPosKey> selected = awayFromEntrance.isEmpty() ? allSafe : awayFromEntrance;
        return new ArrayList<>(selected);
    }

    private static boolean isSafeSpawnPoint(World world, BlockPosKey point) {
        if (point.y() <= world.getMinHeight() || point.y() + 1 >= world.getMaxHeight()
                || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
            return false;
        }
        Block floor = world.getBlockAt(point.x(), point.y() - 1, point.z());
        Block feet = world.getBlockAt(point.x(), point.y(), point.z());
        Block head = world.getBlockAt(point.x(), point.y() + 1, point.z());
        return floor.isSolid() && feet.isPassable() && head.isPassable();
    }

    private static int horizontalDistanceSquared(BlockPosKey a, BlockPosKey b) {
        int dx = a.x() - b.x(), dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private static Location randomInteriorPoint(DungeonInstance house) {
        World world = house.getOrigin().world();
        if (world == null) return null;
        List<BlockPosKey> points = safeSpawnPoints(world, house);
        if (!points.isEmpty()) {
            return points.get(ThreadLocalRandom.current().nextInt(points.size())).floorLocation();
        }
        Location entrance = house.teleportLocation();
        return entrance != null && isSafeSpawnPoint(world, BlockPosKey.of(entrance)) ? entrance : null;
    }
}
