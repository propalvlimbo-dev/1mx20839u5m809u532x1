package ru.rooyzee.elytrixsubscribe.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.database.Storage;
import ru.rooyzee.elytrixsubscribe.events.PlayerGoneSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.PlayerReceivedSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.state.ReceivedSubscribeState;

/**
 * Срок подписки: кэш в памяти + запись в data.yml.
 *
 * Проверка «истекла ли» живёт в SubManager — здесь только данные, чтобы
 * плейсхолдеры не дёргали базу на каждый рендер.
 */
public class SubService implements ISubService {
    private final Main plugin;
    private final Storage storage;
    private final ConcurrentHashMap<UUID, Long> cache = new ConcurrentHashMap<UUID, Long>();

    public SubService(Main plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorage();
    }

    public void loadToCache(UUID uuid) {
        String path = "subs." + uuid;
        if (!this.storage.has(path)) return;
        long time = this.storage.getLong(path, 0L);
        if (time > 0L) this.cache.put(uuid, Long.valueOf(time));
    }

    public void removeFromCache(UUID uuid) {
        this.cache.remove(uuid);
    }

    @Override
    public void remSub(Player player) {
        UUID uuid = player.getUniqueId();
        this.cache.remove(uuid);
        this.storage.remove("subs." + uuid);
        this.storage.saveAsync();
        Bukkit.getScheduler().runTask(this.plugin, () ->
                Bukkit.getPluginManager().callEvent(new PlayerGoneSubscribeEvent(player)));
    }

    /** Забрать подписку у игрока, которого нет в сети (истекла к моменту входа). */
    public void removeOffline(UUID uuid) {
        this.cache.remove(uuid);
        this.storage.remove("subs." + uuid);
        this.storage.saveAsync();
    }

    @Override
    public void insertSub(Player player, long time, Consumer<Boolean> callback) {
        UUID uuid = player.getUniqueId();
        this.cache.put(uuid, Long.valueOf(time));
        this.storage.setLong("subs." + uuid, time);
        this.storage.saveAsync();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Bukkit.getPluginManager().callEvent(
                    new PlayerReceivedSubscribeEvent(player, time, ReceivedSubscribeState.NEW));
            callback.accept(Boolean.TRUE);
        });
    }

    @Override
    public void updatePlayer(Player player, long time, Consumer<Boolean> callback) {
        UUID uuid = player.getUniqueId();
        this.cache.put(uuid, Long.valueOf(time));
        this.storage.setLong("subs." + uuid, time);
        this.storage.saveAsync();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Bukkit.getPluginManager().callEvent(
                    new PlayerReceivedSubscribeEvent(player, time, ReceivedSubscribeState.UPDATE));
            callback.accept(Boolean.TRUE);
        });
    }

    @Override
    public void setSub(Player player, long time) {
        if (this.hasSub(player.getUniqueId())) {
            this.updatePlayer(player, time, success -> { });
        } else {
            this.insertSub(player, time, success -> { });
        }
    }

    @Override
    public long getTimeToEnd(UUID uuid) {
        Long value = this.cache.get(uuid);
        return value == null ? 0L : value.longValue();
    }

    @Override
    public boolean hasSub(UUID uuid) {
        return this.cache.containsKey(uuid);
    }

}
