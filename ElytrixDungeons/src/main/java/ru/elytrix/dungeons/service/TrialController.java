package ru.elytrix.dungeons.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.hook.VaultAndItemsHook;
import ru.elytrix.dungeons.hook.VaultAndItemsHook.MapEnchant;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.LootPoolType;
import ru.elytrix.dungeons.util.TextUtil;

public final class TrialController {

    public enum MobRewardTier { NONE, STANDARD, STRONG, BOSS }

    public static final int WAVE_COUNT = 3;
    public static final int TOTAL_MOB_COUNT = 13;
    private static final int SPAWN_SEARCH_RADIUS = 4;
    private static final double[] SPAWN_SEPARATION_THRESHOLDS = {2.25, 1.0};

    public static final class ActiveFight {
        public final DungeonInstance dungeon;
        public final BossBar bossBar;
        /** Total combatants across all three waves; used by status displays while idle. */
        public final int initialMobCount;
        public final long deadlineMs;
        public final Set<UUID> aliveMobs = new HashSet<>();
        public int waveIndex;
        public int currentWaveInitialMobCount = 1;
        public long nextWaveAtMs;

        ActiveFight(DungeonInstance dungeon, BossBar bossBar, int initialMobCount, long timeoutSec) {
            this.dungeon = dungeon;
            this.bossBar = bossBar;
            this.initialMobCount = Math.max(1, initialMobCount);
            this.deadlineMs = System.currentTimeMillis() + timeoutSec * 1000L;
        }
    }

    private record FighterSpec(
            EntityType type,
            String name,
            double health,
            double attackDamage,
            boolean archer,
            boolean elite,
            boolean boss
    ) {}

    private final ElytrixDungeonsPlugin plugin;
    private final NamespacedKey trialMobPdc;
    private final NamespacedKey trialMobTierPdc;
    private final Map<String, ActiveFight> fightsByDungeon = new HashMap<>();
    private final Map<UUID, String> mobToDungeon = new HashMap<>();

