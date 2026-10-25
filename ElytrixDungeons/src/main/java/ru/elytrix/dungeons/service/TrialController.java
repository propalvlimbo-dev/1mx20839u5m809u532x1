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
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.LootPoolType;
import ru.elytrix.dungeons.util.TextUtil;

public final class TrialController {

    public static final class ActiveFight {
        public final DungeonInstance dungeon;
        public final BossBar bossBar;
        public final int initialMobCount;
        public final long deadlineMs;
        public final Set<UUID> aliveMobs = new HashSet<>();

        ActiveFight(DungeonInstance dungeon, BossBar bossBar, int initialMobCount, long timeoutSec) {
            this.dungeon = dungeon;
            this.bossBar = bossBar;
            this.initialMobCount = Math.max(1, initialMobCount);
            this.deadlineMs = System.currentTimeMillis() + timeoutSec * 1000L;
        }
    }

    private final ElytrixDungeonsPlugin plugin;
    private final NamespacedKey trialMobPdc;
    private final Map<String, ActiveFight> fightsByDungeon = new HashMap<>();
    private final Map<UUID, String> mobToDungeon = new HashMap<>();

    public TrialController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.trialMobPdc = new NamespacedKey(plugin, "dungeon_mob");
    }

    public ActiveFight getFight(String dungeonId) {
        return fightsByDungeon.get(dungeonId);
    }

    public boolean isTrialMob(Entity entity) {
        return entity != null && (mobToDungeon.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(trialMobPdc, PersistentDataType.STRING));
    }

    public void startUndergroundFight(Player starter, DungeonInstance dungeon) {
        ActiveFight running = fightsByDungeon.get(dungeon.getId());
        if (running != null) {
            starter.sendMessage(plugin.msg("underground-already-active")
                    .replace("{mobs}", String.valueOf(running.aliveMobs.size())));
            return;
        }
        World w = dungeon.getOrigin().world();
        if (w == null) {
            return;
        }

        int guards = Math.max(2, plugin.getConfig().getInt("underground-dungeon.mob-count", 5));
        boolean withLeader = plugin.getConfig().getBoolean("underground-dungeon.spawn-leader", true);
        int totalExpected = guards + (withLeader ? 1 : 0);
        long timeoutSec = Math.max(90L, plugin.getConfig().getLong("underground-dungeon.fight-timeout-seconds", 210L));

        BossBar bar = Bukkit.createBossBar(
                TextUtil.color("&#89A0FFПодземный данж &8┃ &fОсталось стражей: &#F8BEFB" + totalExpected),
                BarColor.BLUE,
                BarStyle.SEGMENTED_10
        );
        bar.setProgress(1.0);

        ActiveFight fight = new ActiveFight(dungeon, bar, totalExpected, timeoutSec);
        fightsByDungeon.put(dungeon.getId(), fight);
        dungeon.setStatus(DungeonStatus.ACTIVE_TRIAL);

        spawnUndergroundSquad(w, dungeon, fight, guards, withLeader);

        Location center = dungeon.getHologramAnchor().centerLocation();
        w.playSound(center, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 1.0f, 0.95f);
        w.playSound(center, Sound.ENTITY_WARDEN_NEARBY_CLOSER, 0.9f, 1.0f);
        w.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0, 0.8, 0), 30, 0.6, 0.4, 0.6, 0.03);

        notifyNearby(dungeon, 40.0, plugin.msg("underground-started").replace("{player}", starter.getName()));
        refreshFightVisuals(fight);
    }

    private void spawnUndergroundSquad(World w, DungeonInstance d, ActiveFight fight, int guards, boolean withLeader) {
        List<BlockPosKey> pts = d.getMobSpawns().isEmpty()
                ? List.of(d.getEntrancePos() != null ? d.getEntrancePos() : d.getHologramAnchor())
                : d.getMobSpawns();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double guardHp = plugin.getConfig().getDouble("underground-dungeon.guard-health", 64.0);
        double guardDmg = plugin.getConfig().getDouble("underground-dungeon.guard-damage", 14.5);
        double leaderHp = plugin.getConfig().getDouble("underground-dungeon.leader-health", 150.0);
        double leaderDmg = plugin.getConfig().getDouble("underground-dungeon.leader-damage", 18.5);

        for (int i = 0; i < guards; i++) {
            BlockPosKey sp = pts.get(i % pts.size());
            boolean archer = (i % 3 == 2);
            EntityType type = archer
                    ? (rng.nextBoolean() ? EntityType.BOGGED : EntityType.STRAY)
                    : (i % 2 == 0 ? EntityType.WITHER_SKELETON : EntityType.VINDICATOR);
            spawnSingleFighter(w, d, fight, sp.floorLocation(), type, false, guardHp, guardDmg, archer);
        }
        if (withLeader) {
            BlockPosKey sp = pts.get(0);
            spawnSingleFighter(w, d, fight, sp.floorLocation(), EntityType.WITHER_SKELETON, true, leaderHp, leaderDmg, false);
        }
    }

    private void spawnSingleFighter(
            World w,
            DungeonInstance d,
            ActiveFight fight,
            Location loc,
            EntityType type,
            boolean leader,
            double hp,
            double attackDamage,
            boolean archer
    ) {
        Entity raw = w.spawnEntity(loc, type);
        if (!(raw instanceof LivingEntity le)) {
            raw.remove();
            return;
        }
        le.getPersistentDataContainer().set(trialMobPdc, PersistentDataType.STRING, d.getId());
        le.setPersistent(false);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        le.setCustomNameVisible(true);

        setAttr(le, Attribute.MAX_HEALTH, hp);
        le.setHealth(hp);
        setAttr(le, Attribute.ATTACK_DAMAGE, attackDamage);
        setAttr(le, Attribute.ARMOR, leader ? 18.0 : 12.0);
        setAttr(le, Attribute.ARMOR_TOUGHNESS, leader ? 8.0 : 4.0);
        setAttr(le, Attribute.FOLLOW_RANGE, 32.0);
        setAttr(le, Attribute.KNOCKBACK_RESISTANCE, leader ? 0.65 : 0.30);

        if (leader) {
            setAttr(le, Attribute.SCALE, 1.24);
            le.setCustomName(TextUtil.color("&#F8BEFBКаратель Подземелья &8[&cБосс&8]"));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 300, 0, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 300, 0, false, false));
        } else {
            le.setCustomName(TextUtil.color(archer ? "&#89A0FFПодземный Стрелок" : "&#89A0FFСтраж Шалкера"));
            if (!archer) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 300, 0, false, false));
            }
        }
        le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 300, 0, false, false));

        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            if (leader) {
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET), true);
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE), true);
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS), true);
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS), true);
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE), true);
            } else {
                eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET), true);
                eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE), true);
                if (archer) {
                    eq.setItemInMainHand(VaultAndItemsHook.enchantedGear(Material.BOW, null,
                            new MapEnchant(Enchantment.POWER, 3), new MapEnchant(Enchantment.PUNCH, 1)), true);
                } else {
                    eq.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD), true);
                }
            }
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }

        Player target = findNearestPlayer(w, loc, 26.0);
        if (target != null && le instanceof Mob mob) {
            mob.setTarget(target);
        }
        fight.aliveMobs.add(le.getUniqueId());
        mobToDungeon.put(le.getUniqueId(), d.getId());
    }

    public void handleMobDeath(UUID mobId) {
        String dungeonId = mobToDungeon.remove(mobId);
        if (dungeonId == null) return;
        ActiveFight fight = fightsByDungeon.get(dungeonId);
        if (fight == null) return;
        fight.aliveMobs.remove(mobId);

        World w = fight.dungeon.getOrigin().world();
        if (fight.aliveMobs.isEmpty()) {
            if (w != null) {
                unlockUndergroundShulkersAndCooldown(w, fight);
            }
        } else {
            refreshFightVisuals(fight);
        }
    }

    public void tickOncePerSecond() {
        long now = System.currentTimeMillis();
        for (ActiveFight fight : new ArrayList<>(fightsByDungeon.values())) {
            DungeonInstance d = fight.dungeon;
            World w = d.getOrigin().world();
            if (w == null) {
                abortFight(d.getId(), false);
                continue;
            }
            if (now >= fight.deadlineMs) {
                notifyNearby(d, 40.0, plugin.msg("underground-timeout"));
                abortFight(d.getId(), true);
                continue;
            }
            pruneAndLeashMobs(w, fight);
            if (fight.aliveMobs.isEmpty()) {
                unlockUndergroundShulkersAndCooldown(w, fight);
                continue;
            }
            refreshFightVisuals(fight);
        }
    }

    private void unlockUndergroundShulkersAndCooldown(World w, ActiveFight fight) {
        DungeonInstance d = fight.dungeon;
        fightsByDungeon.remove(d.getId());
        cleanupEntities(fight);
        fight.bossBar.removeAll();
        d.setStatus(DungeonStatus.CLEARED);

        int stacks = Math.max(3, plugin.getConfig().getInt("underground-dungeon.shulker-stacks", 7));
        for (BlockPosKey shulkerPos : new ArrayList<>(d.getShulkerUnlocked().keySet())) {
            d.getShulkerUnlocked().put(shulkerPos, Boolean.TRUE);
            Block b = w.getBlockAt(shulkerPos.x(), shulkerPos.y(), shulkerPos.z());
            if (!(b.getState() instanceof InventoryHolder)) {
                b.setBlockData(Bukkit.createBlockData(Material.PURPLE_SHULKER_BOX), false);
            }
            if (b.getState() instanceof InventoryHolder holder) {
                plugin.getLootRegistry().fillContainerRandomSlots(holder.getInventory(), LootPoolType.UNDERGROUND_SHULKER, stacks);
            }
            Location c = shulkerPos.centerLocation();
            w.strikeLightningEffect(c);
            w.playSound(c, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            w.playSound(c, Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, c.clone().add(0, 0.9, 0), 40, 0.5, 0.5, 0.5, 0.14);
        }

        plugin.getDungeonManager().updateShulkerHolograms(d);
        notifyNearby(d, 45.0, plugin.msg("underground-completed"));
        plugin.getDungeonManager().saveDungeons();
    }

    public void abortFight(String dungeonId, boolean markReady) {
        ActiveFight fight = fightsByDungeon.remove(dungeonId);
        if (fight == null) return;
        cleanupEntities(fight);
        fight.bossBar.removeAll();
        if (markReady) {
            fight.dungeon.setStatus(DungeonStatus.READY);
        }
    }

    public void shutdownAll() {
        for (String id : new ArrayList<>(fightsByDungeon.keySet())) {
            abortFight(id, true);
        }
    }

    private void cleanupEntities(ActiveFight fight) {
        World w = fight.dungeon.getOrigin().world();
        for (UUID uid : new HashSet<>(fight.aliveMobs)) {
            mobToDungeon.remove(uid);
            if (w != null) {
                Entity e = w.getEntity(uid);
                if (e != null && e.isValid()) e.remove();
            }
        }
        fight.aliveMobs.clear();
    }

    private void pruneAndLeashMobs(World w, ActiveFight fight) {
        Location center = fight.dungeon.getHologramAnchor().centerLocation();
        double maxDistSq = Math.pow(Math.max(14, fight.dungeon.getBounds().sizeX() / 2.0 + 3.0), 2);
        Iterator<UUID> it = fight.aliveMobs.iterator();
        while (it.hasNext()) {
            UUID uid = it.next();
            Entity e = w.getEntity(uid);
            if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                it.remove();
                mobToDungeon.remove(uid);
                continue;
            }
            Location ml = le.getLocation();
            boolean outside = ml.distanceSquared(center) > maxDistSq || ml.getY() < fight.dungeon.getOrigin().y() - 1;
            boolean suffocating = ml.getBlock().getType().isOccluding() && ml.clone().add(0, 1, 0).getBlock().getType().isOccluding();
            if (outside || suffocating) {
                Location safe = fight.dungeon.teleportLocation();
                if (safe != null) {
                    le.setFallDistance(0f);
                    le.teleport(safe);
                    w.spawnParticle(Particle.PORTAL, safe.clone().add(0, 1.0, 0), 12, 0.3, 0.4, 0.3, 0.05);
                }
            }
            if (le instanceof Mob mob && (mob.getTarget() == null || mob.getTarget().isDead())) {
                Player nearest = findNearestPlayer(w, ml, 26.0);
                if (nearest != null) mob.setTarget(nearest);
            }
        }
    }

    private void refreshFightVisuals(ActiveFight fight) {
        DungeonInstance d = fight.dungeon;
        long secLeft = Math.max(0L, (fight.deadlineMs - System.currentTimeMillis()) / 1000L);
        double prog = Math.max(0.03, Math.min(1.0, (double) fight.aliveMobs.size() / fight.initialMobCount));
        fight.bossBar.setProgress(prog);
        fight.bossBar.setTitle(TextUtil.color("&#89A0FFПодземный данж &8┃ &fОсталось мобов: &#F8BEFB"
                + fight.aliveMobs.size() + " &8(&7" + TextUtil.formatShortClock(secLeft) + "&8)"));

        World w = d.getOrigin().world();
        if (w != null) {
            Location c = d.getHologramAnchor().centerLocation();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(w) && p.getLocation().distanceSquared(c) <= 36.0 * 36.0) {
                    if (!fight.bossBar.getPlayers().contains(p)) fight.bossBar.addPlayer(p);
                } else {
                    fight.bossBar.removePlayer(p);
                }
            }
        }
    }

    private static void setAttr(LivingEntity le, Attribute attr, double val) {
        if (attr == null) return;
        AttributeInstance inst = ((Attributable) le).getAttribute(attr);
        if (inst != null) inst.setBaseValue(val);
    }

    private static Player findNearestPlayer(World w, Location from, double radius) {
        Player best = null;
        double bestSq = radius * radius;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            double d = p.getLocation().distanceSquared(from);
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    private static void notifyNearby(DungeonInstance d, double radius, String message) {
        World w = d.getOrigin().world();
        if (w == null) return;
        Location c = d.getHologramAnchor().centerLocation();
        double rSq = radius * radius;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= rSq) {
                p.sendMessage(message);
            }
        }
    }
}
