package ru.rooyzee.elytrixsubscribe.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.database.SQLite;
import ru.rooyzee.elytrixsubscribe.events.PlayerGoneSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.PlayerReceivedSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.state.ReceivedSubscribeState;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SubService implements ISubService {
    private final Main plugin;
    private final SQLite sqlite;
    private final ConcurrentHashMap<UUID, Long> cachedSubs = new ConcurrentHashMap<>();

    public SubService(Main plugin) {
        this.plugin = plugin;
        this.sqlite = plugin.getSQLite();
    }

    public ConcurrentHashMap<UUID, Long> getCachedSubs() {
        return this.cachedSubs;
    }

    public void loadToCache(UUID uuid) {
        this.sqlite.query("SELECT time FROM subscribe_users WHERE player=?", rs -> {
            if (rs.next()) {
                this.cachedSubs.put(uuid, rs.getLong("time"));
            }
        }, uuid.toString());
    }

    public void removeFromCache(UUID uuid) {
        this.cachedSubs.remove(uuid);
    }

    public void remSub(Player player) {
        UUID uuid = player.getUniqueId();
        this.cachedSubs.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () ->
                this.sqlite.update("DELETE FROM subscribe_users WHERE player=?", uuid.toString())
        );
        Bukkit.getScheduler().runTask(this.plugin, () ->
                Bukkit.getPluginManager().callEvent(new PlayerGoneSubscribeEvent(player))
        );
    }

    public void insertSub(Player player, long time, Consumer<Boolean> callback) {
        UUID uuid = player.getUniqueId();
        if (this.hasSub(uuid)) {
            Bukkit.getPluginManager().callEvent(new PlayerReceivedSubscribeEvent(player, time, ReceivedSubscribeState.CANCEL));
            callback.accept(false);
            return;
        }
        this.cachedSubs.put(uuid, time);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.sqlite.update("INSERT INTO subscribe_users (player, time) VALUES(?,?)", uuid.toString(), time);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                Bukkit.getPluginManager().callEvent(new PlayerReceivedSubscribeEvent(player, time, ReceivedSubscribeState.NEW));
                callback.accept(true);
            });
        });
    }

    public void updatePlayer(Player player, long time, Consumer<Boolean> callback) {
        UUID uuid = player.getUniqueId();
        if (!this.hasSub(uuid)) {
            Bukkit.getPluginManager().callEvent(new PlayerReceivedSubscribeEvent(player, time, ReceivedSubscribeState.CANCEL));
            callback.accept(false);
            return;
        }
        this.cachedSubs.put(uuid, time);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.sqlite.update("UPDATE subscribe_users SET time=? WHERE player=?", time, uuid.toString());
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                Bukkit.getPluginManager().callEvent(new PlayerReceivedSubscribeEvent(player, time, ReceivedSubscribeState.UPDATE));
                callback.accept(true);
            });
        });
    }

    public void setSub(Player player, long time) {
        UUID uuid = player.getUniqueId();
        if (!this.hasSub(uuid)) {
            this.insertSub(player, time, success -> {});
        } else {
            this.updatePlayer(player, time, success -> {});
        }
    }

    public long getTimeToEnd(UUID uuid) {
        return this.cachedSubs.getOrDefault(uuid, 0L);
    }

    public boolean hasSub(UUID uuid) {
        return this.cachedSubs.containsKey(uuid);
    }

    public void createTable() {
        this.sqlite.update("CREATE TABLE IF NOT EXISTS subscribe_users (player TEXT PRIMARY KEY, time INTEGER)");
    }
}