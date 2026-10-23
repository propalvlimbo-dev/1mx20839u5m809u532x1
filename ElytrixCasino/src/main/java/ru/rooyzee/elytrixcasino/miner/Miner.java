package ru.rooyzee.elytrixcasino.miner;

import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.entity.HumanoidArm;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.item.VirtualItem;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.by1337.blib.geom.Vec3d;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.util.SkinFetcher;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Пакетный NPC «Минёр» в скине динамита (ExplodingTNT):
 * - стоит на месте, держит динамит (TNT) в ОБЕИХ руках (MAINHAND и OFFHAND),
 * - раз в 3 секунды (60 тиков) поворачивается налево/направо и выбрасывает виртуальный TNT,
 * - над головой висит голограмма в точном стиле dHolograms ElytriX с живой волной WAVE.
 */
public final class Miner {

    private static final String HIDE_NAMETAG_TEAM = "ec_npc";
    // Все 3D-слои скина включены, плащ (0x01) выключен, чтобы на спине была видна текстура TNT
    private static final byte SKIN_LAYERS_NO_CAPE = 0x7E;

    private static final class ThrownTnt {
        final VirtualItem entity;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        final double groundY;
        boolean landed;
        int groundTicksLeft;

        ThrownTnt(VirtualItem entity,
                  double x, double y, double z,
                  double vx, double vy, double vz,
                  double groundY,
                  int groundLifetimeTicks) {
            this.entity = entity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.groundY = groundY;
            this.landed = false;
            this.groundTicksLeft = groundLifetimeTicks;
        }
    }

    private final Main plugin;
    private final String id;
    private final Location location;

    private VirtualPlayer npc;
    private PlayerTracker tracker;
    private BukkitTask tickTask;
    private boolean spawned;

    private final List<VirtualArmorStand> holograms = new ArrayList<>();
    private final List<String> hologramTemplates = new ArrayList<>();
    private final List<ThrownTnt> activeThrows = new ArrayList<>();

    private float baseYaw;
    private float currentYaw;
    private float targetYaw;
    private float currentPitch;
    private float targetPitch;
    private boolean nextTurnLeft = true;
    private int ticksUntilThrow;
    private int tickCounter;

