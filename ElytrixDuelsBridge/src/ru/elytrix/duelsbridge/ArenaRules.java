package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Правила боя на арене: горизонтальная граница, возврат на свой спавн и
 * предупреждение о слишком большой высоте.
 *
 * Границы считаются по точкам спавна арены (spawn1/spawn2): центр — середина
 * зоны, радиус — половина расстояния между точками + protection.arena-margin.
 * Координата Y не ограничивает арену. Высота для предупреждения считается
 * отдельно от максимальной Y-координаты двух спавнов текущей дуэли.
 */
final class ArenaRules implements Listener {

    private static final long WARN_DELAY_MS = 2500L;

    private final ElytrixDuelsBridge plugin;
    private final Duels duels;
    private final Map<UUID, Bounds> cache = new HashMap<UUID, Bounds>();
    private final Map<UUID, Long> warned = new HashMap<UUID, Long>();
    private final Map<UUID, Long> pulledBack = new HashMap<UUID, Long>();
    private final Map<UUID, HeightWarning> heightWarnings = new HashMap<UUID, HeightWarning>();

    ArenaRules(ElytrixDuelsBridge plugin, Duels duels) {
        this.plugin = plugin;
        this.duels = duels;
    }

    /** One active duel's horizontal arena and spawn height. */
    static final class Bounds {
        final Object instance;
        final String world;
        final double centerX;
        final double centerZ;
        final double radius;
        final double baseY;
        final Location spawnA;
        final Location spawnB;
        final List<Location> points;

        Bounds(Object instance, String world, Location spawnA, Location spawnB,
               List<Location> points, double margin) {
            this.instance = instance;
            this.world = world;
            this.spawnA = spawnA;
            this.spawnB = spawnB;
            this.points = new ArrayList<Location>(points);
            if (this.points.isEmpty()) {
                this.points.add(spawnA);
                this.points.add(spawnB);
            }

            double minX = spawnA.getX();
            double maxX = spawnA.getX();
            double minZ = spawnA.getZ();
            double maxZ = spawnA.getZ();
            for (Location point : this.points) {
                minX = Math.min(minX, point.getX());
                maxX = Math.max(maxX, point.getX());
                minZ = Math.min(minZ, point.getZ());
                maxZ = Math.max(maxZ, point.getZ());
            }
            this.centerX = (minX + maxX) / 2.0;
            this.centerZ = (minZ + maxZ) / 2.0;

            double outerRadius = 0;
            for (Location point : this.points) {
                double dx = point.getX() - centerX;
                double dz = point.getZ() - centerZ;
                outerRadius = Math.max(outerRadius, Math.sqrt(dx * dx + dz * dz));
            }
            this.radius = outerRadius + Math.max(0, margin);
            this.baseY = Math.max(spawnA.getY(), spawnB.getY());
        }

        boolean horizontal(Location location) {
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        }

        boolean tooHigh(Location location, double warningBlocks) {
            return location.getY() > baseY + warningBlocks;
        }

