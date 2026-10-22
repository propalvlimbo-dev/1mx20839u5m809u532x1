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
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Правила боя на арене: не выпускаем игрока за пределы его арены и не даём
 * улететь выше лимита.
 *
 * Границы считаются по точкам спавна арены (spawn1/spawn2):
 *   центр      — середина между точками,
 *   радиус      — половина расстояния между ними + protection.arena-margin,
 *   высота      — max(spawn1.y, spawn2.y) + protection.max-height.
 *
 * Так в одном шаблоне может стоять несколько арен: у каждой свой сп1/сп2,
 * значит и своя зона. Настраивается под конкретную арену через
 * /duelx bounds (показывает текущие границы).
 *
 * Выход за границы: перемещение отменяется. Пёрл за границу или выше лимита
 * не телепортирует (пёрл просто тратится). Телепорт в другой мир во время
 * боя отменяется; команды вида /spawn, /hub, /home блокируются.
 */
final class ArenaRules implements Listener {

    private static final long WARN_DELAY_MS = 2500L;

    private final ElytrixDuelsBridge plugin;
    private final Duels duels;
    private final Map<UUID, Bounds> cache = new HashMap<UUID, Bounds>();
    private final Map<UUID, Long> warned = new HashMap<UUID, Long>();
    private final Map<UUID, Long> pulledBack = new HashMap<UUID, Long>();

    ArenaRules(ElytrixDuelsBridge plugin, Duels duels) {
        this.plugin = plugin;
        this.duels = duels;
    }

    // ------------------------------------------------------------------ зона

    /** Границы одной арены в мире боя. */
    static final class Bounds {
        final String world;
        final double centerX;
        final double centerZ;
        final double radius;
        final double baseY;
        final double maxY;
        final double minY;
        final Location spawnA;
        final Location spawnB;
        final java.util.List<Location> points;

        Bounds(String world, Location spawnA, Location spawnB, java.util.List<Location> points,
               double margin, double maxHeight, double minDrop) {
            this.world = world;
            this.spawnA = spawnA;
            this.spawnB = spawnB;
            this.points = points;
            double minX = spawnA.getX();
            double maxX = spawnA.getX();
            double minZ = spawnA.getZ();
            double maxZ = spawnA.getZ();
            double topY = Math.max(spawnA.getY(), spawnB.getY());
            for (Location point : points) {
                minX = Math.min(minX, point.getX());
                maxX = Math.max(maxX, point.getX());
                minZ = Math.min(minZ, point.getZ());
                maxZ = Math.max(maxZ, point.getZ());
                topY = Math.max(topY, point.getY());
            }
            this.centerX = (minX + maxX) / 2.0;
            this.centerZ = (minZ + maxZ) / 2.0;
            double radius = 0;
            for (Location point : points) {
                double dx = point.getX() - centerX;
                double dz = point.getZ() - centerZ;
                radius = Math.max(radius, Math.sqrt(dx * dx + dz * dz));
            }
            this.radius = radius + Math.max(0, margin);
            this.baseY = topY;
            this.maxY = baseY + Math.max(0, maxHeight);
            this.minY = baseY - Math.max(0, minDrop);
        }

        boolean horizontal(Location location) {
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        }

        boolean inside(Location location) {
            return horizontal(location) && location.getY() <= maxY && location.getY() >= minY;
        }

        /** Ближайшая точка арены: упал за границу — вернётся на свою пару, а не на 1-2. */
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

    private boolean leaveEnabled() {
        return plugin.getConfig().getBoolean("protection.leave", true);
    }

    private boolean pearlEnabled() {
        return plugin.getConfig().getBoolean("protection.pearl-limit", true);
    }

    private double margin() {
        return plugin.getConfig().getDouble("protection.arena-margin", 20.0);
    }

    private double maxHeight() {
        return plugin.getConfig().getDouble("protection.max-height", 7.0);
    }

    /**
     * С булавой нужен запас по высоте: Wind Burst подбрасывает высоко,
     * поэтому у кита «Булава» лимит свой (protection.mace-height, 15).
     */
    private double maxHeight(Object instance) {
        String kit = instance == null ? null : String.valueOf(Duels.call(instance, "getKitName"));
        String lower = kit == null ? "" : kit.toLowerCase(Locale.ROOT);
        if (lower.contains("булав") || lower.contains("mace")) {
            return plugin.getConfig().getDouble("protection.mace-height", 15.0);
        }
        return maxHeight();
    }

    private double minDrop() {
        return plugin.getConfig().getDouble("protection.min-drop", 25.0);
    }

