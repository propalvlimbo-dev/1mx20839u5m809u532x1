package ru.rooyzee.elytrixtrader.trader;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.animation.DisappearAnimation;
import ru.rooyzee.elytrixtrader.config.AppConfig;
import ru.rooyzee.elytrixtrader.model.SchematicConfig;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.region.RegionManager;
import ru.rooyzee.elytrixtrader.region.TraderRegion;
import ru.rooyzee.elytrixtrader.schematic.SchematicSnapshot;

import java.util.*;

public class TraderManager {

    private static final int REGION_PADDING = 20;

    private final Main plugin;
    private final List<TraderInstance> instances = new ArrayList<>();
    private final Random random = new Random();
    private final RegionManager regionManager;
    private final DisappearAnimation animation;
    private BukkitTask scheduleTask;
    private final Map<String, BukkitTask> intervalScheduleTasks = new HashMap<>();
    private final Map<String, Long> nextIntervalSpawnAtMillis = new HashMap<>();
    private int cycleIndex;
    private boolean eventEndedAnnounced;

    public TraderManager(Main plugin) {
        this.plugin = plugin;
        this.regionManager = new RegionManager(plugin);
        this.animation = new DisappearAnimation(plugin);
    }

    public RegionManager regions() { return regionManager; }

    public void start() {
        stopImmediate();
        scheduleNextEvent();
        scheduleIntervalTraders();
    }