    public TrialController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.trialMobPdc = new NamespacedKey(plugin, "dungeon_mob");
        this.trialMobTierPdc = new NamespacedKey(plugin, "dungeon_mob_reward_tier");
    }

    public ActiveFight getFight(String dungeonId) {
        return fightsByDungeon.get(dungeonId);
    }

    public boolean isTrialMob(Entity entity) {
        return entity != null && (mobToDungeon.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(trialMobPdc, PersistentDataType.STRING));
    }

    public MobRewardTier getMobRewardTier(Entity entity) {
        if (!isTrialMob(entity)) return MobRewardTier.NONE;
        String tier = entity.getPersistentDataContainer().get(trialMobTierPdc, PersistentDataType.STRING);
        if ("boss".equals(tier)) return MobRewardTier.BOSS;
        if ("strong".equals(tier)) return MobRewardTier.STRONG;
        return MobRewardTier.STANDARD;
    }

    public void startUndergroundFight(Player starter, DungeonInstance dungeon) {
        ActiveFight running = fightsByDungeon.get(dungeon.getId());
        if (running != null) {
            starter.sendMessage(plugin.msg("underground-already-active")
                    .replace("{mobs}", String.valueOf(running.aliveMobs.size())));
            return;
        }
        World world = dungeon.getOrigin().world();
        if (world == null) return;

        long timeoutSec = Math.max(90L,
                plugin.getConfig().getLong("underground-dungeon.fight-timeout-seconds", 690L));
        BossBar bar = Bukkit.createBossBar(
                TextUtil.color("&#89A0FFПодземный данж &8┃ &fВолна 1/3 &8┃ &fОсталось: &#F8BEFB7"),
                BarColor.BLUE,
                BarStyle.SEGMENTED_10
        );
        bar.setProgress(1.0);

        ActiveFight fight = new ActiveFight(dungeon, bar, TOTAL_MOB_COUNT, timeoutSec);
        fightsByDungeon.put(dungeon.getId(), fight);
        dungeon.setStatus(DungeonStatus.ACTIVE_TRIAL);

        if (!spawnWave(world, dungeon, fight, 0)) {
            abortFight(dungeon.getId(), true);
            starter.sendMessage(plugin.msg("underground-spawn-failed"));
            plugin.getDungeonManager().updateShulkerHolograms(dungeon);
            return;
        }

        plugin.getDungeonManager().updateShulkerHolograms(dungeon);
        Location center = dungeon.getHologramAnchor().centerLocation();
        world.playSound(center, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 1.0f, 0.95f);
        world.playSound(center, Sound.ENTITY_WARDEN_NEARBY_CLOSER, 0.9f, 1.0f);
        world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 0.8, 0), 30, 0.6, 0.4, 0.6, 0.03);

        notifyNearby(dungeon, 40.0,
                plugin.msg("underground-started").replace("{player}", starter.getName()));
        refreshFightVisuals(fight);
    }

    private List<FighterSpec> createWaveLineup(int waveIndex) {
        double standardHealth = Math.max(1.0,
                plugin.getConfig().getDouble("underground-dungeon.wither-skeleton-health", 36.0));
        double standardDamage = Math.max(1.0,
                plugin.getConfig().getDouble("underground-dungeon.wither-skeleton-damage", 8.0));
        double eliteHealth = Math.max(1.0,
                plugin.getConfig().getDouble("underground-dungeon.guard-health", 64.0));
        double eliteDamage = Math.max(1.0,
                plugin.getConfig().getDouble("underground-dungeon.guard-damage", 14.5));
        double bossHealth = Math.max(1.0,
                plugin.getConfig().getDouble("underground-dungeon.leader-health", 150.0));
        double bossDamage = Math.max(1.0,
                plugin.getConfig().getDouble("underground-dungeon.leader-damage", 18.5));

        List<FighterSpec> lineup = new ArrayList<>();
        switch (waveIndex) {
            case 0 -> {
                for (int i = 0; i < 2; i++) {
                    lineup.add(new FighterSpec(EntityType.WITHER_SKELETON, "&#89A0FFСильный скелет",
                            eliteHealth, eliteDamage, false, true, false));
                }
                for (int i = 0; i < 5; i++) {
                    lineup.add(new FighterSpec(EntityType.WITHER_SKELETON, "&7Визер-скелет",
                            standardHealth, standardDamage, false, false, false));
                }
            }
            case 1 -> {
                for (int i = 0; i < 3; i++) lineup.add(strongArcher(eliteHealth, eliteDamage));
            }
            case 2 -> {
                lineup.add(new FighterSpec(EntityType.WITHER_SKELETON,
                        "&#F8BEFBКаратель Подземелья &8[&cБосс&8]",
                        bossHealth, bossDamage, false, true, true));
                for (int i = 0; i < 2; i++) lineup.add(strongArcher(eliteHealth, eliteDamage));
            }
            default -> { }
        }
        return lineup;
    }

    private static FighterSpec strongArcher(double health, double damage) {
        EntityType type = ThreadLocalRandom.current().nextBoolean() ? EntityType.BOGGED : EntityType.STRAY;
        return new FighterSpec(type, "&#89A0FFСильный лучник", health, damage, true, true, false);
    }

    private boolean spawnWave(World world, DungeonInstance dungeon, ActiveFight fight, int waveIndex) {
        List<FighterSpec> lineup = createWaveLineup(waveIndex);
        if (lineup.isEmpty()) return false;

        List<BlockPosKey> spawnPositions = chooseSpawnPositions(world, dungeon, lineup.size());
        if (spawnPositions.size() != lineup.size()) return false;

        fight.waveIndex = waveIndex;
        fight.nextWaveAtMs = 0L;
        for (int i = 0; i < lineup.size(); i++) {
            if (!spawnSingleFighter(world, dungeon, fight, spawnPositions.get(i).floorLocation(), lineup.get(i))) {
                cleanupEntities(fight);
                return false;
            }
        }
        fight.currentWaveInitialMobCount = fight.aliveMobs.size();
        return fight.currentWaveInitialMobCount == lineup.size();
    }

    private List<BlockPosKey> chooseSpawnPositions(World world, DungeonInstance dungeon, int count) {
        List<BlockPosKey> candidates = collectSafeSpawnCandidates(world, dungeon);
        Collections.shuffle(candidates, ThreadLocalRandom.current());

        for (double minHorizontalDistanceSquared : SPAWN_SEPARATION_THRESHOLDS) {
            List<BlockPosKey> selected = new ArrayList<>(count);
            for (BlockPosKey candidate : candidates) {
                if (!isSeparatedFromSelected(candidate, selected, minHorizontalDistanceSquared)) continue;
                selected.add(candidate);
                if (selected.size() == count) return selected;
            }
        }
        return List.of();
    }

    private List<BlockPosKey> collectSafeSpawnCandidates(World world, DungeonInstance dungeon) {
        LinkedHashSet<BlockPosKey> safe = new LinkedHashSet<>();
        List<BlockPosKey> anchors = new ArrayList<>(dungeon.getMobSpawns());
        if (anchors.isEmpty()) {
            BlockPosKey fallback = dungeon.getEntrancePos() != null
                    ? dungeon.getEntrancePos()
                    : dungeon.getHologramAnchor();
            if (fallback != null) anchors.add(fallback);
        }
        if (anchors.isEmpty()) anchors.add(dungeon.getOrigin());

        CuboidRegion bounds = dungeon.getBounds();
        Location center = new Location(world,
                (bounds.minX() + bounds.maxX() + 1) / 2.0,
                (bounds.minY() + bounds.maxY() + 1) / 2.0,
                (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
        double radiusX = Math.max(2.0, bounds.sizeX() / 2.0 + 2.0);
        double radiusY = Math.max(3.0, bounds.sizeY() / 2.0 + 2.0);
        double radiusZ = Math.max(2.0, bounds.sizeZ() / 2.0 + 2.0);
        List<Location> occupiedByLivingEntities = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radiusX, radiusY, radiusZ)) {
            if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                occupiedByLivingEntities.add(living.getLocation());
            }
        }

        for (BlockPosKey anchor : anchors) {
            if (!world.getName().equals(anchor.worldName())) continue;
            for (int radius = 0; radius <= SPAWN_SEARCH_RADIUS; radius++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPosKey candidate = anchor.add(dx, dy, dz);
                            if (bounds.containsBlock(world.getName(), candidate.x(), candidate.y(), candidate.z())
                                    && isSafeSpawnPoint(world, candidate, occupiedByLivingEntities)) {
                                safe.add(candidate);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(safe);
    }

    private static boolean isSafeSpawnPoint(World world, BlockPosKey point, List<Location> occupied) {
        if (point.y() <= world.getMinHeight() || point.y() + 2 >= world.getMaxHeight()
                || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
            return false;
        }

        Block floor = world.getBlockAt(point.x(), point.y() - 1, point.z());
        if (!floor.isSolid() || floor.isLiquid()) return false;
        for (int height = 0; height <= 2; height++) {
            Block bodySpace = world.getBlockAt(point.x(), point.y() + height, point.z());
            if (!bodySpace.isPassable() || bodySpace.isLiquid()) return false;
        }

        double x = point.x() + 0.5;
        double y = point.y();
        double z = point.z() + 0.5;
        for (Location entityLocation : occupied) {
            if (Math.abs(entityLocation.getX() - x) < 0.85
                    && Math.abs(entityLocation.getZ() - z) < 0.85
                    && Math.abs(entityLocation.getY() - y) < 3.0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSeparatedFromSelected(
            BlockPosKey candidate, List<BlockPosKey> selected, double minHorizontalDistanceSquared
    ) {
        for (BlockPosKey other : selected) {
            int dy = Math.abs(candidate.y() - other.y());
            int dx = candidate.x() - other.x();
            int dz = candidate.z() - other.z();
            if (dy < 3 && dx * dx + dz * dz < minHorizontalDistanceSquared) return false;
        }
        return true;
    }

    private boolean spawnSingleFighter(
            World world,
            DungeonInstance dungeon,
            ActiveFight fight,
            Location location,
            FighterSpec spec
    ) {
        Entity raw = world.spawnEntity(location, spec.type());
        if (!(raw instanceof LivingEntity living)) {
            if (raw != null) raw.remove();
            return false;
        }
        living.getPersistentDataContainer().set(trialMobPdc, PersistentDataType.STRING, dungeon.getId());
        living.getPersistentDataContainer().set(trialMobTierPdc, PersistentDataType.STRING,
                spec.boss() ? "boss" : spec.elite() ? "strong" : "standard");
        living.setPersistent(false);
        living.setRemoveWhenFarAway(false);
        living.setCanPickupItems(false);
        living.setCustomNameVisible(true);

        setAttr(living, Attribute.MAX_HEALTH, spec.health());
        living.setHealth(spec.health());
        setAttr(living, Attribute.ATTACK_DAMAGE, spec.attackDamage());
        setAttr(living, Attribute.ARMOR, spec.boss() ? 18.0 : spec.elite() ? 12.0 : 4.0);
        setAttr(living, Attribute.ARMOR_TOUGHNESS, spec.boss() ? 8.0 : spec.elite() ? 4.0 : 0.0);
        setAttr(living, Attribute.FOLLOW_RANGE, 32.0);
        setAttr(living, Attribute.KNOCKBACK_RESISTANCE, spec.boss() ? 0.65 : spec.elite() ? 0.30 : 0.10);
        living.setCustomName(TextUtil.color(spec.name()));

        if (spec.boss()) {
            setAttr(living, Attribute.SCALE, 1.24);
            living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 300, 0, false, false));
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 300, 0, false, false));
        } else if (spec.elite() && !spec.archer()) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 300, 0, false, false));
        }
        living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 300, 0, false, false));

        EntityEquipment equipment = living.getEquipment();
        if (equipment != null) {
            if (spec.boss()) {
                equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET), true);
                equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE), true);
                equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS), true);
                equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS), true);
                equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE), true);
            } else if (spec.elite()) {
                equipment.setHelmet(new ItemStack(Material.DIAMOND_HELMET), true);
                equipment.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE), true);
                equipment.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS), true);
                equipment.setBoots(new ItemStack(Material.DIAMOND_BOOTS), true);
                if (spec.archer()) {
                    equipment.setItemInMainHand(VaultAndItemsHook.enchantedGear(Material.BOW, null,
                            new MapEnchant(Enchantment.POWER, 4), new MapEnchant(Enchantment.PUNCH, 1)), true);
                } else {
                    equipment.setItemInMainHand(VaultAndItemsHook.enchantedGear(Material.DIAMOND_SWORD, null,
                            new MapEnchant(Enchantment.SHARPNESS, 3), new MapEnchant(Enchantment.UNBREAKING, 3)), true);
                }
            }
            equipment.setHelmetDropChance(0f);
            equipment.setChestplateDropChance(0f);
            equipment.setLeggingsDropChance(0f);
            equipment.setBootsDropChance(0f);
            equipment.setItemInMainHandDropChance(0f);
        }

        Player target = findNearestPlayer(world, location, 26.0);
        if (target != null && living instanceof Mob mob) mob.setTarget(target);
        fight.aliveMobs.add(living.getUniqueId());
        mobToDungeon.put(living.getUniqueId(), dungeon.getId());
        return true;
    }

    public void handleMobDeath(UUID mobId) {
        String dungeonId = mobToDungeon.remove(mobId);
        if (dungeonId == null) return;
        ActiveFight fight = fightsByDungeon.get(dungeonId);
        if (fight == null) return;
        fight.aliveMobs.remove(mobId);

        World world = fight.dungeon.getOrigin().world();
        if (fight.aliveMobs.isEmpty()) {
            if (world != null) handleClearedWave(world, fight);
        } else {
            refreshFightVisuals(fight);
            plugin.getDungeonManager().updateShulkerHolograms(fight.dungeon);
        }
    }

    private void handleClearedWave(World world, ActiveFight fight) {
        DungeonInstance dungeon = fight.dungeon;
        if (fight.waveIndex >= WAVE_COUNT - 1) {
            unlockUndergroundShulkersAndCooldown(world, fight);
            return;
        }

        if (fight.nextWaveAtMs == 0L) {
            long delaySeconds = Math.max(1L,
                    plugin.getConfig().getLong("underground-dungeon.wave-delay-seconds", 3L));
            fight.nextWaveAtMs = System.currentTimeMillis() + delaySeconds * 1000L;
            notifyNearby(dungeon, 40.0, plugin.msg("underground-wave-cleared")
                    .replace("{wave}", String.valueOf(fight.waveIndex + 2))
                    .replace("{seconds}", String.valueOf(delaySeconds)));
        }
        refreshFightVisuals(fight);
        plugin.getDungeonManager().updateShulkerHolograms(dungeon);
    }

    private static String waveDescription(int waveIndex) {
        return switch (waveIndex) {
            case 1 -> "3 сильных лучника";
            case 2 -> "босс и 2 сильных лучника";
            default -> "2 сильных скелета и 5 обычных визер-скелетов";
        };
    }

    private void announceWave(DungeonInstance dungeon, int waveIndex) {
        notifyNearby(dungeon, 40.0, plugin.msg("underground-wave-started")
                .replace("{wave}", String.valueOf(waveIndex + 1))
                .replace("{description}", waveDescription(waveIndex)));
    }

    public void tickOncePerSecond() {
        long now = System.currentTimeMillis();
        for (ActiveFight fight : new ArrayList<>(fightsByDungeon.values())) {
            DungeonInstance dungeon = fight.dungeon;
            World world = dungeon.getOrigin().world();
            if (world == null) {
                abortFight(dungeon.getId(), false);
                continue;
            }
            if (now >= fight.deadlineMs) {
                notifyNearby(dungeon, 40.0, plugin.msg("underground-timeout"));
                abortFight(dungeon.getId(), true);
                plugin.getDungeonManager().updateShulkerHolograms(dungeon);
                continue;
            }

            pruneAndLeashMobs(world, fight);
            if (fight.aliveMobs.isEmpty()) {
                if (fight.nextWaveAtMs == 0L) {
                    handleClearedWave(world, fight);
                    continue;
                }
                if (now < fight.nextWaveAtMs) {
                    refreshFightVisuals(fight);
                    continue;
                }

                int nextWave = fight.waveIndex + 1;
                if (!spawnWave(world, dungeon, fight, nextWave)) {
                    notifyNearby(dungeon, 40.0, plugin.msg("underground-spawn-failed"));
                    abortFight(dungeon.getId(), true);
                    plugin.getDungeonManager().updateShulkerHolograms(dungeon);
                    continue;
                }
                announceWave(dungeon, nextWave);
                plugin.getDungeonManager().updateShulkerHolograms(dungeon);
            }
            refreshFightVisuals(fight);
        }
    }

    private void unlockUndergroundShulkersAndCooldown(World world, ActiveFight fight) {
        DungeonInstance dungeon = fight.dungeon;
        fightsByDungeon.remove(dungeon.getId());
        cleanupEntities(fight);
        fight.bossBar.removeAll();
        dungeon.setStatus(DungeonStatus.CLEARED);

        int stacks = Math.max(3, plugin.getConfig().getInt("underground-dungeon.shulker-stacks", 7));
        for (BlockPosKey shulkerPos : new ArrayList<>(dungeon.getShulkerUnlocked().keySet())) {
            dungeon.getShulkerUnlocked().put(shulkerPos, Boolean.TRUE);
            Block block = world.getBlockAt(shulkerPos.x(), shulkerPos.y(), shulkerPos.z());
            if (!(block.getState() instanceof InventoryHolder)) {
                block.setBlockData(Bukkit.createBlockData(Material.PURPLE_SHULKER_BOX), false);
            }
            if (block.getState() instanceof InventoryHolder holder) {
                plugin.getLootRegistry().fillContainerRandomSlots(
                        holder.getInventory(), LootPoolType.UNDERGROUND_SHULKER, stacks);
            }
            Location location = shulkerPos.centerLocation();
            world.strikeLightningEffect(location);
            world.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            world.playSound(location, Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0, 0.9, 0), 40,
                    0.5, 0.5, 0.5, 0.14);
        }

        plugin.getDungeonManager().updateShulkerHolograms(dungeon);
        notifyNearby(dungeon, 45.0, plugin.msg("underground-completed"));
        plugin.getDungeonManager().saveDungeons();
    }

    public void abortFight(String dungeonId, boolean markReady) {
        ActiveFight fight = fightsByDungeon.remove(dungeonId);
        if (fight == null) return;
        cleanupEntities(fight);
        fight.bossBar.removeAll();
        if (markReady) fight.dungeon.setStatus(DungeonStatus.READY);
    }

    public void shutdownAll() {
        for (String id : new ArrayList<>(fightsByDungeon.keySet())) abortFight(id, true);
    }

    private void cleanupEntities(ActiveFight fight) {
        World world = fight.dungeon.getOrigin().world();
        for (UUID uuid : new HashSet<>(fight.aliveMobs)) {
            mobToDungeon.remove(uuid);
            if (world != null) {
                Entity entity = world.getEntity(uuid);
                if (entity != null && entity.isValid()) entity.remove();
            }
        }
        fight.aliveMobs.clear();
    }

    private void pruneAndLeashMobs(World world, ActiveFight fight) {
        Location center = fight.dungeon.getHologramAnchor().centerLocation();
        double maxDistSq = Math.pow(Math.max(14, fight.dungeon.getBounds().sizeX() / 2.0 + 3.0), 2);
        Iterator<UUID> iterator = fight.aliveMobs.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Entity entity = world.getEntity(uuid);
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                iterator.remove();
                mobToDungeon.remove(uuid);
                continue;
            }
            Location mobLocation = living.getLocation();
            boolean outside = mobLocation.distanceSquared(center) > maxDistSq
                    || mobLocation.getY() < fight.dungeon.getOrigin().y() - 1;
            boolean suffocating = mobLocation.getBlock().getType().isOccluding()
                    && mobLocation.clone().add(0, 1, 0).getBlock().getType().isOccluding();
            if (outside || suffocating) {
                List<BlockPosKey> safePositions = chooseSpawnPositions(world, fight.dungeon, 1);
                if (!safePositions.isEmpty()) {
                    Location safe = safePositions.get(0).floorLocation();
                    living.setFallDistance(0f);
                    living.teleport(safe);
                    world.spawnParticle(Particle.PORTAL, safe.clone().add(0, 1.0, 0), 12,
                            0.3, 0.4, 0.3, 0.05);
                }
            }
            if (living instanceof Mob mob && (mob.getTarget() == null || mob.getTarget().isDead())) {
                Player nearest = findNearestPlayer(world, mobLocation, 26.0);
                if (nearest != null) mob.setTarget(nearest);
            }
        }
    }

    private void refreshFightVisuals(ActiveFight fight) {
        DungeonInstance dungeon = fight.dungeon;
        long secondsLeft = Math.max(0L, (fight.deadlineMs - System.currentTimeMillis()) / 1000L);
        int currentWaveTotal = Math.max(1, fight.currentWaveInitialMobCount);
        double progress = Math.max(0.03,
                Math.min(1.0, (double) fight.aliveMobs.size() / currentWaveTotal));
        fight.bossBar.setProgress(progress);

        String waveStatus;
        if (fight.nextWaveAtMs > 0L) {
            long waitSeconds = Math.max(0L,
                    (fight.nextWaveAtMs - System.currentTimeMillis() + 999L) / 1000L);
            waveStatus = "&fВолна " + (fight.waveIndex + 1) + "/" + WAVE_COUNT
                    + " завершена &8┃ &fСледующая через &#F8BEFB" + waitSeconds + "с";
        } else {
            waveStatus = "&fВолна " + (fight.waveIndex + 1) + "/" + WAVE_COUNT
                    + " &8┃ &fОсталось: &#F8BEFB" + fight.aliveMobs.size();
        }
        fight.bossBar.setTitle(TextUtil.color("&#89A0FFПодземный данж &8┃ " + waveStatus
                + " &8(&7" + TextUtil.formatShortClock(secondsLeft) + "&8)"));

        World world = dungeon.getOrigin().world();
        if (world != null) {
            Location center = dungeon.getHologramAnchor().centerLocation();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(world)
                        && player.getLocation().distanceSquared(center) <= 36.0 * 36.0) {
                    if (!fight.bossBar.getPlayers().contains(player)) fight.bossBar.addPlayer(player);
                } else {
                    fight.bossBar.removePlayer(player);
                }
            }
        }
    }

    private static void setAttr(LivingEntity living, Attribute attribute, double value) {
        if (attribute == null) return;
        AttributeInstance instance = ((Attributable) living).getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private static Player findNearestPlayer(World world, Location from, double radius) {
        Player best = null;
        double bestSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                    || player.getGameMode() == GameMode.CREATIVE) continue;
            double distanceSquared = player.getLocation().distanceSquared(from);
            if (distanceSquared < bestSquared) {
                bestSquared = distanceSquared;
                best = player;
            }
        }
        return best;
    }

    private static void notifyNearby(DungeonInstance dungeon, double radius, String message) {
        World world = dungeon.getOrigin().world();
        if (world == null) return;
        Location center = dungeon.getHologramAnchor().centerLocation();
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSquared) player.sendMessage(message);
        }
    }
}