    /** Живой поиск боя игрока: арена, мир боя, точки спавна. */
    Bounds resolve(Player player) {
        if (player == null || !duels.available()) return null;
        UUID id = player.getUniqueId();
        Object instance = Duels.call(duels.manager("getDuelManager"), "getDuel", id);
        if (instance == null) {
            instance = Duels.call(duels.manager("getTeamDuelManager"), "getTeamDuelOf", id);
        }
        if (instance == null) return null;
        if (!Boolean.TRUE.equals(Duels.call(instance, "isActive"))) return null;

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
        // границы — по точкам своей пары: бой идёт только на ней, и убежать
        // на соседнюю пару нельзя
        java.util.List<Location> points = new java.util.ArrayList<Location>();
        if (plugin.arenas() != null) {
            points.addAll(plugin.arenas().pairPointsIn((org.bukkit.World) world));
        }
        if (points.size() != 2) {
            // пара ещё не выбрана — считаем по всем точкам арены
            points.clear();
            points.add(spawnA);
            points.add(spawnB);
            if (plugin.arenas() != null) {
                points.addAll(plugin.arenas().pointsIn((org.bukkit.World) world));
            }
        }
        return new Bounds(String.valueOf(worldName), spawnA, spawnB, points,
                margin(), maxHeight(instance), minDrop());
    }

    // ------------------------------------------------------------- обновление

    /** Раз в полсекунды держим кэш зон в актуальном состоянии. */
    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                // кэш зон нужен только для границ: пёрлы проверяются напрямую
                if (!leaveEnabled()) {
                    cache.clear();
                    return;
                }
                Set<UUID> online = new LinkedHashSet<UUID>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    online.add(id);
                    Bounds bounds = resolve(player);
                    if (bounds == null) {
                        cache.remove(id);
                        continue;
                    }
                    cache.put(id, bounds);
                    pullBack(player, bounds);
                }
                cache.keySet().retainAll(online);
            }
        }, 20L, 10L);
    }

    /** Страховка: если игрок всё же оказался вне зоны — возвращаем на спавн. */
    private void pullBack(Player player, Bounds bounds) {
        Location location = player.getLocation();
        if (location == null || location.getWorld() == null) return;
        if (!bounds.world.equals(location.getWorld().getName())) return;
        if (bounds.inside(location)) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = pulledBack.get(id);
        if (last != null && now - last < 1000L) return;
        pulledBack.put(id, now);
        player.teleport(bounds.nearestSpawn(location));
    }

    // -------------------------------------------------------------- события

    /** Шаг/полёт за границу арены или выше лимита — не пускаем. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !leaveEnabled() || plugin.isAdmin(player)) return;
        Bounds bounds = cache.get(player.getUniqueId());
        if (bounds == null) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        if (!bounds.world.equals(to.getWorld().getName())) return;

        if (to.getY() > bounds.maxY) {
            event.setTo(event.getFrom());
            warn(player, "messages.arena-height", "&#F8BEFBДуэли &8» &cВыше &f%block%&c блоков не подняться.",
                    String.valueOf((int) Math.round(bounds.maxY - bounds.baseY)));
            return;
        }
        if (to.getY() < bounds.minY || !bounds.horizontal(to)) {
            event.setTo(event.getFrom());
            warn(player, "messages.arena-bounds", "&#F8BEFBДуэли &8» &cЭто край арены.", null);
        }
    }

    /** Пёрл и любой телепорт из боя. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null || plugin.isAdmin(player)) return;
        Bounds bounds = resolve(player);
        if (bounds == null) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        boolean pearl = event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL;

        // другой мир во время боя = попытка выйти (или чужой плагин)
        if (!bounds.world.equals(to.getWorld().getName())) {
            if (!leaveEnabled()) return;
            event.setCancelled(true);
            warn(player, "messages.arena-leave", "&#F8BEFBДуэли &8» &cИз боя телепортом не выйти.", null);
            return;
        }

        if (pearl || leaveEnabled()) {
            if (!bounds.inside(to)) {
                if (pearl && !pearlEnabled()) return;
                event.setCancelled(true);
                if (pearl) {
                    warn(player, "messages.pearl-blocked",
                            "&#F8BEFBДуэли &8» &cПёрл туда не сработает: за ареной или слишком высоко.", null);
                } else {
                    warn(player, "messages.arena-bounds", "&#F8BEFBДуэли &8» &cЭто край арены.", null);
                }
            }
        }
    }

    /** Во время дуэли ресурсы из набора нельзя выбрасывать. */
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

    /** /spawn, /hub, /home и подобные команды во время боя. */
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
        if (raw == null || raw.isEmpty()) {
            raw = new ArrayList<String>();
        }
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