        /** Return to a spawn from this duel, never to a neighbouring arena. */
        Location nearestSpawn(Location from) {
            Location best = spawnA.clone();
            double bestDistance = Double.MAX_VALUE;
            for (Location point : points) {
                double dx = from.getX() - point.getX();
                double dz = from.getZ() - point.getZ();
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = point.clone();
                }
            }
            return best;
        }
    }

    private static final class HeightWarning {
        final Object instance;
        final long startedAt;

        HeightWarning(Object instance, long startedAt) {
            this.instance = instance;
            this.startedAt = startedAt;
        }
    }

    private boolean leaveEnabled() {
        return plugin.getConfig().getBoolean("protection.leave", true);
    }

    private boolean pearlEnabled() {
        return plugin.getConfig().getBoolean("protection.pearl-limit", true);
    }

    private double margin() {
        return plugin.getConfig().getDouble("protection.arena-margin", 50.0);
    }

    private double heightWarningBlocks() {
        return Math.max(0.0, plugin.getConfig().getDouble("protection.height-warning-blocks", 15.0));
    }

    private long heightWarningSeconds() {
        return Math.max(1L, Math.min(3600L,
                plugin.getConfig().getLong("protection.height-warning-seconds", 10L)));
    }

    /** Resolve an active duel, its world, and its two spawn points. */
    Bounds resolve(Player player) {
        if (player == null || !duels.available()) return null;
        UUID id = player.getUniqueId();
        Object instance = Duels.call(duels.manager("getDuelManager"), "getDuel", id);
        if (instance == null) {
            instance = Duels.call(duels.manager("getTeamDuelManager"), "getTeamDuelOf", id);
        }
        if (instance == null || !Boolean.TRUE.equals(Duels.call(instance, "isActive"))) return null;

        Object world = Duels.call(instance, "getInstanceWorld");
        Object arenaName = Duels.call(instance, "getArenaName");
        if (world == null || arenaName == null) return null;
        Object worldName = Duels.call(world, "getName");
        if (worldName == null) return null;

        Object arena = Duels.call(duels.manager("getArenaManager"), "getArena", String.valueOf(arenaName));
        if (arena == null) return null;
        Object first = Duels.call(arena, "getSpawn1");
        Object second = Duels.call(arena, "getSpawn2");
        if (!(first instanceof Location) || !(second instanceof Location)) return null;

        Location spawnA = ((Location) first).clone();
        Location spawnB = ((Location) second).clone();
        spawnA.setWorld((org.bukkit.World) world);
        spawnB.setWorld((org.bukkit.World) world);
        // Prefer only this match's pair, so adjacent arenas stay isolated.
        List<Location> points = new ArrayList<Location>();
        if (plugin.arenas() != null) {
            points.addAll(plugin.arenas().pairPointsIn((org.bukkit.World) world));
        }
        if (points.size() != 2) {
            // If pair selection is not available yet, use the arena's points.
            points.clear();
            points.add(spawnA);
            points.add(spawnB);
            if (plugin.arenas() != null) {
                points.addAll(plugin.arenas().pointsIn((org.bukkit.World) world));
            }
        }
        return new Bounds(instance, String.valueOf(worldName), spawnA, spawnB, points, margin());
    }

    /** Keep cached boundaries current and apply height warnings every half second. */
    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!leaveEnabled()) {
                    cache.clear();
                    heightWarnings.clear();
                    return;
                }
                Set<UUID> online = new LinkedHashSet<UUID>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    online.add(id);
                    Bounds bounds = resolve(player);
                    if (bounds == null) {
                        cache.remove(id);
                        heightWarnings.remove(id);
                        continue;
                    }
                    cache.put(id, bounds);
                    pullBack(player, bounds);
                    updateHeightWarning(player, bounds, player.getLocation());
                }
                cache.keySet().retainAll(online);
                heightWarnings.keySet().retainAll(online);
            }
        }, 20L, 10L);
    }

    /** Safety net: if a player is already outside horizontally, return them. */
    private void pullBack(Player player, Bounds bounds) {
        Location location = player.getLocation();
        if (!inWorld(location, bounds)) return;
        if (bounds.horizontal(location)) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = pulledBack.get(id);
        if (last != null && now - last < 1000L) return;
        pulledBack.put(id, now);
        player.teleport(ownSpawn(player, bounds, location));
    }

    private boolean inWorld(Location location, Bounds bounds) {
        return location != null && location.getWorld() != null
                && bounds.world.equals(location.getWorld().getName());
    }

    private Location ownSpawn(Player player, Bounds bounds, Location fallback) {
        Location own = plugin.arenas() == null ? null : plugin.arenas().spot(player.getUniqueId(), bounds.world);
        if (own == null || own.getWorld() == null || !bounds.world.equals(own.getWorld().getName())) {
            own = bounds.nearestSpawn(fallback);
        }
        return own;
    }

    // ---------------------------------------------------------------- events

    /** Horizontal movement is limited; height is warned, not blocked. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !leaveEnabled() || plugin.isAdmin(player)) return;
        Bounds bounds = cache.get(player.getUniqueId());
        if (bounds == null) return;
        Location to = event.getTo();
        if (!inWorld(to, bounds)) return;

        if (!bounds.horizontal(to)) {
            event.setTo(ownSpawn(player, bounds, to));
            heightWarnings.remove(player.getUniqueId());
            warn(player, "messages.arena-bounds", "&#F8BEFBДуэли &8» &cЭто край арены.", null);
            return;
        }
        updateHeightWarning(player, bounds, to);
    }

    /** Ender pearls and other teleports cannot cross the horizontal boundary. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null || plugin.isAdmin(player)) return;
        Bounds bounds = resolve(player);
        if (bounds == null) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        boolean pearl = event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL;

        // A different world is always blocked when the leave guard is enabled.
        if (!bounds.world.equals(to.getWorld().getName())) {
            if (!leaveEnabled()) return;
            event.setCancelled(true);
            warn(player, "messages.arena-leave", "&#F8BEFBДуэли &8» &cИз боя телепортом не выйти.", null);
            return;
        }

        if (pearl && !pearlEnabled()) return;
        if (!pearl && !leaveEnabled()) return;
        if (!bounds.horizontal(to)) {
            event.setTo(ownSpawn(player, bounds, to));
            heightWarnings.remove(player.getUniqueId());
            if (pearl) {
                warn(player, "messages.pearl-blocked",
                        "&#F8BEFBДуэли &8» &cПёрл за горизонтальную границу не сработает.", null);
            } else {
                warn(player, "messages.arena-bounds", "&#F8BEFBДуэли &8» &cЭто край арены.", null);
            }
            return;
        }
        if (leaveEnabled()) updateHeightWarning(player, bounds, to);
    }

    private void updateHeightWarning(Player player, Bounds bounds, Location location) {
        UUID id = player.getUniqueId();
        if (plugin.isAdmin(player)) {
            heightWarnings.remove(id);
            return;
        }
        if (!inWorld(location, bounds)) {
            heightWarnings.remove(id);
            return;
        }
        if (!bounds.tooHigh(location, heightWarningBlocks())) {
            heightWarnings.remove(id);
            return;
        }

        long now = System.currentTimeMillis();
        HeightWarning warning = heightWarnings.get(id);
        if (warning == null || warning.instance != bounds.instance) {
            heightWarnings.put(id, new HeightWarning(bounds.instance, now));
            String text = plugin.string("messages.arena-height-warning",
                    "&#F8BEFBДуэли &8» &cТы выше &f%blocks%&c блоков от спавна. "
                            + "Спустись за &f%seconds%&c секунд, иначе будет засчитано поражение.")
                    .replace("%blocks%", blockText(heightWarningBlocks()))
                    .replace("%seconds%", String.valueOf(heightWarningSeconds()));
            plugin.msg(player, text);
            return;
        }
        if (now - warning.startedAt < heightWarningSeconds() * 1000L) return;

        heightWarnings.remove(id);
        Bounds active = resolve(player);
        if (active == null || active.instance != warning.instance || !inWorld(location, active)
                || !active.tooHigh(location, heightWarningBlocks())) return;
        scheduleForfeit(player, active.instance);
    }

    /** Defer out of PlayerMoveEvent, then re-check that the player stayed high. */
    private void scheduleForfeit(final Player player, final Object instance) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline() || plugin.isAdmin(player)) return;
                Bounds active = resolve(player);
                Location location = player.getLocation();
                if (active == null || active.instance != instance || !inWorld(location, active)
                        || !active.tooHigh(location, heightWarningBlocks())) return;
                forfeit(player, instance);
            }
        }, 1L);
    }

    private String blockText(double blocks) {
        double rounded = Math.round(blocks * 10.0) / 10.0;
        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** Use EpicDuels' existing 1v1/team forfeit paths so its normal result handling runs. */
    private void forfeit(Player player, Object instance) {
        UUID id = player.getUniqueId();
        Object duelManager = duels.manager("getDuelManager");
        Object duel = Duels.call(duelManager, "getDuel", id);
        if (duel == instance) {
            Duels.call(duelManager, "handleForfeit", id);
            return;
        }
        Object teamManager = duels.manager("getTeamDuelManager");
        Object teamDuel = Duels.call(teamManager, "getTeamDuelOf", id);
        if (teamDuel == instance) {
            Duels.call(teamManager, "handleForfeit", player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) return;
        UUID id = event.getPlayer().getUniqueId();
        cache.remove(id);
        warned.remove(id);
        pulledBack.remove(id);
        heightWarnings.remove(id);
    }

    /** During a duel, resources from the kit cannot be dropped. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || plugin.isAdmin(player)) return;
        if (!plugin.getConfig().getBoolean("protection.no-drop", true)) return;
        if (resolve(player) == null) return;
        event.setCancelled(true);
        warn(player, "messages.arena-no-drop",
                "&#F8BEFBДуэли &8» &cРесурсы нельзя выбрасывать во время дуэли.", null);
    }

    /** Block /spawn, /hub, /home, and similar commands during a duel. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player == null || !leaveEnabled() || plugin.isAdmin(player)) return;
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) return;
        String label = message.substring(1).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!blocked().contains(label)) return;
        if (resolve(player) == null) return;
        event.setCancelled(true);
        warn(player, "messages.arena-command",
                "&#F8BEFBДуэли &8» &cВо время боя эта команда недоступна.", null);
    }

    private Set<String> blocked() {
        List<String> raw = plugin.getConfig().getStringList("protection.blocked-commands");
        if (raw == null || raw.isEmpty()) raw = new ArrayList<String>();
        Set<String> out = new LinkedHashSet<String>();
        for (String value : raw) {
            if (value == null) continue;
            String clean = value.trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }

    private void warn(Player player, String path, String fallback, String value) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = warned.get(id);
        if (last != null && now - last < WARN_DELAY_MS) return;
        warned.put(id, now);
        String text = plugin.string(path, fallback);
        if (value != null) text = text.replace("%block%", value);
        plugin.msg(player, text);
    }
}