    private void scheduleIntervalTraders() {
        for (TraderConfig config : plugin.traderConfigs().values()) {
            if (config == null || !config.enabled() || config.spawnIntervalSeconds() <= 0) continue;

            String id = config.id().toLowerCase(Locale.ROOT);
            long intervalTicks = Math.max(1L, config.spawnIntervalSeconds()) * 20L;
            nextIntervalSpawnAtMillis.put(id, System.currentTimeMillis() + 50L);
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> fireIntervalEvent(id), 1L, intervalTicks);
            intervalScheduleTasks.put(id, task);
        }
    }

    private void fireIntervalEvent(String traderId) {
        TraderConfig config = plugin.traderConfigs().get(traderId);
        if (config == null || !config.enabled() || config.spawnIntervalSeconds() <= 0) return;

        nextIntervalSpawnAtMillis.put(traderId,
                System.currentTimeMillis() + config.spawnIntervalSeconds() * 1000L);
        if (count(traderId) > 0) return;

        TraderInstance spawned = null;
        try {
            spawned = spawn(config, false);
        } catch (Throwable ignored) {
        }
        if (spawned != null) {
            try { plugin.announcer().eventStarted(Collections.singletonList(spawned)); } catch (Throwable ignored) {}
        }
    }

    private void scheduleNextEvent() {
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
        long delaySeconds = nextEventDelaySeconds();
        if (delaySeconds < 0) return;
        scheduleTask = Bukkit.getScheduler().runTaskLater(plugin, this::fireScheduledEvent, delaySeconds * 20L);
    }

    private long nextEventDelaySeconds() {
        if (!plugin.config().scheduleEnabled()) return -1;
        long now = java.time.LocalTime.now().toSecondOfDay();
        long best = Long.MAX_VALUE;
        for (String time : plugin.config().scheduleTimes()) {
            long target = parseTime(time);
            if (target < 0) continue;
            long diff = target - now;
            if (diff <= 0) diff += 24L * 3600L;
            if (diff < best) best = diff;
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    private long parseTime(String value) {
        if (value == null) return -1;
        String[] parts = value.trim().split(":");
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return h * 3600L + m * 60L;
        } catch (Throwable t) { return -1; }
    }

    private void fireScheduledEvent() {
        scheduleTask = null;
        List<TraderConfig> enabled = new ArrayList<>();
        for (TraderConfig trader : plugin.traderConfigs().values()) {
            if (trader.enabled()) enabled.add(trader);
        }
        if (enabled.isEmpty() || !plugin.config().scheduleEnabled()) {
            scheduleNextEvent();
            return;
        }

        // Interval-scheduled traders (such as Ghost Trader) run independently and
        // must not block the ordinary event schedule while they are active.
        for (TraderInstance instance : instances) {
            if (instance.alive() && instance.config().spawnIntervalSeconds() <= 0) {
                scheduleNextEvent();
                return;
            }
        }

        TraderInstance spawned = null;
        for (int attempt = 0; attempt < enabled.size(); attempt++) {
            TraderConfig config = enabled.get(cycleIndex % enabled.size());
            cycleIndex = (cycleIndex + 1) % enabled.size();

            // Keep the old rotation slots, but skip interval-scheduled traders
            // rather than running them from the global clock.
            if (config.spawnIntervalSeconds() > 0) continue;
            try { spawned = spawn(config, false); } catch (Throwable ignored) { spawned = null; }
            if (spawned != null) break;
        }
        if (spawned != null) {
            try { plugin.announcer().eventStarted(Collections.singletonList(spawned)); } catch (Throwable ignored) {}
        }
        scheduleNextEvent();
    }

    public String nextEventTime() {
        long delay = nextEventDelaySeconds();
        if (delay < 0) return "—";
        long abs = (java.time.LocalTime.now().toSecondOfDay() + delay) % (24L * 3600L);
        return String.format("%02d:%02d", abs / 3600, (abs % 3600) / 60);
    }

    public String nextEventTime(String traderId) {
        if (traderId == null) return nextEventTime();
        TraderConfig config = plugin.traderConfigs().get(traderId.toLowerCase(Locale.ROOT));
        if (config == null || config.spawnIntervalSeconds() <= 0) return nextEventTime();

        long nextAt = nextIntervalSpawnAtMillis.getOrDefault(config.id().toLowerCase(Locale.ROOT),
                System.currentTimeMillis() + config.spawnIntervalSeconds() * 1000L);
        long delaySeconds = Math.max(0L, (nextAt - System.currentTimeMillis() + 999L) / 1000L);
        java.time.LocalTime next = java.time.LocalTime.now().plusSeconds(delaySeconds);
        return String.format("%02d:%02d", next.getHour(), next.getMinute());
    }

    public TraderInstance spawn(TraderConfig config) {
        return spawn(config, true);
    }

    public TraderInstance spawn(TraderConfig config, boolean announce) {
        if (config == null || !config.enabled()) return null;
        if (count(config.id()) >= config.maxSimultaneous()) return null;

        Location loc = findLocation(config);
        if (loc == null) return null;
        return spawnInternal(config, loc, announce);
    }

    public TraderInstance spawnAt(TraderConfig config, Location loc) {
        return spawnInternal(config, loc, true);
    }

    private TraderInstance spawnInternal(TraderConfig config, Location loc, boolean announce) {
        if (config == null || !config.enabled() || loc == null || loc.getWorld() == null) return null;
        World world = loc.getWorld();
        ensureChunkLoaded(world, loc.getBlockX(), loc.getBlockZ());

        int top = world.getMaxHeight() - 8;
        int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);
        int safeY = config.hasFixedSpawnLocation()
                ? findSafeYNear(world, loc.getBlockX(), loc.getBlockZ(), loc.getBlockY(), top, floor)
                : findSafeY(world, loc.getBlockX(), loc.getBlockZ(), top, floor);
        if (safeY < floor) safeY = findSafeY(world, loc.getBlockX(), loc.getBlockZ(), top, floor);
        if (safeY > 0) {
            loc = new Location(world, loc.getX(), safeY, loc.getZ(), loc.getYaw(), loc.getPitch());
        }

        Location schematicAnchor = loc.clone().add(0, 1, 0);
        SchematicSnapshot snapshot = null;
        if (config.schematic().enabled()) {
            try {
                Location anchor = config.schematic().anchor(schematicAnchor);
                snapshot = plugin.schematics().pasteWithBackup(anchor, config.schematic());
            } catch (Throwable ignored) {}
        }

        if (snapshot != null) {
            Location stand = snapshot.standingSpot();
            if (stand != null) {
                loc = new Location(world, stand.getX(), stand.getY(), stand.getZ(), loc.getYaw(), loc.getPitch());
            } else if (!snapshot.placedBlocks().isEmpty()) {
                int topY = schematicAnchor.getBlockY();
                for (Location placed : snapshot.placedBlocks()) {
                    if (placed.getBlockY() > topY) topY = placed.getBlockY();
                }
                loc = new Location(world, loc.getX(), topY + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
        }

        loc.setYaw(random.nextInt(360) - 180.0F);
        TraderInstance instance = new TraderInstance(plugin, config, loc);
        if (snapshot != null) instance.setSchematicSnapshot(snapshot);
        instance.spawn();
        byEntity.put(instance.entityId(), instance);

        try {
            TraderRegion region = regionManager.createRegion(loc, config, instance);
            instance.setRegion(region);
        } catch (Throwable ignored) {}

        instances.add(instance);
        if (instances.size() == 1) {
            eventEndedAnnounced = false;
        }
        if (announce) {
            try { plugin.announcer().spawned(instance); } catch (Throwable ignored) {}
        }

        return instance;
    }

    // ─── Оптимизированный поиск места для спавна (0% лагов) ───────────
    public Location findLocation(TraderConfig config) {
        if (config.hasFixedSpawnLocation()) {
            return findFixedLocation(config);
        }

        List<String> worlds = new ArrayList<>(config.worlds());
        if (worlds.isEmpty()) worlds.add("world");
        Collections.shuffle(worlds, random);

        AppConfig cfg = plugin.config();

        for (String name : worlds) {
            World world = Bukkit.getWorld(name);
            if (world == null) continue;

            List<int[]> regionBounds = protectedRegionBounds(world);

            // 1. Сначала ищем среди уже загруженных чанков (0 мс задержки!)
            Location fromLoaded = tryFindFromLoadedChunks(world, config, regionBounds);
            if (fromLoaded != null) {
                return fromLoaded;
            }

            // 2. Если в загруженных чанках подходящего места нет, делаем быстрый поиск с ограничением
            Location spawn = world.getSpawnLocation();
            double cx = spawn.getX(), cz = spawn.getZ();
            int top = world.getMaxHeight() - 8;
            int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);

            for (int attempt = 0; attempt < 35; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = cfg.minRadius() + random.nextDouble() * (cfg.maxRadius() - cfg.minRadius());
                int x = (int) (cx + Math.cos(angle) * dist);
                int z = (int) (cz + Math.sin(angle) * dist);

                if (!isFarFromRegions(world, x, z, REGION_PADDING, regionBounds)) continue;

                ensureChunkLoaded(world, x, z);

                int y = findSafeY(world, x, z, top, floor);
                if (y < 0) continue;
                if (!isFlatEnough(world, x, z, y)) continue;

                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }

        // Fallback: spawn
        for (String name : worlds) {
            World world = Bukkit.getWorld(name);
            if (world == null) continue;
            Location s = world.getSpawnLocation();
            int y = findSafeY(world, s.getBlockX(), s.getBlockZ(), world.getMaxHeight() - 8, 0);
            if (y < 1) y = 64;
            for (int dy = 0; dy < 3; dy++) {
                world.getBlockAt(s.getBlockX(), y + dy, s.getBlockZ()).setType(Material.AIR, false);
            }
            return new Location(world, s.getBlockX() + 0.5, y + 1, s.getBlockZ() + 0.5);
        }
        return null;
    }

    private Location findFixedLocation(TraderConfig config) {
        World world = Bukkit.getWorld(config.fixedSpawnWorld());
        if (world == null) return null;

        int centerX = (int) Math.floor(config.fixedSpawnX());
        int centerZ = (int) Math.floor(config.fixedSpawnZ());
        int top = world.getMaxHeight() - 8;
        int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);
        int searchRadius = config.fixedSpawnSearchRadius();

        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    ensureChunkLoaded(world, x, z);

                    int y = findSafeYNear(world, x, z, (int) Math.floor(config.fixedSpawnY()), top, floor);
                    if (y < floor) y = findSafeY(world, x, z, top, floor);
                    if (y < floor || y + 1 >= world.getMaxHeight()) continue;
                    Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                    if (world.getWorldBorder().isInside(candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private Location tryFindFromLoadedChunks(World world, TraderConfig config, List<int[]> regionBounds) {
        Chunk[] loaded = world.getLoadedChunks();
        if (loaded == null || loaded.length == 0) return null;

        List<Chunk> shuffled = new ArrayList<>(Arrays.asList(loaded));
        Collections.shuffle(shuffled, random);

        AppConfig cfg = plugin.config();
        int top = world.getMaxHeight() - 8;
        int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);
        Location spawn = world.getSpawnLocation();
        double cx = spawn.getX(), cz = spawn.getZ();

        for (Chunk chunk : shuffled) {
            int x = (chunk.getX() << 4) + 8;
            int z = (chunk.getZ() << 4) + 8;

            double dx = x - cx;
            double dz = z - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < cfg.minRadius() || dist > cfg.maxRadius()) continue;

            if (!isFarFromRegions(world, x, z, REGION_PADDING, regionBounds)) continue;

            int y = findSafeY(world, x, z, top, floor);
            if (y < 0) continue;
            if (!isFlatEnough(world, x, z, y)) continue;

            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private int findSafeY(World world, int x, int z, int top, int floor) {
        int minY = Math.max(floor, SchematicConfig.getMinHeight(world) + 1);
        int maxY = Math.min(top, world.getMaxHeight() - 3);
        if (maxY <= minY) return -1;

        int highest = world.getHighestBlockYAt(x, z);
        int start = Math.min(maxY, Math.max(minY, highest + 2));
        for (int y = start; y >= minY; y--) {
            if (isSafeSpawnY(world, x, z, y)) return y;
        }
        return -1;
    }

    private int findSafeYNear(World world, int x, int z, int preferredY, int top, int floor) {
        int minY = Math.max(floor, SchematicConfig.getMinHeight(world) + 1);
        int maxY = Math.min(top, world.getMaxHeight() - 3);
        int highest = world.getHighestBlockYAt(x, z);
        maxY = Math.min(maxY, highest + 1);
        if (maxY <= minY) return -1;

        int start = Math.max(minY, Math.min(maxY, preferredY));
        int maxDistance = Math.max(start - minY, maxY - start);
        for (int distance = 0; distance <= maxDistance; distance++) {
            int above = start + distance;
            if (above <= maxY && isSafeSpawnY(world, x, z, above)) return above;
            if (distance > 0) {
                int below = start - distance;
                if (below >= minY && isSafeSpawnY(world, x, z, below)) return below;
            }
        }
        return -1;
    }

    private boolean isSafeSpawnY(World world, int x, int z, int y) {
        Block floorBlock = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        return solid(floorBlock) && !liquid(floorBlock)
                && !liquid(feet) && !liquid(head)
                && feet.isEmpty() && head.isEmpty();
    }

    private boolean isFlatEnough(World world, int x, int z, int y) {
        for (int dx = -3; dx <= 3; dx += 2) {
            for (int dz = -3; dz <= 3; dz += 2) {
                if (dx == 0 && dz == 0) continue;
                int checkY = findSafeY(world, x + dx, z + dz, y + 3, y - 3);
                if (checkY < 0 || Math.abs(checkY - y) > 1) return false;
            }
        }
        return true;
    }

    private List<int[]> protectedRegionBounds(World world) {
        try {
            com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world);
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.managers.RegionManager rm = wg.getPlatform().getRegionContainer()
                    .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            if (rm == null) return null;
            List<int[]> bounds = new ArrayList<>();
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion r : rm.getRegions().values()) {
                String id = r.getId().toLowerCase();
                if (id.startsWith("elytrix_") || id.equals("__global__")) continue;
                com.sk89q.worldedit.math.BlockVector3 min = r.getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 max = r.getMaximumPoint();
                bounds.add(new int[]{min.getBlockX(), max.getBlockX(), min.getBlockZ(), max.getBlockZ()});
            }
            return bounds;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isFarFromRegions(World world, int x, int z, int padding,
                                     List<int[]> regionBounds) {
        for (TraderInstance inst : instances) {
            if (inst.location().getWorld() != world) continue;
            double dx = inst.location().getBlockX() - x;
            double dz = inst.location().getBlockZ() - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 30 + padding) return false;
        }
        if (regionBounds != null) {
            for (int[] b : regionBounds) {
                if (x >= b[0] - padding && x <= b[1] + padding &&
                    z >= b[2] - padding && z <= b[3] + padding) {
                    return false;
                }
            }
        }
        return true;
    }

    private void ensureChunkLoaded(World world, int x, int z) {
        try {
            int cx = x >> 4, cz = z >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                world.loadChunk(cx, cz, true);
                world.getChunkAt(cx, cz);
            }
        } catch (Throwable ignored) {}
    }

    private boolean solid(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == null || block.isEmpty()) return false;
        String name = type.name().toUpperCase(Locale.ROOT);
        if (name.contains("LEAVES")) return false;
        if (name.contains("LOG")) return true;
        return type.isSolid() || type.isOccluding();
    }

    private boolean liquid(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.WATER || type == Material.LAVA;
    }

    // ─── Public API ────────────────────────────────────────────────

    public void stopImmediate() {
        if (scheduleTask != null) { scheduleTask.cancel(); scheduleTask = null; }
        for (BukkitTask task : intervalScheduleTasks.values()) {
            if (task != null) task.cancel();
        }
        intervalScheduleTasks.clear();
        nextIntervalSpawnAtMillis.clear();
        for (TraderInstance inst : new ArrayList<>(instances)) {
            despawnImmediate(inst);
        }
        instances.clear();
        regionManager.clearAll();
        eventEndedAnnounced = true;
    }

    private void checkEventEnded() {
        if (!plugin.isEnabled() || eventEndedAnnounced) return;
        for (TraderInstance inst : instances) {
            if (inst.alive()) return;
        }
        eventEndedAnnounced = true;
        try { plugin.announcer().eventEnded(); } catch (Throwable ignored) {}
    }

    public void despawnImmediate(TraderInstance instance) {
        if (instance == null) return;
        SchematicSnapshot snap = instance.schematicSnapshot();
        if (snap != null) {
            try { snap.restore(); } catch (Throwable ignored) {}
        }
        removeRegion(instance);
        instance.finalizeDespawn();
        plugin.announcer().expired(instance);
        checkEventEnded();
    }

    public void expire(TraderInstance instance) {
        if (instance == null || instance.isDespawning()) return;
        instance.despawnAnimated();
        instance.stopAura();
        SchematicSnapshot snap = instance.schematicSnapshot();
        animation.play(instance, instance.region(), snap, () -> {
            if (snap != null) {
                try { snap.restore(); } catch (Throwable ignored) {}
            }
            removeRegion(instance);
            instance.finalizeDespawn();
            plugin.announcer().expired(instance);
            checkEventEnded();
        });
    }

    private void removeRegion(TraderInstance instance) {
        if (instance == null) return;
        TraderRegion region = instance.region();
        if (region != null) {
            regionManager.removeRegion(region);
            instance.setRegion(null);
        }
    }

    public void forget(TraderInstance instance) {
        instances.remove(instance);
        byEntity.remove(instance.entityId());
    }

    private final Map<Integer, TraderInstance> byEntity = new HashMap<>();

    public TraderInstance byEntityId(int entityId) {
        return byEntity.get(entityId);
    }

    public void reRegisterByEntity(TraderInstance instance) {
        if (instance != null && instance.entityId() > 0) {
            byEntity.put(instance.entityId(), instance);
        }
    }

    public TraderInstance spawnForce(TraderConfig config, Location loc) {
        return spawnForce(config, loc, true);
    }

    public TraderInstance spawnForce(TraderConfig config, Location loc, boolean announce) {
        if (config == null || !config.enabled() || loc == null || loc.getWorld() == null) return null;
        return spawnInternal(config, loc, announce);
    }

    public int stopAll(String traderId, boolean withAnimation) {
        int removed = 0;
        for (TraderInstance inst : new ArrayList<>(instances)) {
            if (traderId != null && !inst.config().id().equalsIgnoreCase(traderId)) continue;
            if (withAnimation) despawnAnimated(inst, null);
            else despawnImmediate(inst);
            removed++;
        }
        return removed;
    }

    public int despawnAllImmediate(String traderId) {
        return stopAll(traderId, false);
    }

    public void despawnAnimated(TraderInstance inst, Runnable after) {
        if (inst == null) { if (after != null) after.run(); return; }
        expire(inst);
    }

    public TraderInstance nearest(Player player, String traderId) {
        if (player == null) return null;
        TraderInstance nearest = null;
        double best = Double.MAX_VALUE;
        for (TraderInstance instance : instances) {
            if (!instance.alive()) continue;
            if (traderId != null && !instance.config().id().equalsIgnoreCase(traderId)) continue;
            double dist = instance.distance(player);
            if (dist < best) { best = dist; nearest = instance; }
        }
        return nearest;
    }

    public List<TraderInstance> instances() { return instances; }

    public int count(String traderId) {
        int total = 0;
        for (TraderInstance instance : instances) {
            if (instance.alive() && instance.config().id().equalsIgnoreCase(traderId)) total++;
        }
        return total;
    }

    public int total() {
        int total = 0;
        for (TraderInstance instance : instances) {
            if (instance.alive()) total++;
        }
        return total;
    }
}
