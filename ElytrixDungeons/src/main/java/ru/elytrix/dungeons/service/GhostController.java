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
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
 *  - Spawns ghosts inside rooms of every Surface House & keeps them leashed inside house walls
 *  - Prevents vanilla Allay duplication / item-handing / passive regen exploits
 *  - On kill: 100% drops Призрачный осколок (amethyst_shard) + 5% weighted Vault Key (mostly common!)
 */
public final class GhostController {

    private static final int GHOSTS_PER_WAVE = 6;
    private static final long WAVE_DURATION_MS = 30L * 60L * 1000L;

    private final ElytrixDungeonsPlugin plugin;
    private final NamespacedKey ghostPdc;
    private final NamespacedKey ghostWavePdc;
    private final Map<String, Set<UUID>> houseGhosts = new HashMap<>();
    private final Map<UUID, String> ghostToHouse = new HashMap<>();
    private final Map<String, Long> waveStartedAtMs = new HashMap<>();
    private final Map<String, Integer> killedInWave = new HashMap<>();

    public GhostController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.ghostPdc = new NamespacedKey(plugin, "house_ghost");
        this.ghostWavePdc = new NamespacedKey(plugin, "house_ghost_wave");
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
        clearHouseGhosts(house.getId());
        World world = house.getOrigin().world();
        if (world == null) return;
        startFreshWave(house, world, System.currentTimeMillis(), false);
    }

    public void tickHousesOncePerSecond(Iterable<DungeonInstance> allDungeons) {
        long now = System.currentTimeMillis();
        for (DungeonInstance house : allDungeons) {
            if (house.getCategory() != DungeonCategory.SURFACE || house.getStatus() == DungeonStatus.REGENERATING) {
                continue;
            }
            World world = house.getOrigin().world();
            BlockPosKey anchor = house.getHologramAnchor();
            if (world == null || anchor == null || !world.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
                continue;
            }

            Set<UUID> set = houseGhosts.computeIfAbsent(house.getId(), key -> new HashSet<>());
            CuboidRegion bounds = house.getBounds();
            Iterator<UUID> it = set.iterator();
            while (it.hasNext()) {
                UUID uid = it.next();
                Entity entity = world.getEntity(uid);
                // A ghost in an unloaded chunk is still part of this wave; keep its UUID until it loads.
                if (entity == null) continue;
                if (!(entity instanceof LivingEntity ghost) || ghost.isDead() || !ghost.isValid()) {
                    it.remove();
                    ghostToHouse.remove(uid);
                    continue;
                }
                if (ghost.isInsideVehicle()) {
                    ghost.eject();
                }
                if (!bounds.contains(ghost.getLocation())) {
                    Location back = randomInteriorPoint(house);
                    if (back != null) {
                        ghost.setFallDistance(0f);
                        ghost.teleport(back);
                        world.spawnParticle(Particle.SOUL, back.clone().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }

            Long waveStartedAt = waveStartedAtMs.get(house.getId());
            if (waveStartedAt == null) {
                startFreshWave(house, world, now, true);
            } else if (now - waveStartedAt >= WAVE_DURATION_MS) {
                // If the wave was not fully defeated, replace all remaining ghosts after 30 minutes.
                startFreshWave(house, world, now, true);
            }
        }
    }

    private void startFreshWave(DungeonInstance house, World world, long now, boolean removePreviousWave) {
        String id = house.getId();
        if (removePreviousWave) {
            Set<UUID> previous = houseGhosts.get(id);
            if (previous != null) {
                for (UUID uid : previous) {
                    ghostToHouse.remove(uid);
                    Entity entity = world.getEntity(uid);
                    if (entity != null && entity.isValid()) entity.remove();
                }
            }
            removeTaggedGhostsNearHouse(world, house);
        }

        houseGhosts.put(id, new HashSet<>());
        waveStartedAtMs.put(id, now);
        killedInWave.put(id, 0);
        spawnUpTo(world, house, GHOSTS_PER_WAVE);
    }

    private void removeTaggedGhostsNearHouse(World world, DungeonInstance house) {
        BlockPosKey anchor = house.getHologramAnchor();
        Location center = anchor == null ? null : anchor.centerLocation();
        if (center == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return;
        double rx = house.getBounds().sizeX() / 2.0 + 4.0;
        double ry = house.getBounds().sizeY() / 2.0 + 4.0;
        double rz = house.getBounds().sizeZ() / 2.0 + 4.0;
        for (Entity entity : world.getNearbyEntities(center, rx, ry, rz)) {
            if (house.getId().equals(entity.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING))) {
                ghostToHouse.remove(entity.getUniqueId());
                entity.remove();
            }
        }
    }

    private void spawnUpTo(World w, DungeonInstance house, int targetTotal) {
        Set<UUID> set = houseGhosts.computeIfAbsent(house.getId(), k -> new HashSet<>());
        double hp = Math.max(4.0, plugin.getConfig().getDouble("surface-dungeon.ghosts.health", 16.0));
        while (set.size() < targetTotal) {
            Location loc = randomInteriorPoint(house);
            if (loc == null) break;
            Entity raw = w.spawnEntity(loc.clone().add(0, 0.6, 0), EntityType.ALLAY);
            if (!(raw instanceof LivingEntity le)) {
                raw.remove();
                break;
            }
            le.getPersistentDataContainer().set(ghostPdc, PersistentDataType.STRING, house.getId());
            le.getPersistentDataContainer().set(ghostWavePdc, PersistentDataType.LONG,
                    waveStartedAtMs.getOrDefault(house.getId(), System.currentTimeMillis()));
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            le.setCustomName(TextUtil.color("&#F8BEFBПризрак"));
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
        }
    }

    public void handleGhostDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        String dungeonId = ghostToHouse.remove(dead.getUniqueId());
        if (dungeonId == null) {
            dungeonId = dead.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING);
        }
        if (dungeonId != null) {
            Set<UUID> set = houseGhosts.get(dungeonId);
            boolean belongedToCurrentWave = set != null && set.remove(dead.getUniqueId());
            if (belongedToCurrentWave) {
                int killed = killedInWave.getOrDefault(dungeonId, 0) + 1;
                killedInWave.put(dungeonId, killed);
                if (killed >= GHOSTS_PER_WAVE) {
                    DungeonInstance house = plugin.getDungeonManager().getDungeon(dungeonId);
                    World world = house == null ? null : house.getOrigin().world();
                    if (house != null && world != null && house.getStatus() != DungeonStatus.REGENERATING) {
                        // The sixth kill starts a complete new wave immediately.
                        startFreshWave(house, world, System.currentTimeMillis(), false);
                    }
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

        // 2. 5% chance: Vault Key of any rarity (weighted — common drops most frequently!)
        double keyChance = plugin.getConfig().getDouble("surface-dungeon.ghosts.key-drop-chance", 5.0);
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

        if (dungeonId != null) {
            DungeonInstance house = plugin.getDungeonManager().getDungeon(dungeonId);
            if (house != null) plugin.getDungeonManager().updateDungeonHologram(house);
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

    public void handleChunkLoad(org.bukkit.Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            String tag = e.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING);
            if (tag != null) {
                Long entityWave = e.getPersistentDataContainer().get(ghostWavePdc, PersistentDataType.LONG);
                Long currentWave = waveStartedAtMs.get(tag);
                if (plugin.getDungeonManager().getDungeon(tag) == null
                        || entityWave != null && currentWave != null && !entityWave.equals(currentWave)) {
                    e.remove();
                } else {
                    houseGhosts.computeIfAbsent(tag, k -> new HashSet<>()).add(e.getUniqueId());
                    ghostToHouse.put(e.getUniqueId(), tag);
                }
            }
        }
    }

    public void clearHouseGhosts(String dungeonId) {
        Set<UUID> set = houseGhosts.remove(dungeonId);
        waveStartedAtMs.remove(dungeonId);
        killedInWave.remove(dungeonId);
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

    private static Location randomInteriorPoint(DungeonInstance house) {
        List<BlockPosKey> pts = house.getMobSpawns();
        if (pts.isEmpty()) {
            return house.teleportLocation();
        }
        return pts.get(ThreadLocalRandom.current().nextInt(pts.size())).floorLocation();
    }
}
