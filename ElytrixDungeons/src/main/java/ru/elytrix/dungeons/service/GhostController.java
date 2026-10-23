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

    private final ElytrixDungeonsPlugin plugin;
    private final NamespacedKey ghostPdc;
    private final Map<String, Set<UUID>> houseGhosts = new HashMap<>();
    private final Map<UUID, String> ghostToHouse = new HashMap<>();
    private final Map<String, Long> nextRespawnMs = new HashMap<>();

    public GhostController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.ghostPdc = new NamespacedKey(plugin, "house_ghost");
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
        World w = house.getOrigin().world();
        if (w == null) return;
        BlockPosKey anchor = house.getHologramAnchor();
        if (anchor == null || !w.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) return;

        int initial = Math.max(1, plugin.getConfig().getInt("surface-dungeon.ghosts.initial-count", 6));
        spawnUpTo(w, house, initial);
    }

    public void tickHousesOncePerSecond(Iterable<DungeonInstance> allDungeons) {
        long now = System.currentTimeMillis();
        int maxAlive = Math.max(1, plugin.getConfig().getInt("surface-dungeon.ghosts.max-alive", 6));
        long intervalMs = Math.max(15L, plugin.getConfig().getLong("surface-dungeon.ghosts.respawn-every-seconds", 60L)) * 1000L;

        for (DungeonInstance d : allDungeons) {
            if (d.getCategory() != DungeonCategory.SURFACE || d.getStatus() == DungeonStatus.REGENERATING) {
                continue;
            }
            World w = d.getOrigin().world();
            BlockPosKey anchor = d.getHologramAnchor();
            if (w == null || anchor == null || !w.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
                continue;
            }

            // Re-attach any loaded tagged ghosts after chunk reload & leash all ghosts strictly inside the House
            Set<UUID> set = houseGhosts.computeIfAbsent(d.getId(), k -> new HashSet<>());
            CuboidRegion box = d.getBounds();
            Iterator<UUID> it = set.iterator();
            while (it.hasNext()) {
                UUID uid = it.next();
                Entity e = w.getEntity(uid);
                if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                    it.remove();
                    ghostToHouse.remove(uid);
                    continue;
                }
                if (le.isInsideVehicle()) {
                    le.eject();
                }
                if (!box.contains(le.getLocation())) {
                    Location back = randomInteriorPoint(d);
                    if (back != null) {
                        le.setFallDistance(0f);
                        le.teleport(back);
                        w.spawnParticle(Particle.SOUL, back.clone().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }

            if (set.size() < maxAlive) {
                long due = nextRespawnMs.computeIfAbsent(d.getId(), k -> now + intervalMs);
                if (now >= due) {
                    nextRespawnMs.put(d.getId(), now + intervalMs);
                    spawnUpTo(w, d, set.size() + 2 <= maxAlive ? set.size() + 2 : maxAlive);
                }
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
            le.setPersistent(false);
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
            if (set != null) set.remove(dead.getUniqueId());
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
                killer.sendMessage(plugin.msg("ghost-key-dropped")
                        .replace("{key}", VaultAndItemsHook.vaultKeyDisplayName(keyId)));
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
                if (plugin.getDungeonManager().getDungeon(tag) == null) {
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
        nextRespawnMs.remove(dungeonId);
        DungeonInstance d = plugin.getDungeonManager().getDungeon(dungeonId);
        World w = d != null ? d.getOrigin().world() : null;
        if (set != null) {
            for (UUID uid : set) {
                ghostToHouse.remove(uid);
                if (w != null) {
                    Entity e = w.getEntity(uid);
                    if (e != null && e.isValid()) e.remove();
                }
            }
        }
        if (w != null && d != null) {
            Location center = d.getHologramAnchor() != null ? d.getHologramAnchor().centerLocation() : null;
            if (center != null && w.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
                double rx = d.getBounds().sizeX() / 2.0 + 4.0;
                double ry = d.getBounds().sizeY() / 2.0 + 4.0;
                double rz = d.getBounds().sizeZ() / 2.0 + 4.0;
                for (Entity e : w.getNearbyEntities(center, rx, ry, rz)) {
                    if (dungeonId.equals(e.getPersistentDataContainer().get(ghostPdc, PersistentDataType.STRING))) {
                        e.remove();
                    }
                }
            }
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