    public Miner(Main plugin, String id, Location location) {
        this.plugin = plugin;
        this.id = id;
        this.location = location.clone();
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public boolean isSpawned() {
        return spawned;
    }

    public int getNpcEntityId() {
        return npc != null ? npc.getId() : -1;
    }

    public boolean spawn() {
        if (spawned) {
            despawn();
        }
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        MinerSettings settings = plugin.getMinerSettings();
        Vec3d origin = new Vec3d(location.getX(), location.getY(), location.getZ());
        this.tracker = new PlayerTracker(world, Collections.emptyList(), origin, settings.getViewDistance());

        this.baseYaw = location.getYaw();
        this.currentYaw = baseYaw;
        this.targetYaw = baseYaw;
        this.currentPitch = 0.0F;
        this.targetPitch = 0.0F;
        this.nextTurnLeft = true;
        this.ticksUntilThrow = settings.getThrowIntervalTicks();
        this.tickCounter = 0;

        String hiddenProfileName = buildHiddenName();
        ensureHiddenNametag(hiddenProfileName);

        VirtualPlayer created = VirtualPlayer.create();
        created.setName(hiddenProfileName);
        created.setPos(origin);
        created.setYaw(baseYaw);
        created.setPitch(0.0F);
        created.setNoGravity(true);
        created.setOnGround(true);
        created.setSilent(true);
        created.setListed(false);
        created.setPlayerMainHand(HumanoidArm.RIGHT);
        created.setPlayerModeCustomisation(SKIN_LAYERS_NO_CAPE);

        equipDualTnt(created);
        applyConfiguredSkin(created, settings);

        this.npc = created;
        tracker.addEntity(created);

        spawnHologram(settings);

        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        this.spawned = true;
        return true;
    }

    private void equipDualTnt(VirtualPlayer target) {
        ItemStack tnt = new ItemStack(Material.TNT);
        target.setEquipment(EquipmentSlot.MAINHAND, tnt);
        target.setEquipment(EquipmentSlot.OFFHAND, tnt.clone());
    }

    public void despawn() {
        if (!spawned) {
            return;
        }
        spawned = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        activeThrows.clear();
        holograms.clear();
        hologramTemplates.clear();
        if (tracker != null) {
            tracker.removeAll();
            tracker = null;
        }
        npc = null;
    }

    private void tick() {
        if (!spawned || tracker == null || npc == null) {
            return;
        }
        tickCounter++;

        MinerSettings settings = plugin.getMinerSettings();

        float deltaYaw = wrapDegrees(targetYaw - currentYaw);
        if (Math.abs(deltaYaw) > 0.35F) {
            currentYaw = wrapDegrees(currentYaw + deltaYaw * 0.38F);
            npc.setYaw(currentYaw);
        } else if (currentYaw != targetYaw) {
            currentYaw = targetYaw;
            npc.setYaw(currentYaw);
        }

        float deltaPitch = targetPitch - currentPitch;
        if (Math.abs(deltaPitch) > 0.25F) {
            currentPitch += deltaPitch * 0.35F;
            npc.setPitch(currentPitch);
        }

        ticksUntilThrow--;
        if (ticksUntilThrow <= 0) {
            ticksUntilThrow = settings.getThrowIntervalTicks();
            performTurnAndThrow(settings);
        } else if (ticksUntilThrow == settings.getThrowIntervalTicks() - 8) {
            targetPitch = 0.0F;
        }

        if (tickCounter % 3 == 0) {
            int waveStep = tickCounter / 3;
            for (int i = 0; i < holograms.size() && i < hologramTemplates.size(); i++) {
                String tpl = hologramTemplates.get(i);
                if (tpl.contains(Text.HOLO_WAVE_TOKEN)) {
                    holograms.get(i).setCustomName(Text.component(Text.resolveHoloLine(tpl, waveStep)));
                }
            }
        }

        updateThrownItems();
        tracker.tick();
        hideFromTab();
    }

    private void performTurnAndThrow(MinerSettings settings) {
        boolean throwingLeft = this.nextTurnLeft;
        this.nextTurnLeft = !this.nextTurnLeft;

        float angleOffset = throwingLeft ? -settings.getTurnAngle() : settings.getTurnAngle();
        this.targetYaw = wrapDegrees(baseYaw + angleOffset);
        this.currentYaw = wrapDegrees(currentYaw + wrapDegrees(this.targetYaw - currentYaw) * 0.65F);
        this.targetPitch = 10.0F;
        this.currentPitch = 8.0F;

        npc.setYaw(this.currentYaw);
        npc.setPitch(this.currentPitch);

        npc.playAnimation(throwingLeft ? EntityAnimation.SWING_OFFHAND : EntityAnimation.SWING_MAIN_ARM);

        double rad = Math.toRadians(this.targetYaw);
        double dirX = -Math.sin(rad);
        double dirZ = Math.cos(rad);

        double startX = location.getX() + dirX * 0.38D;
        double startY = location.getY() + 1.24D;
        double startZ = location.getZ() + dirZ * 0.38D;

        double landCheckX = location.getX() + dirX * 1.15D;
        double landCheckZ = location.getZ() + dirZ * 1.15D;
        double groundY = resolveGroundY(location.getWorld(), landCheckX, location.getY(), landCheckZ);

        VirtualItem item = VirtualItem.create();
        item.setItem(new ItemStack(Material.TNT));
        item.setNoGravity(true);
        item.setSilent(true);
        item.setPos(new Vec3d(startX, startY, startZ));

        tracker.addEntity(item);
        activeThrows.add(new ThrownTnt(
                item,
                startX, startY, startZ,
                dirX * 0.145D, 0.075D, dirZ * 0.145D,
                groundY,
                settings.getGroundLifetimeTicks()
        ));
    }

    private void updateThrownItems() {
        if (activeThrows.isEmpty()) {
            return;
        }
        Iterator<ThrownTnt> it = activeThrows.iterator();
        while (it.hasNext()) {
            ThrownTnt tnt = it.next();
            if (!tnt.landed) {
                tnt.vy -= 0.038D;
                tnt.x += tnt.vx;
                tnt.y += tnt.vy;
                tnt.z += tnt.vz;
                if (tnt.y <= tnt.groundY) {
                    tnt.y = tnt.groundY;
                    tnt.vx = 0.0D;
                    tnt.vy = 0.0D;
                    tnt.vz = 0.0D;
                    tnt.landed = true;
                }
                tnt.entity.setPos(new Vec3d(tnt.x, tnt.y, tnt.z));
            } else {
                tnt.groundTicksLeft--;
                if (tnt.groundTicksLeft <= 0) {
                    tracker.removeEntity(tnt.entity);
                    it.remove();
                }
            }
        }
    }

    private double resolveGroundY(World world, double x, double baseY, double z) {
        if (world == null) {
            return baseY + 0.10D;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startBy = (int) Math.floor(baseY);
        for (int by = startBy; by >= startBy - 3; by--) {
            Block below = world.getBlockAt(bx, by - 1, bz);
            Block current = world.getBlockAt(bx, by, bz);
            if (!below.getType().isAir() && current.getType().isAir()) {
                return by + 0.10D;
            }
        }
        return baseY + 0.10D;
    }

    private void applyConfiguredSkin(VirtualPlayer targetNpc, MinerSettings settings) {
        String val = settings.getSkinValue();
        String sig = settings.getSkinSignature();
        String name = settings.getSkinName();

        if (isDefaultSkinName(name) && val != null && !val.isEmpty() && sig != null && !sig.isEmpty()) {
            targetNpc.setTexture(val, sig);
            return;
        }
        if (val != null && !val.isEmpty() && sig != null && !sig.isEmpty()) {
            targetNpc.setTexture(val, sig);
            return;
        }
        if (name != null && !name.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String[] fetched = SkinFetcher.fetch(name);
                    if (fetched != null && fetched.length >= 2) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (this.npc == targetNpc && this.spawned) {
                                targetNpc.setTexture(fetched[0], fetched[1]);
                                targetNpc.respawn();
                                equipDualTnt(targetNpc);
                                hideFromTab();
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static boolean isDefaultSkinName(String configuredName) {
        return configuredName == null
                || configuredName.isEmpty()
                || MinerSettings.DEFAULT_SKIN_NAME.equalsIgnoreCase(configuredName.trim());
    }

    private void spawnHologram(MinerSettings settings) {
        List<String> lines = settings.getHologramLines();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            String tpl = lines.get(i);
            double y = location.getY()
                    + settings.getHologramHeight()
                    + (total - 1 - i) * settings.getHologramLineGap();
            VirtualArmorStand stand = VirtualArmorStand.create();
            stand.setPos(new Vec3d(location.getX(), y, location.getZ()));
            stand.setInvisible(true);
            stand.setNoBasePlate(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setSmall(true);
            stand.setCustomName(Text.component(Text.resolveHoloLine(tpl, 0)));
            stand.setCustomNameVisible(true);
            tracker.addEntity(stand);
            holograms.add(stand);
            hologramTemplates.add(tpl);
        }
    }

    private void hideFromTab() {
        if (npc == null) {
            return;
        }
        for (Player viewer : npc.getLastViewers()) {
            npc.sendRemovePlayerPacket(viewer);
        }
    }

    private String buildHiddenName() {
        String clean = id.replaceAll("[^a-zA-Z0-9_]", "");
        String raw = "ec_m_" + (clean.isEmpty() ? "npc" : clean);
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    private void ensureHiddenNametag(String entry) {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(HIDE_NAMETAG_TEAM);
            if (team == null) {
                team = board.registerNewTeam(HIDE_NAMETAG_TEAM);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360.0F;
        if (deg >= 180.0F) {
            deg -= 360.0F;
        }
        if (deg < -180.0F) {
            deg += 360.0F;
        }
        return deg;
    }
}
