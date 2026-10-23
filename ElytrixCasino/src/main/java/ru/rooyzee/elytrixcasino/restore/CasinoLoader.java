package ru.rooyzee.elytrixcasino.restore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.automate.Automate;
import ru.rooyzee.elytrixcasino.data.DataManager;
import ru.rooyzee.elytrixcasino.miner.Miner;
import ru.rooyzee.elytrixcasino.roulette.Roulette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Восстановление рулеток, автоматов и NPC Минёров из data.yml после старта/перезагрузки сервера.
 */
public class CasinoLoader implements Listener {

    private static final int RETRY_PERIOD_TICKS = 100;
    private static final int MAX_RETRIES = 60;
    private static final int MAX_ATTEMPTS = 3;

    private final Main plugin;
    private final Map<String, DataManager.StoredRoulette> pendingRoulettes = new LinkedHashMap<>();
    private final Map<String, DataManager.StoredAutomate> pendingAutomates = new LinkedHashMap<>();
    private final Map<String, DataManager.StoredMiner> pendingMiners = new LinkedHashMap<>();

    private final Map<String, Integer> attempts = new HashMap<>();

    private BukkitTask retryTask;
    private int retries;

    public CasinoLoader(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTask(plugin, this::reload);
    }

    public void reload() {
        cancelRetries();
        pendingRoulettes.clear();
        pendingAutomates.clear();
        pendingMiners.clear();
        attempts.clear();
        retries = 0;

        DataManager data = plugin.getDataManager();
        List<DataManager.StoredRoulette> roulettes = data.readRoulettes();
        List<DataManager.StoredAutomate> automates = data.readAutomates();
        List<DataManager.StoredMiner> miners = data.readMiners();
        for (DataManager.StoredRoulette stored : roulettes) {
            pendingRoulettes.put(stored.getId(), stored);
        }
        for (DataManager.StoredAutomate stored : automates) {
            pendingAutomates.put(stored.getId(), stored);
        }
        for (DataManager.StoredMiner stored : miners) {
            pendingMiners.put(stored.getId(), stored);
        }

        int spawnedRoulettes = spawnPendingRoulettes();
        int spawnedAutomates = spawnPendingAutomates();
        int spawnedMiners = spawnPendingMiners();

        if (roulettes.isEmpty() && automates.isEmpty() && miners.isEmpty()) {
            plugin.getLogger().info("В data.yml нет сохранённых объектов казино.");
        } else {
            plugin.getLogger().info("Загружено рулеток: " + spawnedRoulettes + "/" + roulettes.size()
                    + ", автоматов: " + spawnedAutomates + "/" + automates.size()
                    + ", минёров: " + spawnedMiners + "/" + miners.size());
        }
        scheduleRetries();
    }

    public void shutdown() {
        cancelRetries();
        pendingRoulettes.clear();
        pendingAutomates.clear();
        pendingMiners.clear();
        attempts.clear();
    }

    public boolean hasPending() {
        return !pendingRoulettes.isEmpty() || !pendingAutomates.isEmpty() || !pendingMiners.isEmpty();
    }

    public int pendingCount() {
        return pendingRoulettes.size() + pendingAutomates.size() + pendingMiners.size();
    }

    private int spawnPendingRoulettes() {
        int spawned = 0;
        Iterator<Map.Entry<String, DataManager.StoredRoulette>> iterator = pendingRoulettes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DataManager.StoredRoulette> entry = iterator.next();
            String id = entry.getKey();
            DataManager.StoredRoulette stored = entry.getValue();

            if (isWorldMissing(stored.getNpc()) || isWorldMissing(stored.getWheel())) {
                continue;
            }
            Location npc = DataManager.deserialize(stored.getNpc());
            Location wheel = DataManager.deserialize(stored.getWheel());
            if (npc == null || wheel == null) {
                plugin.getLogger().warning("Рулетка '" + id + "' пропущена: битые координаты в data.yml.");
                iterator.remove();
                continue;
            }
            try {
                if (plugin.getRouletteManager().spawn(id, npc, wheel) != null) {
                    iterator.remove();
                    attempts.remove(id);
                    spawned++;
                } else if (!registerFailure(id, "Рулетка")) {
                    iterator.remove();
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось восстановить рулетку '" + id + "'", t);
                if (!registerFailure(id, "Рулетка")) {
                    iterator.remove();
                }
            }
        }
        return spawned;
    }

