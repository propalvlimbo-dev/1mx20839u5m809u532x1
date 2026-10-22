package ru.rooyzee.elytrixsubscribe.functions.impl;

import dev.by1337.virtualentity.api.entity.Pose;
import dev.by1337.virtualentity.api.virtual.animal.VirtualParrot;
import org.by1337.blib.geom.Vec3d;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParrotFunction extends Function implements Listener {
    private final Map<UUID, ParrotState> states = new ConcurrentHashMap<>();
    private final Map<UUID, ParrotConfig> configs = new ConcurrentHashMap<>();

    private static final double ORBIT_RADIUS = 1.4;
    private static final double ORBIT_SPEED = 0.055;
    private static final double MAX_SPEED = 0.5;
    private static final double TELEPORT_THRESHOLD = 15.0;
    private static final double CENTER_LERP_IDLE = 0.06;
    private static final double CENTER_LERP_MOVE = 0.14;
    private static final double VIEW_DISTANCE_SQ = 4096.0;
    private static final int TICK_INTERVAL = 2;
    private static final int VIEWERS_REFRESH_TICKS = 10;
    private static final double MOVE_THRESHOLD = 0.05;
    private static final int LANDING_DELAY = 8;

    public ParrotFunction() {
        super("parrot", "Попугай");
        this.createTable();
        this.startFollowTask();
    }

    public static class ParrotConfig {
        public int variant = 0;
        public String name = null;
        public String behavior = "orbit";
    }

    private static class ParrotState {
        VirtualParrot entity;
        double angle = 0.0;
        double bobAngle = 0.0;
        Location orbitCenter;
        Location lastPlayerLoc;
        Set<Player> cachedViewers = Collections.emptySet();
        int viewersRefreshCounter = 999;
        int stillTicks = 0;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        ParrotConfig conf = new ParrotConfig();
        this.loadConfig(uuid, conf);
        this.configs.put(uuid, conf);
    }

    public ParrotConfig getConfig(UUID uuid) {
        return this.configs.computeIfAbsent(uuid, k -> {
            ParrotConfig conf = new ParrotConfig();
            this.loadConfig(uuid, conf);
            return conf;
        });
    }

    private void createTable() {
        Main.getInstance().getSQLite().update(
                "CREATE TABLE IF NOT EXISTS parrot_settings (player TEXT PRIMARY KEY, variant INTEGER, name TEXT, behavior TEXT)"
        );
    }

    private void loadConfig(UUID uuid, ParrotConfig conf) {
        Main.getInstance().getSQLite().query(
                "SELECT * FROM parrot_settings WHERE player=?",
                rs -> {
                    if (rs.next()) {
                        conf.variant = rs.getInt("variant");
                        conf.name = rs.getString("name");
                        conf.behavior = rs.getString("behavior");
                    }
                },
                uuid.toString()
        );
    }

    public void saveConfig(UUID uuid) {
        ParrotConfig conf = this.configs.get(uuid);
        if (conf != null) {
            Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () ->
                    Main.getInstance().getSQLite().update(
                            "INSERT OR REPLACE INTO parrot_settings (player, variant, name, behavior) VALUES(?,?,?,?)",
                            uuid.toString(), conf.variant, conf.name, conf.behavior
                    )
            );
        }
    }

    public void updateParrot(Player player) {
        if (Main.getInstance().getSubManager().isFunctionEnabled(player, this)) {
            this.spawnParrot(player);
        }
    }

    @Override
    public void onEnable(Player player) {
        this.spawnParrot(player);
        super.onEnable(player);
    }

    @Override
    public void onDisable(Player player) {
        this.removeParrot(player);
        super.onDisable(player);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent e) {
        this.removeParrot(e.getPlayer());
        this.configs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onDeath(PlayerDeathEvent e) {
        this.removeParrot(e.getEntity());
    }

    @EventHandler
    private void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (Main.getInstance().getSubManager().isFunctionEnabled(player, this)) {
                this.spawnParrot(player);
            }
        }, 10L);
    }

    @EventHandler
    private void onWorldChange(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (Main.getInstance().getSubManager().isFunctionEnabled(player, this)) {
                this.spawnParrot(player);
            }
        }, 10L);
    }

    @EventHandler
    private void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        ParrotState state = this.states.get(player.getUniqueId());
        if (state == null || state.entity == null) return;

        Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            Location dest = e.getTo();
            if (dest == null) return;
            state.entity.setPos(new Vec3d(dest.getX() + ORBIT_RADIUS, dest.getY() + 1.2, dest.getZ()));
            state.entity.setNoMotion();
            state.orbitCenter = dest.clone();
            state.lastPlayerLoc = dest.clone();
            state.cachedViewers = computeViewers(player);
            state.viewersRefreshCounter = 0;
            state.entity.tick(state.cachedViewers);
        }, 1L);
    }

    private Set<Player> computeViewers(Player owner) {
        Set<Player> viewers = new HashSet<>();
        Location loc = owner.getLocation();
        for (Player p : owner.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < VIEW_DISTANCE_SQ) {
                viewers.add(p);
            }
        }
        return viewers;
    }

    private void spawnParrot(Player player) {
        this.removeParrot(player);

        ParrotConfig conf = this.getConfig(player.getUniqueId());
        if (conf == null) return;

        ParrotState state = new ParrotState();
        VirtualParrot parrot = createParrot(conf);

        Location loc = player.getLocation().clone().add(ORBIT_RADIUS, 1.2, 0);
        parrot.setPos(new Vec3d(loc.getX(), loc.getY(), loc.getZ()));

        state.entity = parrot;
        state.orbitCenter = player.getLocation().clone();
        state.lastPlayerLoc = player.getLocation().clone();
        state.cachedViewers = computeViewers(player);
        state.viewersRefreshCounter = 0;
        parrot.tick(state.cachedViewers);

        this.states.put(player.getUniqueId(), state);
    }

    private VirtualParrot createParrot(ParrotConfig conf) {
        VirtualParrot parrot = VirtualParrot.create();
        parrot.setVariant(conf.variant);
        parrot.setNoGravity(true);
        parrot.setOnGround(false);
        parrot.setSilent(true);
        if (conf.name != null && !conf.name.isEmpty()) {
            parrot.setCustomName(toComponent(conf.name));
            parrot.setCustomNameVisible(true);
        } else {
            parrot.setCustomNameVisible(false);
        }
        return parrot;
    }

    private void removeParrot(Player player) {
        ParrotState state = this.states.remove(player.getUniqueId());
        if (state != null && state.entity != null) {
            state.entity.tick(Collections.emptySet());
        }
        player.setShoulderEntityLeft(null);
        player.setShoulderEntityRight(null);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Component toComponent(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(Utils.color(text));
    }

    private void startFollowTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
            if (this.states.isEmpty()) return;

            for (UUID playerUuid : new ArrayList<>(this.states.keySet())) {
                ParrotState state = this.states.get(playerUuid);
                if (state == null || state.entity == null) continue;

                Player player = Bukkit.getPlayer(playerUuid);
                if (player == null || !player.isOnline()) continue;

                ParrotConfig conf = this.getConfig(playerUuid);
                if (conf == null) continue;

                if (!player.getWorld().equals(state.orbitCenter.getWorld())) {
                    continue;
                }

                state.viewersRefreshCounter++;
                if (state.viewersRefreshCounter >= VIEWERS_REFRESH_TICKS) {
                    state.cachedViewers = computeViewers(player);
                    state.viewersRefreshCounter = 0;
                }
                Set<Player> viewers = state.cachedViewers;

                Location playerLoc = player.getLocation();
                Location lastLoc = state.lastPlayerLoc != null ? state.lastPlayerLoc : playerLoc;
                double moveDistSq = playerLoc.distanceSquared(lastLoc);
                boolean isMoving = moveDistSq > MOVE_THRESHOLD * MOVE_THRESHOLD;
                state.lastPlayerLoc = playerLoc.clone();

                Vec3d parrotVec = state.entity.getPos();
                double ddx = parrotVec.getX() - playerLoc.getX();
                double ddy = parrotVec.getY() - playerLoc.getY();
                double ddz = parrotVec.getZ() - playerLoc.getZ();
                double distToPlayerSq = ddx * ddx + ddy * ddy + ddz * ddz;

                if (distToPlayerSq > TELEPORT_THRESHOLD * TELEPORT_THRESHOLD) {
                    state.orbitCenter = playerLoc.clone();
                    Location loc = playerLoc.clone().add(ORBIT_RADIUS, 1.2, 0);
                    state.entity.setPos(new Vec3d(loc.getX(), loc.getY(), loc.getZ()));
                    state.entity.setNoMotion();
                    state.entity.tick(viewers);
                    continue;
                }

                Location targetLoc;
                boolean sitOnHead = false;

                if (conf.behavior.equals("head")) {
                    if (!isMoving) {
                        state.stillTicks += TICK_INTERVAL;
                    } else {
                        state.stillTicks = 0;
                    }
                    sitOnHead = state.stillTicks >= LANDING_DELAY;

                    if (sitOnHead) {
                        Location headLoc = player.getEyeLocation().add(0, 0.35, 0);
                        targetLoc = headLoc;
                    } else {
                        Vector back = playerLoc.getDirection().setY(0);
                        if (back.lengthSquared() > 0.001) {
                            back.normalize().multiply(-0.3);
                        }
                        targetLoc = playerLoc.clone().add(back).add(0, 2.2, 0);
                        state.bobAngle += 0.15;
                        targetLoc.add(0, Math.sin(state.bobAngle) * 0.05, 0);
                    }
                } else if (conf.behavior.equals("hover")) {
                    Vector direction = playerLoc.getDirection().setY(0);
                    if (direction.lengthSquared() > 0.001) direction.normalize();
                    Vector right = new Vector(-direction.getZ(), 0, direction.getX());
                    state.bobAngle += 0.05;
                    targetLoc = playerLoc.clone().add(right.multiply(1.5)).add(0, 1.3 + Math.sin(state.bobAngle) * 0.08, 0);
                } else {
                    if (isMoving) {
                        Vector playerDelta = playerLoc.toVector().subtract(lastLoc.toVector());
                        Vector moveDir = playerDelta.setY(0);
                        if (moveDir.lengthSquared() > 0.001) moveDir.normalize().multiply(2.2);
                        Location targetCenter = playerLoc.clone().add(moveDir);
                        state.orbitCenter = new Location(
                                player.getWorld(),
                                lerp(state.orbitCenter.getX(), targetCenter.getX(), CENTER_LERP_MOVE),
                                lerp(state.orbitCenter.getY(), playerLoc.getY(), CENTER_LERP_MOVE),
                                lerp(state.orbitCenter.getZ(), targetCenter.getZ(), CENTER_LERP_MOVE)
                        );
                    } else {
                        state.orbitCenter = new Location(
                                player.getWorld(),
                                lerp(state.orbitCenter.getX(), playerLoc.getX(), CENTER_LERP_IDLE),
                                lerp(state.orbitCenter.getY(), playerLoc.getY(), CENTER_LERP_IDLE),
                                lerp(state.orbitCenter.getZ(), playerLoc.getZ(), CENTER_LERP_IDLE)
                        );
                    }

                    state.angle += ORBIT_SPEED * TICK_INTERVAL;
                    double x = state.orbitCenter.getX() + Math.cos(state.angle) * ORBIT_RADIUS;
                    double z = state.orbitCenter.getZ() + Math.sin(state.angle) * ORBIT_RADIUS;
                    double y = playerLoc.getY() + 1.15 + Math.sin(state.angle * 2.0) * 0.15;
                    targetLoc = new Location(player.getWorld(), x, y, z);
                }

                double dx = targetLoc.getX() - parrotVec.getX();
                double dy = targetLoc.getY() - parrotVec.getY();
                double dz = targetLoc.getZ() - parrotVec.getZ();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                double moveX, moveY, moveZ;
                Vec3d newPos;

                if (distance < 0.001) {
                    moveX = 0;
                    moveY = 0;
                    moveZ = 0;
                    newPos = parrotVec;
                } else {
                    double step = sitOnHead ? distance : Math.min(MAX_SPEED * TICK_INTERVAL, distance);
                    double factor = step / distance;
                    moveX = dx * factor;
                    moveY = dy * factor;
                    moveZ = dz * factor;
                    newPos = new Vec3d(parrotVec.getX() + moveX, parrotVec.getY() + moveY, parrotVec.getZ() + moveZ);
                }

                state.entity.setMotion(new Vec3d(moveX, moveY, moveZ));
                state.entity.setPos(newPos);

                if (sitOnHead) {
                    state.entity.setYaw(player.getLocation().getYaw());
                    state.entity.setPitch(player.getLocation().getPitch());
                } else if (Math.abs(moveX) > 0.01 || Math.abs(moveZ) > 0.01) {
                    double lookYaw = Math.toDegrees(Math.atan2(-moveX, moveZ));
                    state.entity.setYaw((float) lookYaw);
                }

                state.entity.tick(viewers);
            }
        }, 0L, TICK_INTERVAL);
    }
}