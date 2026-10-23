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
import ru.rooyzee.elytrixcasino.roulette.Roulette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Восстановление рулеток и автоматов из data.yml после старта/перезагрузки сервера.
 * <p>
 * Почему нельзя грузить прямо в {@code onEnable}:
 * <ul>
 *   <li>миры в этот момент могут быть ещё не подняты (например, их грузит Multiverse,
 *       который включается позже нас) — {@code Bukkit.getWorld()} вернёт {@code null},
 *       и запись просто пропустят до следующего рестарта;</li>
 *   <li>чанки на старте не загружены, а голограммы автоматов — настоящие армор-стенды:
 *       сущность, созданная в невыгруженном чанке, не появится;</li>
 *   <li>любое исключение на одной записи роняло весь цикл загрузки, из-за чего пропадали
 *       сразу и рулетки, и автоматы.</li>
 * </ul>
 * Теперь первый проход отложен на тик после полного старта сервера, каждая запись грузится
 * в своём {@code try/catch}, а всё, для чего мир ещё не готов, ждёт в очереди и поднимается
 * по {@link WorldLoadEvent} либо по таймеру. Записи из data.yml при этом никогда не удаляются.
 */
public class CasinoLoader implements Listener {

    /** Период повторных попыток, тиков (5 секунд). */
    private static final int RETRY_PERIOD_TICKS = 100;
    /** Сколько попыток ждём появления мира, прежде чем ругнуться в консоль (~5 минут). */
    private static final int MAX_RETRIES = 60;
    /** Сколько раз пробуем поднять запись, мир которой уже загружен, прежде чем сдаться. */
    private static final int MAX_ATTEMPTS = 3;

    private final Main plugin;
    private final Map<String, DataManager.StoredRoulette> pendingRoulettes = new LinkedHashMap<>();
    private final Map<String, DataManager.StoredAutomate> pendingAutomates = new LinkedHashMap<>();

    private final Map<String, Integer> attempts = new HashMap<>();

    private BukkitTask retryTask;
    private int retries;

    public CasinoLoader(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Старт восстановления: первый проход — на следующем тике, когда сервер уже поднялся,
     * все плагины включены, а миры загружены.
     */
    public void start() {
        Bukkit.getScheduler().runTask(plugin, this::reload);
    }

    /**
     * Полный перезапуск восстановления (старт сервера или /ec reload).
     */
    public void reload() {
        cancelRetries();
        pendingRoulettes.clear();
        pendingAutomates.clear();
        attempts.clear();
        retries = 0;

        DataManager data = plugin.getDataManager();
        List<DataManager.StoredRoulette> roulettes = data.readRoulettes();
        List<DataManager.StoredAutomate> automates = data.readAutomates();
        for (DataManager.StoredRoulette stored : roulettes) {
            pendingRoulettes.put(stored.getId(), stored);
        }
        for (DataManager.StoredAutomate stored : automates) {
            pendingAutomates.put(stored.getId(), stored);
        }

        int spawnedRoulettes = spawnPendingRoulettes();
        int spawnedAutomates = spawnPendingAutomates();

        if (roulettes.isEmpty() && automates.isEmpty()) {
            plugin.getLogger().info("В data.yml нет сохранённых рулеток и автоматов.");
        } else {
            plugin.getLogger().info("Загружено рулеток: " + spawnedRoulettes + "/" + roulettes.size()
                    + ", автоматов: " + spawnedAutomates + "/" + automates.size());
        }
        scheduleRetries();
    }

    /**
     * Остановить повторы (используется при выключении плагина).
     */
    public void shutdown() {
        cancelRetries();
        pendingRoulettes.clear();
        pendingAutomates.clear();
        attempts.clear();
    }

    public boolean hasPending() {
        return !pendingRoulettes.isEmpty() || !pendingAutomates.isEmpty();
    }

    public int pendingCount() {
        return pendingRoulettes.size() + pendingAutomates.size();
    }

    /* ------------------------------------------------------------------ */
    /* Очередь                                                             */
    /* ------------------------------------------------------------------ */

    private int spawnPendingRoulettes() {
        int spawned = 0;
        Iterator<Map.Entry<String, DataManager.StoredRoulette>> iterator = pendingRoulettes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DataManager.StoredRoulette> entry = iterator.next();
            String id = entry.getKey();
            DataManager.StoredRoulette stored = entry.getValue();

            if (isWorldMissing(stored.getNpc()) || isWorldMissing(stored.getWheel())) {
                // мир ещё не загружен — подождём WorldLoadEvent или следующую попытку
                continue;
            }
            Location npc = DataManager.deserialize(stored.getNpc());
            Location wheel = DataManager.deserialize(stored.getWheel());
            if (npc == null || wheel == null) {
                plugin.getLogger().warning("Рулетка '" + id + "' пропущена: битые координаты в data.yml (npc='"
                        + stored.getNpc() + "', wheel='" + stored.getWheel() + "').");
                iterator.remove();
                continue;
            }
            try {
                if (plugin.getRouletteManager().spawn(id, npc, wheel) != null) {
                    iterator.remove();
                    attempts.remove(id);
                    spawned++;
                } else if (!registerFailure(id, "Рулетка")) {
                    // причину Roulette.spawn() уже написал в лог (например, NPC и колесо в разных мирах)
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
                plugin.getLogger().warning("Автомат '" + id + "' пропущен: битые координаты в data.yml (bottom='"
                        + stored.getBottom() + "').");
                iterator.remove();
                continue;
            }
            try {
                // Автомат регистрируется сразу, а голограмму он поднимет сам,
                // как только его чанк загрузится (см. AutomateManager.onChunkLoad).
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

    /**
     * Учитывает неудачную попытку.
     *
     * @return true, если запись стоит попробовать ещё раз
     */
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
        if (spawnedRoulettes > 0 || spawnedAutomates > 0) {
            plugin.getLogger().info("Догружено рулеток: " + spawnedRoulettes + ", автоматов: " + spawnedAutomates);
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
                    + worldOf(stored.getNpc(), stored.getWheel()) + "' не загружен. Запись осталась в data.yml,"
                    + " подниму её, как только мир загрузится.");
        }
        for (DataManager.StoredAutomate stored : pendingAutomates.values()) {
            plugin.getLogger().warning("Автомат '" + stored.getId() + "' не восстановлен: мир '"
                    + DataManager.worldName(stored.getBottom()) + "' не загружен. Запись осталась в data.yml,"
                    + " подниму её, как только мир загрузится.");
        }
    }

    private String worldOf(String first, String second) {
        String name = DataManager.worldName(first);
        return name != null ? name : String.valueOf(DataManager.worldName(second));
    }

    /* ------------------------------------------------------------------ */
    /* Миры                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Мир доехал (Multiverse и прочие загрузчики) — пробуем поднять то, что его ждало.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (!hasPending() || !plugin.isEnabled()) {
            return;
        }
        final String name = event.getWorld().getName();
        if (!waitsFor(name)) {
            return;
        }
        // не спавним прямо внутри события: даём миру и чанкам окончательно подняться
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            int spawnedRoulettes = spawnPendingRoulettes();
            int spawnedAutomates = spawnPendingAutomates();
            if (spawnedRoulettes > 0 || spawnedAutomates > 0) {
                plugin.getLogger().info("Мир '" + name + "' загружен — восстановлено рулеток: "
                        + spawnedRoulettes + ", автоматов: " + spawnedAutomates);
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
        return false;
    }

    /**
     * Мир выгрузили — снимаем сущности и возвращаем их в очередь,
     * чтобы при повторной загрузке мира всё встало на место.
     */
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
        scheduleRetries();
    }
}
