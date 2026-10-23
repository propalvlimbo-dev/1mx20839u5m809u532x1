package ru.elytrix.dungeons.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
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
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.TrialType;
import ru.elytrix.dungeons.util.TextUtil;

public final class TrialController {

    public static final class ActiveTrial {
        public final DungeonInstance dungeon;
        public final BossBar bossBar;
        public final long startedMs;
        public final long deadlineMs;
        public final Set<UUID> aliveMobs = new HashSet<>();
        public final Set<BlockPosKey> activatedSeals = new HashSet<>();
        public int currentWave = 0;
        public final int totalWaves = 3;
        public double captureSeconds = 0.0;
        public double requiredCaptureSeconds = 45.0;
        public boolean bossPhaseSpawned = false;
        public UUID bossUuid;
        public String liveStatusLine = "&fПодготовка испытания...";

        ActiveTrial(DungeonInstance dungeon, BossBar bossBar, long timeoutSec, double reqCapSec) {
            this.dungeon = dungeon;
            this.bossBar = bossBar;
            this.startedMs = System.currentTimeMillis();
            this.deadlineMs = this.startedMs + timeoutSec * 1000L;
            this.requiredCaptureSeconds = reqCapSec;
        }
    }

    private final ElytrixDungeonsPlugin plugin;
    private final NamespacedKey trialMobPdc;
    private final Map<String, ActiveTrial> trialsByDungeonId = new HashMap<>();
    private final Map<UUID, String> mobToDungeonId = new HashMap<>();