    private int spawnPendingAutomates() {
        int spawned = 0;
        Iterator<Map.Entry<String, DataManager.StoredAutomate>> iterator = pendingAutomates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DataManager.StoredAutomate> entry = iterator.next();
            String id = entry.getKey();
            DataManager.StoredAutomate stored = entry.getValue();

            if (isWorldMissing(stored.getBottom())) {
                continue;
            }
            Location bottom = DataManager.deserialize(stored.getBottom());
            if (bottom == null) {
                plugin.getLogger().warning("Автомат '" + id + "' пропущен: битые координаты в data.yml.");
                iterator.remove();
                continue;
            }
            try {
                if (plugin.getAutomateManager().spawn(id, bottom, stored.getBet()) != null) {
                    iterator.remove();
                    attempts.remove(id);
                    spawned++;
                } else if (!registerFailure(id, "Автомат")) {
                    iterator.remove();
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось восстановить автомат '" + id + "'", t);
                if (!registerFailure(id, "Автомат")) {
                    iterator.remove();
                }
            }
        }
        return spawned;
    }

    private int spawnPendingMiners() {
        int spawned = 0;
        Iterator<Map.Entry<String, DataManager.StoredMiner>> iterator = pendingMiners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DataManager.StoredMiner> entry = iterator.next();
            String id = entry.getKey();
            DataManager.StoredMiner stored = entry.getValue();

            if (isWorldMissing(stored.getNpc())) {
                continue;
            }
            Location npc = DataManager.deserialize(stored.getNpc());
            if (npc == null) {
                plugin.getLogger().warning("Минёр '" + id + "' пропущен: битые координаты в data.yml.");
                iterator.remove();
                continue;
            }
            try {
                if (plugin.getMinerManager().spawn(id, npc) != null) {
                    iterator.remove();
                    attempts.remove(id);
                    spawned++;
                } else if (!registerFailure(id, "Минёр")) {
                    iterator.remove();
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось восстановить минёра '" + id + "'", t);
                if (!registerFailure(id, "Минёр")) {
                    iterator.remove();
                }
            }
        }
        return spawned;
    }

    private boolean registerFailure(String id, String what) {
        Integer count = attempts.get(id);
        int total = (count == null ? 0 : count) + 1;
        if (total < MAX_ATTEMPTS) {
            attempts.put(id, total);
            return true;
        }
        attempts.remove(id);
        plugin.getLogger().warning(what + " '" + id + "' не восстановлен после " + total
                + " попыток. Запись осталась в data.yml — повторить можно командой /ec reload.");
        return false;
    }

    private boolean isWorldMissing(String raw) {
        String name = DataManager.worldName(raw);
        return name != null && Bukkit.getWorld(name) == null;
    }

