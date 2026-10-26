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

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class TraderManager {

    private static final int REGION_PADDING = 20;
    private static final int MAX_SCHEMATIC_CHUNKS_TO_PRELOAD = 256;

    private final Main plugin;
    private final List<TraderInstance> instances = new ArrayList<>();
    private final Random random = new Random();
    private final RegionManager regionManager;
    private final DisappearAnimation animation;
    private BukkitTask scheduleTask;
    private final Map<String, BukkitTask> intervalScheduleTasks = new HashMap<>();
    private final Map<String, Long> nextIntervalSpawnAtMillis = new HashMap<>();
    private final Map<String, Location> lastFixedSpawnLocations = new HashMap<>();
    private boolean asyncChunkApiWarningIssued;
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

        spawnAsync(config, false, false, spawned -> {
            if (spawned != null) {
                try { plugin.announcer().eventStarted(Collections.singletonList(spawned)); }
                catch (Throwable ignored) {}
            }
        });
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

        scheduleNextEvent();
        spawnNextScheduledCandidate(enabled, 0);
    }

    private void spawnNextScheduledCandidate(List<TraderConfig> enabled, int attempt) {
        if (attempt >= enabled.size()) return;
        TraderConfig config = enabled.get(cycleIndex % enabled.size());
        cycleIndex = (cycleIndex + 1) % enabled.size();

        // Keep the old rotation: interval traders are skipped, and the next
        // ordinary trader is tried only if this candidate cannot be spawned.
        if (config.spawnIntervalSeconds() > 0) {
            spawnNextScheduledCandidate(enabled, attempt + 1);
            return;
        }
        spawnAsync(config, false, false, spawned -> {
            if (spawned != null) {
                try { plugin.announcer().eventStarted(Collections.singletonList(spawned)); }
                catch (Throwable ignored) {}
            } else {
                spawnNextScheduledCandidate(enabled, attempt + 1);
            }
        });
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

    /**
     * Spawns without ever synchronously loading a chunk. Unloaded chunks are
     * requested through Paper's async API (when available); terrain is inspected
     * only after the chunks are confirmed loaded on the server thread.
     */
    public void spawnAsync(TraderConfig config, boolean force, boolean announce,
                           Consumer<TraderInstance> completion) {
        if (!Bukkit.isPrimaryThread()) {
            runOnMainThread(() -> spawnAsync(config, force, announce, completion));
            return;
        }
        if (config == null || !config.enabled()
                || (!force && count(config.id()) >= config.maxSimultaneous())) {
            completeSpawn(completion, null);
            return;
        }

        if (config.hasFixedSpawnLocation()) {
            loadFixedSpawnAreaAsync(config, () -> {
                if (!plugin.isEnabled()) return;
                Location location = findFixedLocation(config);
                if (location == null) completeSpawn(completion, null);
                else spawnAtLocationAsync(config, location, announce, completion);
            });
            return;
        }

        Location alreadyLoaded = findLocation(config);
        if (alreadyLoaded != null) {
            spawnAtLocationAsync(config, alreadyLoaded, announce, completion);
            return;
        }

        List<Location> candidates = randomSpawnCandidates(config, 8);
        if (candidates.isEmpty()) {
            completeSpawn(completion, null);
            return;
        }
        loadChunksAsync(chunkTargets(candidates), () -> {
            if (!plugin.isEnabled()) return;
            Location location = findSafeCandidate(config, candidates);
            if (location == null) completeSpawn(completion, null);
            else spawnAtLocationAsync(config, location, announce, completion);
        });
    }

    private void spawnAtLocationAsync(TraderConfig config, Location location, boolean announce,
                                      Consumer<TraderInstance> completion) {
        if (!config.schematic().enabled() || !plugin.schematics().available()) {
            completeSpawn(completion, spawnInternal(config, location, announce));
            return;
        }

        Location schematicAnchor = config.schematic().anchor(location.clone().add(0, 1, 0));
        int[] bounds = plugin.schematics().requiredChunkBounds(schematicAnchor, config.schematic());
        if (bounds == null) {
            completeSpawn(completion, spawnInternal(config, location, announce));
            return;
        }

        long chunkCount = (long) (bounds[1] - bounds[0] + 1)
                * (long) (bounds[3] - bounds[2] + 1);
        if (chunkCount <= 0 || chunkCount > MAX_SCHEMATIC_CHUNKS_TO_PRELOAD) {
            plugin.getLogger().warning("Skipping schematic paste for trader " + config.id()
                    + ": affected area is too large to preload safely.");
            completeSpawn(completion, spawnInternal(config, location, announce));
            return;
        }

        Set<Long> chunks = new LinkedHashSet<>();
        for (int chunkX = bounds[0]; chunkX <= bounds[1]; chunkX++) {
            for (int chunkZ = bounds[2]; chunkZ <= bounds[3]; chunkZ++) {
                chunks.add(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL));
            }
        }
        Map<World, Set<Long>> targets = new IdentityHashMap<>();
        targets.put(location.getWorld(), chunks);
        loadChunksAsync(targets, () -> {
            if (!plugin.isEnabled()) return;
            completeSpawn(completion, spawnInternal(config, location, announce));
        });
    }

    private void completeSpawn(Consumer<TraderInstance> completion, TraderInstance instance) {
        if (completion == null) return;
        try { completion.accept(instance); } catch (Throwable ignored) {}
    }

    public TraderInstance spawnAt(TraderConfig config, Location loc) {
        return spawnInternal(config, loc, true);
    }

    private TraderInstance spawnInternal(TraderConfig config, Location loc, boolean announce) {
        if (config == null || !config.enabled() || loc == null || loc.getWorld() == null) return null;
        World world = loc.getWorld();
        if (!isChunkLoadedAt(world, loc.getBlockX(), loc.getBlockZ())) return null;

        int top = world.getMaxHeight() - 8;
        int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);
        int safeY = config.surfaceOnly()
                ? findSurfaceSafeY(world, loc.getBlockX(), loc.getBlockZ(), top, floor)
                : config.hasFixedSpawnLocation()
                    ? findSafeYNear(world, loc.getBlockX(), loc.getBlockZ(), loc.getBlockY(), top, floor)
                    : findSafeY(world, loc.getBlockX(), loc.getBlockZ(), top, floor);
        if (safeY < floor || safeY + 1 >= world.getMaxHeight()) return null;
        loc = new Location(world, loc.getX(), safeY, loc.getZ(), loc.getYaw(), loc.getPitch());

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
        if (config.hasFixedSpawnLocation() && instance.alive()) {
            lastFixedSpawnLocations.put(config.id().toLowerCase(Locale.ROOT), instance.location().clone());
        }
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

        for (String name : worlds) {
            World world = Bukkit.getWorld(name);
            if (world == null) continue;
            Location fromLoaded = tryFindFromLoadedChunks(world, config, protectedRegionBounds(world));
            if (fromLoaded != null) return fromLoaded;
        }
        return null;
    }

    private List<Location> randomSpawnCandidates(TraderConfig config, int limit) {
        List<Location> candidates = new ArrayList<>();
        List<String> worlds = new ArrayList<>(config.worlds());
        if (worlds.isEmpty()) worlds.add("world");
        Collections.shuffle(worlds, random);

        AppConfig cfg = plugin.config();
        Map<World, Set<Long>> seenChunks = new IdentityHashMap<>();
        for (String name : worlds) {
            World world = Bukkit.getWorld(name);
            if (world == null) continue;
            Location spawn = world.getSpawnLocation();
            List<int[]> regions = protectedRegionBounds(world);
            Set<Long> seen = seenChunks.computeIfAbsent(world, ignored -> new HashSet<>());
            double radiusRange = Math.max(0.0D, cfg.maxRadius() - cfg.minRadius());

            for (int attempt = 0; attempt < 128 && candidates.size() < limit; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = cfg.minRadius() + random.nextDouble() * radiusRange;
                int rawX = (int) Math.floor(spawn.getX() + Math.cos(angle) * distance);
                int rawZ = (int) Math.floor(spawn.getZ() + Math.sin(angle) * distance);
                int chunkX = rawX >> 4;
                int chunkZ = rawZ >> 4;
                long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
                if (!seen.add(key)) continue;

                int x = (chunkX << 4) + 8;
                int z = (chunkZ << 4) + 8;
                double dx = x - spawn.getX();
                double dz = z - spawn.getZ();
                double centerDistance = Math.sqrt(dx * dx + dz * dz);
                if (centerDistance < cfg.minRadius() || centerDistance > cfg.maxRadius()) continue;
                if (!isFarFromRegions(world, x, z, REGION_PADDING, regions)) continue;
                Location candidate = new Location(world, x + 0.5D, 0.0D, z + 0.5D);
                if (!world.getWorldBorder().isInside(candidate)) continue;
                candidates.add(candidate);
            }
            if (candidates.size() >= limit) break;
        }
        return candidates;
    }

    private Location findSafeCandidate(TraderConfig config, List<Location> candidates) {
        for (Location candidate : candidates) {
            World world = candidate.getWorld();
            if (world == null || !isChunkLoadedAt(world, candidate.getBlockX(), candidate.getBlockZ())) continue;
            int x = candidate.getBlockX();
            int z = candidate.getBlockZ();
            if (!isFarFromRegions(world, x, z, REGION_PADDING, protectedRegionBounds(world))) continue;

            int top = world.getMaxHeight() - 8;
            int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);
            int y = findSpawnY(config, world, x, z, top, floor);
            if (y < floor || y + 1 >= world.getMaxHeight()) continue;
            Location safe = new Location(world, x + 0.5D, y, z + 0.5D);
            if (!world.getWorldBorder().isInside(safe)) continue;
            if (!isFlatEnough(config, world, x, z, y)) continue;
            return safe;
        }
        return null;
    }

    private Map<World, Set<Long>> chunkTargets(Collection<Location> locations) {
        Map<World, Set<Long>> targets = new IdentityHashMap<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null) continue;
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            targets.computeIfAbsent(location.getWorld(), ignored -> new LinkedHashSet<>()).add(key);
        }
        return targets;
    }

    private void loadFixedSpawnAreaAsync(TraderConfig config, Runnable completion) {
        World world = Bukkit.getWorld(config.fixedSpawnWorld());
        if (world == null) {
            runOnMainThread(completion);
            return;
        }

        int centerX = (int) Math.floor(config.fixedSpawnX());
        int centerZ = (int) Math.floor(config.fixedSpawnZ());
        int radius = config.fixedSpawnSearchRadius();
        int minChunkX = (centerX - radius) >> 4;
        int maxChunkX = (centerX + radius) >> 4;
        int minChunkZ = (centerZ - radius) >> 4;
        int maxChunkZ = (centerZ + radius) >> 4;
        Set<Long> chunks = new LinkedHashSet<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL));
            }
        }
        Map<World, Set<Long>> targets = new IdentityHashMap<>();
        targets.put(world, chunks);
        loadChunksAsync(targets, completion);
    }

    private void loadChunksAsync(Map<World, Set<Long>> targets, Runnable completion) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<World, Set<Long>> entry : targets.entrySet()) {
            World world = entry.getKey();
            for (long key : entry.getValue()) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                futures.add(requestChunkLoadAsync(world, chunkX, chunkZ));
            }
        }
        CompletableFuture<?>[] all = futures.toArray(new CompletableFuture<?>[0]);
        CompletableFuture.allOf(all).whenComplete((ignored, error) -> runOnMainThread(completion));
    }

    private CompletableFuture<Boolean> requestChunkLoadAsync(World world, int chunkX, int chunkZ) {
        try {
            if (world.isChunkLoaded(chunkX, chunkZ)) return CompletableFuture.completedFuture(true);
        } catch (Throwable ignored) {
            return CompletableFuture.completedFuture(false);
        }

        List<Method> methods = new ArrayList<>();
        try {
            for (Method method : world.getClass().getMethods()) {
                if (!"getChunkAtAsync".equals(method.getName())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length < 2 || parameters[0] != int.class || parameters[1] != int.class) continue;
                boolean supported = true;
                boolean hasConsumer = false;
                for (int i = 2; i < parameters.length; i++) {
                    if (parameters[i] == boolean.class) continue;
                    if (java.util.function.Consumer.class.isAssignableFrom(parameters[i])) {
                        hasConsumer = true;
                        continue;
                    }
                    supported = false;
                    break;
                }
                if (!supported || (!hasConsumer
                        && !CompletionStage.class.isAssignableFrom(method.getReturnType()))) continue;
                methods.add(method);
            }
        } catch (Throwable ignored) {
        }

        methods.sort((left, right) -> {
            boolean leftCallback = hasConsumerParameter(left);
            boolean rightCallback = hasConsumerParameter(right);
            if (leftCallback != rightCallback) return leftCallback ? 1 : -1;
            return Integer.compare(right.getParameterCount(), left.getParameterCount());
        });

        for (Method method : methods) {
            CompletableFuture<Boolean> loaded = new CompletableFuture<>();
            try {
                java.util.function.Consumer<Object> callback = ignored -> runOnMainThread(() ->
                        loaded.complete(isChunkLoaded(world, chunkX, chunkZ)));
                Object[] arguments = asyncChunkArguments(method, chunkX, chunkZ, callback);
                Object result = method.invoke(world, arguments);
                if (result instanceof CompletionStage<?>) {
                    ((CompletionStage<?>) result).whenComplete((ignored, error) -> runOnMainThread(() ->
                            loaded.complete(error == null && isChunkLoaded(world, chunkX, chunkZ))));
                    return loaded;
                }
                if (hasConsumerParameter(method)) return loaded;
                if (isChunkLoaded(world, chunkX, chunkZ)) return CompletableFuture.completedFuture(true);
            } catch (Throwable ignored) {
                // Try another Paper API overload, if present.
            }
        }

        if (!asyncChunkApiWarningIssued) {
            asyncChunkApiWarningIssued = true;
            plugin.getLogger().warning("Paper async chunk loading is unavailable; trader spawn will skip unloaded chunks instead of blocking the server thread.");
        }
        return CompletableFuture.completedFuture(false);
    }

    private boolean hasConsumerParameter(Method method) {
        for (Class<?> parameter : method.getParameterTypes()) {
            if (java.util.function.Consumer.class.isAssignableFrom(parameter)) return true;
        }
        return false;
    }

    private Object[] asyncChunkArguments(Method method, int chunkX, int chunkZ,
                                         java.util.function.Consumer<Object> callback) {
        Class<?>[] parameters = method.getParameterTypes();
        Object[] arguments = new Object[parameters.length];
        arguments[0] = chunkX;
        arguments[1] = chunkZ;
        boolean firstBoolean = true;
        for (int i = 2; i < parameters.length; i++) {
            if (parameters[i] == boolean.class) {
                arguments[i] = firstBoolean;
                firstBoolean = false;
            } else if (java.util.function.Consumer.class.isAssignableFrom(parameters[i])) {
                arguments[i] = callback;
            } else {
                throw new IllegalArgumentException("Unsupported async chunk API overload");
            }
        }
        return arguments;
    }

    private boolean isChunkLoaded(World world, int chunkX, int chunkZ) {
        try { return world != null && world.isChunkLoaded(chunkX, chunkZ); }
        catch (Throwable ignored) { return false; }
    }

    private void runOnMainThread(Runnable action) {
        if (action == null || !plugin.isEnabled()) return;
        try {
            if (Bukkit.isPrimaryThread()) action.run();
            else Bukkit.getScheduler().runTask(plugin, action);
        } catch (Throwable ignored) {
        }
    }

    private Location findFixedLocation(TraderConfig config) {
        World world = Bukkit.getWorld(config.fixedSpawnWorld());
        if (world == null) return null;

        int centerX = (int) Math.floor(config.fixedSpawnX());
        int centerZ = (int) Math.floor(config.fixedSpawnZ());
        int top = world.getMaxHeight() - 8;
        int floor = Math.max(SchematicConfig.getMinHeight(world) + 2, 0);
        int searchRadius = config.fixedSpawnSearchRadius();
        Location previous = lastFixedSpawnLocations.get(config.id().toLowerCase(Locale.ROOT));
        if (previous != null && (previous.getWorld() == null
                || !world.getName().equals(previous.getWorld().getName()))) previous = null;

        List<Location> alternatives = new ArrayList<>();
        List<Location> fallback = new ArrayList<>();
        for (int radius = 0; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (!isChunkLoadedAt(world, x, z)) continue;

                    int y = config.surfaceOnly()
                            ? findSurfaceSafeY(world, x, z, top, floor)
                            : findSafeYNear(world, x, z, (int) Math.floor(config.fixedSpawnY()), top, floor);
                    if (y < floor && !config.surfaceOnly()) y = findSafeY(world, x, z, top, floor);
                    if (y < floor || y + 1 >= world.getMaxHeight()) continue;
                    Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                    if (!world.getWorldBorder().isInside(candidate)) continue;
                    fallback.add(candidate);

                    // Keep each recurring fixed-location trader near its anchor,
                    // but avoid reusing the previous block if another safe spot exists.
                    long dxFromPrevious = previous == null ? Long.MAX_VALUE
                            : (long) candidate.getBlockX() - previous.getBlockX();
                    long dzFromPrevious = previous == null ? Long.MAX_VALUE
                            : (long) candidate.getBlockZ() - previous.getBlockZ();
                    if (previous == null || dxFromPrevious * dxFromPrevious
                            + dzFromPrevious * dzFromPrevious >= 4L) {
                        alternatives.add(candidate);
                    }
                }
            }
        }

        List<Location> choices = alternatives.isEmpty() ? fallback : alternatives;
        return choices.isEmpty() ? null : choices.get(random.nextInt(choices.size()));
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

            int y = findSpawnY(config, world, x, z, top, floor);
            if (y < floor) continue;
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (!world.getWorldBorder().isInside(candidate)) continue;
            if (!isFlatEnough(config, world, x, z, y)) continue;

            return candidate;
        }
        return null;
    }

    private int findSpawnY(TraderConfig config, World world, int x, int z, int top, int floor) {
        return config.surfaceOnly()
                ? findSurfaceSafeY(world, x, z, top, floor)
                : findSafeY(world, x, z, top, floor);
    }

    private int findSurfaceSafeY(World world, int x, int z, int top, int floor) {
        if (!isChunkLoadedAt(world, x, z)) return -1;
        int minY = Math.max(floor, SchematicConfig.getMinHeight(world) + 1);
        int maxY = Math.min(top, world.getMaxHeight() - 3);
        if (maxY <= minY) return -1;

        // Only test the highest ground position. Do not descend into caves or mines.
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (y < minY || y > maxY) return -1;
        return isSafeSpawnY(world, x, z, y) ? y : -1;
    }

    private int findSafeY(World world, int x, int z, int top, int floor) {
        if (!isChunkLoadedAt(world, x, z)) return -1;
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
        if (!isChunkLoadedAt(world, x, z)) return -1;
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
        if (!isChunkLoadedAt(world, x, z) || y <= SchematicConfig.getMinHeight(world)
                || y + 1 >= world.getMaxHeight()) return false;
        Block floorBlock = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        return solid(floorBlock) && !liquid(floorBlock)
                && !liquid(feet) && !liquid(head)
                && feet.isEmpty() && head.isEmpty();
    }

    private boolean isFlatEnough(TraderConfig config, World world, int x, int z, int y) {
        for (int dx = -3; dx <= 3; dx += 2) {
            for (int dz = -3; dz <= 3; dz += 2) {
                if (dx == 0 && dz == 0) continue;
                int checkY = findSpawnY(config, world, x + dx, z + dz, y + 3, y - 3);
                if (checkY < 0 || Math.abs(checkY - y) > 1) return false;
            }
        }
        return true;
    }

    private boolean isChunkLoadedAt(World world, int x, int z) {
        return world != null && world.isChunkLoaded(x >> 4, z >> 4);
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
