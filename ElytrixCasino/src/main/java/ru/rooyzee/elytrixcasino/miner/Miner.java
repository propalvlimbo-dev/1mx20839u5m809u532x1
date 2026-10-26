package ru.rooyzee.elytrixcasino.miner;

import dev.by1337.virtualentity.api.entity.BillboardConstraints;
import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.entity.HumanoidArm;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.display.VirtualTextDisplay;
import dev.by1337.virtualentity.api.virtual.item.VirtualItem;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пакетный NPC «Минёр»:
 * - голограмма VirtualTextDisplay без фона и с тенью (backgroundColor=0, shadow=true, billboard=CENTER),
 * - плавный поворот корпуса/головы и нативный клиентский бросок TNT (SetEntityMotionPacket + гравитация клиента)
 *   без серверных телепортов setPos каждый тик — летит плавно 1:1 как реальный выброс предмета игроком на Q.
 */
public final class Miner {

    private static final String HIDE_NAMETAG_TEAM = "ec_npc";
    private static final byte SKIN_LAYERS_NO_CAPE = 0x7E;
    private static final int PRE_TURN_TICKS = 12;
    private static final float YAW_TURN_SPEED = 4.5F;
    private static final float PITCH_TURN_SPEED = 2.0F;
    private static final int FLIGHT_BUFFER_TICKS = 22;

    private static final class ThrownTnt {
        final VirtualItem entity;
        int ticksLeft;

        ThrownTnt(VirtualItem entity, int lifetimeTicks) {
            this.entity = entity;
            this.ticksLeft = lifetimeTicks;
        }
    }

    private final Main plugin;
    private final String id;
    private final Location location;

    private VirtualPlayer npc;
    private VirtualTextDisplay hologram;
    private PlayerTracker tracker;
    private BukkitTask tickTask;
    private boolean spawned;

    private final List<ThrownTnt> activeThrows = new ArrayList<>();

    private float baseYaw;
    private float currentYaw;
    private float targetYaw;
    private float currentPitch;
    private float targetPitch;
    private boolean nextTurnLeft = true;
    private boolean pendingLeftThrow = true;
    private int ticksUntilThrow;

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
        this.pendingLeftThrow = true;
        this.ticksUntilThrow = Math.max(PRE_TURN_TICKS + 4, settings.getThrowIntervalTicks());

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
        hologram = null;
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

        MinerSettings settings = plugin.getMinerSettings();
        int interval = Math.max(20, settings.getThrowIntervalTicks());

        ticksUntilThrow--;
        if (ticksUntilThrow == PRE_TURN_TICKS) {
            // Заранее плавно поворачиваем корпус и голову в сторону броска
            this.pendingLeftThrow = this.nextTurnLeft;
            this.nextTurnLeft = !this.nextTurnLeft;
            float offset = this.pendingLeftThrow ? -settings.getTurnAngle() : settings.getTurnAngle();
            this.targetYaw = wrapDegrees(baseYaw + offset);
            this.targetPitch = 7.0F;
        } else if (ticksUntilThrow <= 0) {
            ticksUntilThrow = interval;
            throwTnt(settings, this.pendingLeftThrow);
        } else if (ticksUntilThrow == interval - 15) {
            // Плавно возвращаем взгляд прямо после броска
            this.targetYaw = baseYaw;
            this.targetPitch = 0.0F;
        }

        float nextYaw = approachAngle(currentYaw, targetYaw, YAW_TURN_SPEED);
        if (nextYaw != currentYaw) {
            currentYaw = nextYaw;
            npc.setYaw(currentYaw);
        }

        float nextPitch = approachAngle(currentPitch, targetPitch, PITCH_TURN_SPEED);
        if (nextPitch != currentPitch) {
            currentPitch = nextPitch;
            npc.setPitch(currentPitch);
        }

        updateThrownLifetimes();
        tracker.tick();
        hideFromTab();
    }

    /**
     * Выбрасывает предмет 1:1 по формуле ванильного Player#drop(ItemStack, false, true):
     * спавн на высоте глаз - 0.30 (y + 1.32), начальный импульс через SetEntityMotionPacket,
     * дальше клиент сам просчитывает гравитацию, воздух, столкновение с полом и плавную интерполяцию кадров.
     */
    private void throwTnt(MinerSettings settings, boolean throwingLeft) {
        npc.playAnimation(throwingLeft ? EntityAnimation.SWING_OFFHAND : EntityAnimation.SWING_MAIN_ARM);

        double yawRad = Math.toRadians(this.currentYaw);
        double pitchRad = Math.toRadians(this.currentPitch);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double speed = 0.28D;
        double randAngle = rng.nextDouble() * Math.PI * 2.0D;
        double randSpread = 0.018D * rng.nextDouble();

        double vx = -sinYaw * cosPitch * speed + Math.cos(randAngle) * randSpread;
        double vy = -sinPitch * speed + 0.10D + (rng.nextDouble() - rng.nextDouble()) * 0.01D;
        double vz = cosYaw * cosPitch * speed + Math.sin(randAngle) * randSpread;

        double startX = location.getX() - sinYaw * 0.16D;
        double startY = location.getY() + 1.32D;
        double startZ = location.getZ() + cosYaw * 0.16D;

        VirtualItem item = VirtualItem.create();
        item.setItem(new ItemStack(Material.TNT));
        item.setNoGravity(false);
        item.setSilent(true);
        item.setPos(new Vec3d(startX, startY, startZ));
        item.setMotion(new Vec3d(vx, vy, vz));

        tracker.addEntity(item);
        activeThrows.add(new ThrownTnt(item, FLIGHT_BUFFER_TICKS + settings.getGroundLifetimeTicks()));
        playDropSound();
    }

    private void playDropSound() {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double radiusSq = 16.0D * 16.0D;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSq) {
                player.playSound(location, Sound.ENTITY_SNOWBALL_THROW, 0.22F, 0.75F);
            }
        }
    }

    private void updateThrownLifetimes() {
        if (activeThrows.isEmpty()) {
            return;
        }
        Iterator<ThrownTnt> it = activeThrows.iterator();
        while (it.hasNext()) {
            ThrownTnt tnt = it.next();
            tnt.ticksLeft--;
            if (tnt.ticksLeft <= 0) {
                tracker.removeEntity(tnt.entity);
                it.remove();
            }
        }
    }

    private void applyConfiguredSkin(VirtualPlayer targetNpc, MinerSettings settings) {
        String val = settings.getSkinValue();
        String sig = settings.getSkinSignature();
        String name = settings.getSkinName();

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

    private void spawnHologram(MinerSettings settings) {
        List<String> lines = settings.getHologramLines();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        VirtualTextDisplay display = VirtualTextDisplay.create();
        display.setPos(new Vec3d(
                location.getX(),
                location.getY() + settings.getHologramHeight(),
                location.getZ()
        ));
        display.setBillboardConstraints(BillboardConstraints.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.BLACK, 0);
        display.setShadow(true);
        display.setLineWidth(400);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setText(Text.linesComponent(lines));
        tracker.addEntity(display);
        this.hologram = display;
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

    private static float approachAngle(float current, float target, float maxStep) {
        float diff = wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) {
            return wrapDegrees(target);
        }
        return wrapDegrees(current + Math.copySign(maxStep, diff));
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