    private void scheduleRetries() {
        if (retryTask != null || !hasPending() || !plugin.isEnabled()) {
            return;
        }
        retries = 0;
        retryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::retry, RETRY_PERIOD_TICKS, RETRY_PERIOD_TICKS);
    }

    private void retry() {
        int spawnedRoulettes = spawnPendingRoulettes();
        int spawnedAutomates = spawnPendingAutomates();
        int spawnedMiners = spawnPendingMiners();
        if (spawnedRoulettes > 0 || spawnedAutomates > 0 || spawnedMiners > 0) {
            plugin.getLogger().info("Догружено рулеток: " + spawnedRoulettes
                    + ", автоматов: " + spawnedAutomates
                    + ", минёров: " + spawnedMiners);
        }
        if (!hasPending()) {
            cancelRetries();
            return;
        }
        if (++retries >= MAX_RETRIES) {
            cancelRetries();
            reportStuck();
        }
    }

    private void cancelRetries() {
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
    }

    private void reportStuck() {
        for (DataManager.StoredRoulette stored : pendingRoulettes.values()) {
            plugin.getLogger().warning("Рулетка '" + stored.getId() + "' не восстановлена: мир '"
                    + worldOf(stored.getNpc(), stored.getWheel()) + "' не загружен.");
        }
        for (DataManager.StoredAutomate stored : pendingAutomates.values()) {
            plugin.getLogger().warning("Автомат '" + stored.getId() + "' не восстановлен: мир '"
                    + DataManager.worldName(stored.getBottom()) + "' не загружен.");
        }
        for (DataManager.StoredMiner stored : pendingMiners.values()) {
            plugin.getLogger().warning("Минёр '" + stored.getId() + "' не восстановлен: мир '"
                    + DataManager.worldName(stored.getNpc()) + "' не загружен.");
        }
    }

    private String worldOf(String first, String second) {
        String name = DataManager.worldName(first);
        return name != null ? name : String.valueOf(DataManager.worldName(second));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!hasPending() || !plugin.isEnabled()) {
            return;
        }
        final String name = event.getWorld().getName();
        if (!waitsFor(name)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            int spawnedRoulettes = spawnPendingRoulettes();
            int spawnedAutomates = spawnPendingAutomates();
            int spawnedMiners = spawnPendingMiners();
            if (spawnedRoulettes > 0 || spawnedAutomates > 0 || spawnedMiners > 0) {
                plugin.getLogger().info("Мир '" + name + "' загружен — восстановлено рулеток: "
                        + spawnedRoulettes + ", автоматов: " + spawnedAutomates + ", минёров: " + spawnedMiners);
            }
        });
    }

    private boolean waitsFor(String worldName) {
        for (DataManager.StoredRoulette stored : pendingRoulettes.values()) {
            if (worldName.equals(DataManager.worldName(stored.getNpc()))
                    || worldName.equals(DataManager.worldName(stored.getWheel()))) {
                return true;
            }
        }
        for (DataManager.StoredAutomate stored : pendingAutomates.values()) {
            if (worldName.equals(DataManager.worldName(stored.getBottom()))) {
                return true;
            }
        }
        for (DataManager.StoredMiner stored : pendingMiners.values()) {
            if (worldName.equals(DataManager.worldName(stored.getNpc()))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        List<Roulette> roulettes = new ArrayList<>(plugin.getRouletteManager().getAll());
        for (Roulette roulette : roulettes) {
            Location home = roulette.getHomeLocation();
            if (home.getWorld() == null || !home.getWorld().equals(world)) {
                continue;
            }
            String id = roulette.getId();
            pendingRoulettes.put(id, new DataManager.StoredRoulette(id,
                    DataManager.serialize(home), DataManager.serialize(roulette.getWheelLocation())));
            plugin.getRouletteManager().unload(id);
        }
        List<Automate> automates = new ArrayList<>(plugin.getAutomateManager().getAll());
        for (Automate automate : automates) {
            Location bottom = automate.getLocation();
            if (bottom.getWorld() == null || !bottom.getWorld().equals(world)) {
                continue;
            }
            String id = automate.getId();
            pendingAutomates.put(id, new DataManager.StoredAutomate(id,
                    DataManager.serialize(bottom), automate.getBet()));
            plugin.getAutomateManager().unload(id);
        }
        List<Miner> miners = new ArrayList<>(plugin.getMinerManager().getAll());
        for (Miner miner : miners) {
            Location loc = miner.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
                continue;
            }
            String id = miner.getId();
            pendingMiners.put(id, new DataManager.StoredMiner(id, DataManager.serialize(loc)));
            plugin.getMinerManager().unload(id);
        }
        scheduleRetries();
    }
}