    public TrialController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.trialMobPdc = new NamespacedKey(plugin, "trial_mob");
    }

    public NamespacedKey getTrialMobPdc() {
        return trialMobPdc;
    }

    public ActiveTrial getTrial(String dungeonId) {
        return trialsByDungeonId.get(dungeonId);
    }

    public boolean isTrialMob(Entity entity) {
        return entity != null && (mobToDungeonId.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(trialMobPdc, PersistentDataType.STRING));
    }

    public void startTrial(Player starter, DungeonInstance dungeon) {
        if (trialsByDungeonId.containsKey(dungeon.getId())) {
            starter.sendMessage(plugin.msg("trial-already-running"));
            return;
        }
        if (dungeon.getStatus() == DungeonStatus.CLEARED) {
            starter.sendMessage(plugin.msg("trial-already-cleared")
                    .replace("{timer}", TextUtil.formatDuration(dungeon.secondsUntilReset())));
            return;
        }

        World w = dungeon.getOrigin().world();
        if (w == null) {
            return;
        }

        long timeoutSec = Math.max(90L, plugin.getConfig().getLong("trials.timeout-seconds", 210L));
        double capSec = Math.max(20.0, plugin.getConfig().getDouble("trials.capture-required-seconds", 45.0));

        BossBar bar = Bukkit.createBossBar(
                TextUtil.color("&#F8BEFB" + dungeon.getDisplayName() + " &8┃ &f" + dungeon.getTrialType().getTitle()),
                BarColor.PURPLE,
                BarStyle.SEGMENTED_10
        );
        bar.setProgress(0.05);

        ActiveTrial trial = new ActiveTrial(dungeon, bar, timeoutSec, capSec);
        trialsByDungeonId.put(dungeon.getId(), trial);
        dungeon.setStatus(DungeonStatus.ACTIVE_TRIAL);

        Location altarCenter = dungeon.getAltarPos().centerLocation();
        w.playSound(altarCenter, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 1.0f, 1.0f);
        w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, altarCenter.clone().add(0, 0.7, 0), 28, 0.5, 0.4, 0.5, 0.02);

        String announce = plugin.msg("trial-started")
                .replace("{player}", starter.getName())
                .replace("{name}", dungeon.getDisplayName());
        notifyNearbyPlayers(dungeon, 40.0, announce);

        if (dungeon.getTrialType() == TrialType.SEALS && !dungeon.getSealPositions().isEmpty()) {
            resetSealsVisual(w, dungeon, 0);
            spawnGuardianPack(w, dungeon, trial, 3 + dungeon.getTier() / 2, false);
        } else if (dungeon.getTrialType() == TrialType.CAPTURE) {
            spawnGuardianPack(w, dungeon, trial, 3, false);
        } else {
            advanceWaveOrComplete(w, trial);
        }
        updateTrialVisuals(trial);
    }

    public boolean tryActivateSeal(Player player, DungeonInstance dungeon, BlockPosKey clickedPos) {
        ActiveTrial trial = trialsByDungeonId.get(dungeon.getId());
        if (trial == null || dungeon.getTrialType() != TrialType.SEALS) {
            return false;
        }
        if (!dungeon.getSealPositions().contains(clickedPos)) {
            return false;
        }
        if (trial.activatedSeals.contains(clickedPos)) {
            return true;
        }
        World w = clickedPos.world();
        if (w == null) {
            return true;
        }
        trial.activatedSeals.add(clickedPos);
        Block b = w.getBlockAt(clickedPos.x(), clickedPos.y(), clickedPos.z());
        if (b.getType() == Material.RESPAWN_ANCHOR && b.getBlockData() instanceof RespawnAnchor ra) {
            ra.setCharges(ra.getMaximumCharges());
            b.setBlockData(ra, false);
        }
        Location c = clickedPos.centerLocation();
        w.playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.15f);
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.8, 0), 22, 0.35, 0.45, 0.35, 0.03);

        int done = trial.activatedSeals.size();
        int total = dungeon.getSealPositions().size();
        notifyNearbyPlayers(dungeon, 36.0, plugin.msg("trial-seal-activated")
                .replace("{done}", String.valueOf(done))
                .replace("{total}", String.valueOf(total)));

        if (done < total) {
            spawnGuardianPack(w, dungeon, trial, 2, false);
        } else if (!trial.bossPhaseSpawned) {
            trial.bossPhaseSpawned = true;
            spawnGuardianPack(w, dungeon, trial, 1, true);
        }
        updateTrialVisuals(trial);
        return true;
    }

    public void handleMobDeath(UUID mobId) {
        String dungeonId = mobToDungeonId.remove(mobId);
        if (dungeonId == null) {
            return;
        }
        ActiveTrial trial = trialsByDungeonId.get(dungeonId);
        if (trial == null) {
            return;
        }
        trial.aliveMobs.remove(mobId);
        World w = trial.dungeon.getOrigin().world();
        if (w == null) {
            return;
        }
        if (trial.dungeon.getTrialType() == TrialType.WAVES || trial.dungeon.getSealPositions().isEmpty() && trial.dungeon.getTrialType() == TrialType.SEALS) {
            if (trial.aliveMobs.isEmpty()) {
                advanceWaveOrComplete(w, trial);
            }
        } else if (trial.dungeon.getTrialType() == TrialType.SEALS) {
            if (trial.bossPhaseSpawned && trial.aliveMobs.isEmpty()) {
                completeTrial(w, trial);
                return;
            }
        }
        updateTrialVisuals(trial);
    }

    public void tickAllOncePerSecond() {
        long now = System.currentTimeMillis();
        for (ActiveTrial trial : new ArrayList<>(trialsByDungeonId.values())) {
            DungeonInstance d = trial.dungeon;
            World w = d.getOrigin().world();
            if (w == null) {
                abortTrial(d.getId(), false);
                continue;
            }
            if (now >= trial.deadlineMs) {
                notifyNearbyPlayers(d, 40.0, plugin.msg("trial-timeout"));
                abortTrial(d.getId(), true);
                continue;
            }

            pruneAndLeashMobs(w, trial);

            if (d.getTrialType() == TrialType.CAPTURE) {
                tickCaptureTrial(w, trial);
                if (!trialsByDungeonId.containsKey(d.getId())) {
                    continue;
                }
            } else if (d.getTrialType() == TrialType.SEALS) {
                for (BlockPosKey seal : d.getSealPositions()) {
                    if (!trial.activatedSeals.contains(seal) && w.isChunkLoaded(seal.x() >> 4, seal.z() >> 4)) {
                        w.spawnParticle(Particle.WITCH, seal.centerLocation().add(0, 0.85, 0), 5, 0.2, 0.25, 0.2, 0.01);
                    }
                }
                if (trial.bossPhaseSpawned && trial.aliveMobs.isEmpty()) {
                    completeTrial(w, trial);
                    continue;
                }
            } else {
                if (trial.aliveMobs.isEmpty()) {
                    advanceWaveOrComplete(w, trial);
                    if (!trialsByDungeonId.containsKey(d.getId())) {
                        continue;
                    }
                }
            }
            updateTrialVisuals(trial);
        }
    }

    private void tickCaptureTrial(World w, ActiveTrial trial) {
        DungeonInstance d = trial.dungeon;
        Location center = d.getAltarPos().centerLocation();
        double radius = plugin.getConfig().getDouble("trials.capture-radius", 7.0);
        int holders = 0;
        for (Player p : w.getPlayers()) {
            if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && p.getLocation().distanceSquared(center) <= radius * radius) {
                holders++;
            }
        }
        for (int a = 0; a < 10; a++) {
            double ang = (Math.PI * 2.0 * a) / 10.0;
            Location ringPt = center.clone().add(Math.cos(ang) * radius, 0.15, Math.sin(ang) * radius);
            w.spawnParticle(holders > 0 ? Particle.END_ROD : Particle.SMOKE, ringPt, 1, 0, 0, 0, 0.0);
        }
        if (holders > 0) {
            double before = trial.captureSeconds;
            trial.captureSeconds = Math.min(trial.requiredCaptureSeconds, trial.captureSeconds + 1.0);
            if (((int) trial.captureSeconds / 14) > ((int) before / 14) && trial.aliveMobs.size() < 6) {
                spawnGuardianPack(w, d, trial, 2 + d.getTier() / 2, trial.captureSeconds >= trial.requiredCaptureSeconds * 0.65 && !trial.bossPhaseSpawned);
                if (trial.captureSeconds >= trial.requiredCaptureSeconds * 0.65) {
                    trial.bossPhaseSpawned = true;
                }
            }
            if (trial.captureSeconds >= trial.requiredCaptureSeconds && trial.aliveMobs.isEmpty()) {
                completeTrial(w, trial);
            }
        } else {
            trial.captureSeconds = Math.max(0.0, trial.captureSeconds - 0.5);
        }
    }

    private void advanceWaveOrComplete(World w, ActiveTrial trial) {
        trial.currentWave++;
        if (trial.currentWave > trial.totalWaves) {
            completeTrial(w, trial);
            return;
        }
        int tier = trial.dungeon.getTier();
        if (trial.currentWave < trial.totalWaves) {
            spawnGuardianPack(w, trial.dungeon, trial, 3 + tier / 2 + trial.currentWave, false);
        } else {
            trial.bossPhaseSpawned = true;
            spawnGuardianPack(w, trial.dungeon, trial, 2, false);
            spawnGuardianPack(w, trial.dungeon, trial, 1, true);
        }
        w.playSound(trial.dungeon.getAltarPos().centerLocation(), Sound.EVENT_RAID_HORN, 0.85f, 1.25f);
    }

    private void completeTrial(World w, ActiveTrial trial) {
        DungeonInstance d = trial.dungeon;
        trialsByDungeonId.remove(d.getId());
        cleanupTrialEntities(trial);
        trial.bossBar.removeAll();
        d.setStatus(DungeonStatus.CLEARED);

        Location center = d.getAltarPos().centerLocation();
        w.strikeLightningEffect(center);
        w.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        w.playSound(center, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.0f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 1.0, 0), 45, 0.7, 0.6, 0.7, 0.15);

        // Unlock and populate all reward chests (restoring the chest block first if damaged)
        int stacksPerChest = 4 + d.getTier();
        for (BlockPosKey chestPos : d.getRewardChests()) {
            Block b = w.getBlockAt(chestPos.x(), chestPos.y(), chestPos.z());
            if (!(b.getState() instanceof InventoryHolder)) {
                b.setBlockData(Bukkit.createBlockData(Material.CHEST), false);
            }
            if (b.getState() instanceof InventoryHolder holder) {
                plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), d.getTier(), true, stacksPerChest);
            }
            w.spawnParticle(Particle.END_ROD, chestPos.centerLocation().add(0, 0.75, 0), 16, 0.25, 0.3, 0.25, 0.03);
        }

        notifyNearbyPlayers(d, 48.0, plugin.msg("trial-completed").replace("{name}", d.getDisplayName()));
        plugin.getDungeonManager().saveDungeons();
        plugin.getDungeonManager().updateDungeonHologram(d);
    }

    public void abortTrial(String dungeonId, boolean setReady) {
        ActiveTrial trial = trialsByDungeonId.remove(dungeonId);
        if (trial == null) {
            return;
        }
        cleanupTrialEntities(trial);
        trial.bossBar.removeAll();
        World w = trial.dungeon.getOrigin().world();
        if (w != null && !trial.dungeon.getSealPositions().isEmpty()) {
            resetSealsVisual(w, trial.dungeon, 0);
        }
        if (setReady) {
            trial.dungeon.setStatus(DungeonStatus.READY);
            plugin.getDungeonManager().updateDungeonHologram(trial.dungeon);
        }
    }

    public void shutdownAll() {
        for (String id : new ArrayList<>(trialsByDungeonId.keySet())) {
            abortTrial(id, true);
        }
    }

    private void cleanupTrialEntities(ActiveTrial trial) {
        World w = trial.dungeon.getOrigin().world();
        for (UUID uid : new HashSet<>(trial.aliveMobs)) {
            mobToDungeonId.remove(uid);
            if (w != null) {
                Entity e = w.getEntity(uid);
                if (e != null && e.isValid()) {
                    e.remove();
                }
            }
        }
        trial.aliveMobs.clear();
    }

    private void pruneAndLeashMobs(World w, ActiveTrial trial) {
        Location altar = trial.dungeon.getAltarPos().centerLocation();
        double maxDistSq = Math.pow(Math.max(13, trial.dungeon.getBounds().sizeX() / 2.0 + 2.0), 2);
        Iterator<UUID> it = trial.aliveMobs.iterator();
        while (it.hasNext()) {
            UUID uid = it.next();
            Entity e = w.getEntity(uid);
            if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                it.remove();
                mobToDungeonId.remove(uid);
                continue;
            }
            Location ml = le.getLocation();
            boolean outOfArena = ml.distanceSquared(altar) > maxDistSq || ml.getY() < trial.dungeon.getBounds().minY() - 1;
            boolean suffocating = ml.getBlock().getType().isOccluding() && ml.clone().add(0, 1, 0).getBlock().getType().isOccluding();
            if (outOfArena || suffocating) {
                Location safe = trial.dungeon.teleportLocation();
                if (safe != null) {
                    le.setFallDistance(0f);
                    le.teleport(safe);
                    w.spawnParticle(Particle.PORTAL, safe.clone().add(0, 1.0, 0), 14, 0.3, 0.5, 0.3, 0.05);
                }
            }
            if (le instanceof Mob mob && (mob.getTarget() == null || mob.getTarget().isDead())) {
                Player nearest = findNearestPlayer(w, le.getLocation(), 24.0);
                if (nearest != null) {
                    mob.setTarget(nearest);
                }
            }
        }
    }

    private void spawnGuardianPack(World w, DungeonInstance d, ActiveTrial trial, int count, boolean boss) {
        List<BlockPosKey> pts = d.getMobSpawns().isEmpty()
                ? List.of(d.getEntrancePos() != null ? d.getEntrancePos() : d.getAltarPos())
                : d.getMobSpawns();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int tier = d.getTier();

        for (int i = 0; i < count; i++) {
            BlockPosKey sp = pts.get(rng.nextInt(pts.size()));
            Location loc = sp.floorLocation();
            EntityType type = chooseEntityType(d.getCategory(), tier, boss, rng);
            Entity raw = w.spawnEntity(loc, type);
            if (!(raw instanceof LivingEntity le)) {
                raw.remove();
                continue;
            }
            le.getPersistentDataContainer().set(trialMobPdc, PersistentDataType.STRING, d.getId());
            le.setPersistent(false);
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            le.setCustomNameVisible(true);

            double hp = boss ? (70.0 + tier * 42.0) : (24.0 + tier * 11.0);
            double dmg = boss ? (7.0 + tier * 2.4) : (3.5 + tier * 1.4);
            setAttr(le, Attribute.MAX_HEALTH, hp);
            le.setHealth(hp);
            setAttr(le, Attribute.ATTACK_DAMAGE, dmg);
            setAttr(le, Attribute.FOLLOW_RANGE, 32.0);
            setAttr(le, Attribute.KNOCKBACK_RESISTANCE, boss ? 0.65 : 0.25);
            if (boss) {
                setAttr(le, Attribute.SCALE, 1.24);
                le.setCustomName(TextUtil.color("&#F8BEFBХранитель Данжа &8[&7" + tier + "★&8]"));
                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 240, 0, false, false));
                trial.bossUuid = le.getUniqueId();
            } else {
                le.setCustomName(TextUtil.color("&7Страж Данжа &8[&f" + tier + "★&8]"));
            }
            le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 300, 0, false, false));
            equipGuardian(le, tier, boss);

            Player nearest = findNearestPlayer(w, loc, 28.0);
            if (nearest != null && le instanceof Mob mob) {
                mob.setTarget(nearest);
            }
            trial.aliveMobs.add(le.getUniqueId());
            mobToDungeonId.put(le.getUniqueId(), d.getId());
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1.0, 0), 12, 0.25, 0.45, 0.25, 0.02);
        }
    }

    private static EntityType chooseEntityType(DungeonCategory cat, int tier, boolean boss, ThreadLocalRandom rng) {
        if (boss) {
            return switch (cat) {
                case SURFACE -> tier >= 3 ? EntityType.VINDICATOR : EntityType.WITHER_SKELETON;
                case UNDERGROUND -> EntityType.WITHER_SKELETON;
                case SKY -> EntityType.WITHER_SKELETON;
            };
        }
        return switch (cat) {
            case SURFACE -> rng.nextBoolean() ? EntityType.ZOMBIE : EntityType.STRAY;
            case UNDERGROUND -> rng.nextBoolean() ? EntityType.BOGGED : EntityType.WITHER_SKELETON;
            case SKY -> rng.nextInt(3) == 0 ? EntityType.BREEZE : EntityType.STRAY;
        };
    }

    private static void equipGuardian(LivingEntity le, int tier, boolean boss) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        Material helm = tier >= 4 ? Material.NETHERITE_HELMET : (tier >= 2 ? Material.DIAMOND_HELMET : Material.IRON_HELMET);
        Material chest = tier >= 4 ? Material.NETHERITE_CHESTPLATE : (tier >= 2 ? Material.DIAMOND_CHESTPLATE : Material.IRON_CHESTPLATE);
        eq.setHelmet(new ItemStack(helm), true);
        if (boss || tier >= 3) {
            eq.setChestplate(new ItemStack(chest), true);
        }
        if (le.getType() != EntityType.STRAY && le.getType() != EntityType.BOGGED && le.getType() != EntityType.BREEZE) {
            Material weapon = boss ? Material.NETHERITE_AXE : (tier >= 3 ? Material.DIAMOND_SWORD : Material.IRON_SWORD);
            eq.setItemInMainHand(new ItemStack(weapon), true);
        }
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    private static void setAttr(LivingEntity le, Attribute attr, double value) {
        if (attr == null) return;
        AttributeInstance inst = ((Attributable) le).getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private void updateTrialVisuals(ActiveTrial trial) {
        DungeonInstance d = trial.dungeon;
        long leftSec = Math.max(0L, (trial.deadlineMs - System.currentTimeMillis()) / 1000L);
        String clock = TextUtil.formatShortClock(leftSec);
        double progress;

        if (d.getTrialType() == TrialType.CAPTURE) {
            int pct = (int) Math.min(100, Math.round((trial.captureSeconds / trial.requiredCaptureSeconds) * 100.0));
            progress = Math.max(0.02, Math.min(1.0, trial.captureSeconds / trial.requiredCaptureSeconds));
            trial.liveStatusLine = "&fУдержание: &#F8BEFB" + pct + "% &8┃ &fВрагов: &#F8BEFB" + trial.aliveMobs.size() + " &8(&7" + clock + "&8)";
        } else if (d.getTrialType() == TrialType.SEALS) {
            int done = trial.activatedSeals.size();
            int total = Math.max(1, d.getSealPositions().size());
            progress = Math.max(0.05, Math.min(1.0, (double) done / total));
            trial.liveStatusLine = trial.bossPhaseSpawned
                    ? "&cПобедите Хранителя Данжа! &8(&7" + clock + "&8)"
                    : "&fПечати: &#F8BEFB" + done + "/" + total + " &8┃ &fСтражей: &#F8BEFB" + trial.aliveMobs.size() + " &8(&7" + clock + "&8)";
        } else {
            progress = Math.max(0.05, Math.min(1.0, (trial.currentWave - 0.5) / trial.totalWaves));
            trial.liveStatusLine = "&fВолна &#F8BEFB" + trial.currentWave + "/" + trial.totalWaves
                    + " &8┃ &fВрагов: &#F8BEFB" + trial.aliveMobs.size() + " &8(&7" + clock + "&8)";
        }

        trial.bossBar.setProgress(progress);
        trial.bossBar.setTitle(TextUtil.color("&#F8BEFB" + d.getDisplayName() + " &8┃ " + trial.liveStatusLine));

        World w = d.getOrigin().world();
        if (w != null) {
            Location center = d.getAltarPos().centerLocation();
            double rSq = Math.pow(plugin.getConfig().getDouble("trials.bossbar-radius", 34.0), 2);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(w) && p.getLocation().distanceSquared(center) <= rSq) {
                    if (!trial.bossBar.getPlayers().contains(p)) {
                        trial.bossBar.addPlayer(p);
                    }
                } else {
                    trial.bossBar.removePlayer(p);
                }
            }
        }
        plugin.getDungeonManager().updateDungeonHologram(d);
    }

    private static void resetSealsVisual(World w, DungeonInstance d, int charges) {
        for (BlockPosKey p : d.getSealPositions()) {
            Block b = w.getBlockAt(p.x(), p.y(), p.z());
            if (b.getType() != Material.RESPAWN_ANCHOR) {
                b.setBlockData(Bukkit.createBlockData(Material.RESPAWN_ANCHOR), false);
            }
            if (b.getBlockData() instanceof RespawnAnchor ra) {
                ra.setCharges(Math.max(0, Math.min(ra.getMaximumCharges(), charges)));
                b.setBlockData(ra, false);
            }
        }
    }

    private static Player findNearestPlayer(World w, Location origin, double maxRadius) {
        Player best = null;
        double bestDistSq = maxRadius * maxRadius;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                continue;
            }
            double d = p.getLocation().distanceSquared(origin);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    private static void notifyNearbyPlayers(DungeonInstance d, double radius, String msg) {
        World w = d.getOrigin().world();
        if (w == null) return;
        Location c = d.getAltarPos().centerLocation();
        double rSq = radius * radius;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= rSq) {
                p.sendMessage(msg);
            }
        }
    }
}
