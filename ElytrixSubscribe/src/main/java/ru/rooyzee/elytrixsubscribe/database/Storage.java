package ru.rooyzee.elytrixsubscribe.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixsubscribe.Main;

/**
 * Хранилище данных плагина в data.yml.
 *
 * Почему не SQLite: драйвер sqlite-jdbc пришлось бы класть в jar, а сервер
 * его не отдаёт — старый плагин из-за этого падал в консоли и настройки не
 * сохранялись. Данных тут мало (срок подписки, включённые функции, три
 * питомца), поэтому YAML надёжнее и не тянет зависимостей.
 *
 * Все чтения/изменения идут через synchronized, запись на диск — асинхронно,
 * чтобы не лагать на клик в меню.
 */
public class Storage {
    private final Main plugin;
    private final File file;
    private YamlConfiguration data;
    /** Последний снимок, который ещё не записан; null — писать нечего. */
    private volatile String pending;
    private final java.util.concurrent.atomic.AtomicBoolean writing =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public Storage(Main plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.load();
    }

    private synchronized void load() {
        this.data = YamlConfiguration.loadConfiguration(this.file);
    }

    /**
     * Сохранить на диск асинхронно. В памяти всегда лежит только самый свежий
     * снимок: если писать каждый клик отдельной задачей, старая запись может
     * завершиться позже новой и откатить данные.
     */
    public void saveAsync() {
        synchronized (this) {
            this.pending = this.data.saveToString();
        }
        if (!this.writing.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::writePending);
    }

    private void writePending() {
        try {
            while (true) {
                String snapshot;
                synchronized (this) {
                    snapshot = this.pending;
                    this.pending = null;
                }
                if (snapshot == null) break;
                java.nio.file.Files.write(this.file.toPath(),
                        snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException error) {
            this.plugin.getLogger().warning("Не удалось сохранить data.yml: " + error);
        } finally {
            this.writing.set(false);
        }
    }

    /** Сохранить сразу, в текущем потоке (onDisable — асинхронные задачи уже не примут). */
    public synchronized void saveNow() {
        try {
            this.data.save(this.file);
        } catch (IOException error) {
            this.plugin.getLogger().warning("Не удалось сохранить data.yml: " + error);
        }
    }

    public synchronized boolean has(String path) {
        return this.data.isSet(path);
    }

    public synchronized String getString(String path, String fallback) {
        String value = this.data.getString(path);
        return value == null ? fallback : value;
    }

    public synchronized long getLong(String path, long fallback) {
        return this.data.getLong(path, fallback);
    }

    public synchronized int getInt(String path, int fallback) {
        return this.data.getInt(path, fallback);
    }

    public synchronized boolean getBoolean(String path, boolean fallback) {
        return this.data.getBoolean(path, fallback);
    }

    public synchronized void set(String path, Object value) {
        this.data.set(path, value);
    }

    public synchronized void remove(String path) {
        this.data.set(path, null);
    }

    public synchronized ConfigurationSection section(String path) {
        return this.data.getConfigurationSection(path);
    }

    public synchronized void createSection(String path) {
        if (this.data.getConfigurationSection(path) != null) return;
        this.data.createSection(path);
    }

    public synchronized List<String> getStringList(String path) {
        List<String> list = this.data.getStringList(path);
        return list == null ? new ArrayList<String>() : new ArrayList<String>(list);
    }

    public synchronized void setStringList(String path, List<String> values) {
        this.data.set(path, new ArrayList<String>(values));
    }

    public synchronized void setLong(String path, long value) {
        this.data.set(path, Long.valueOf(value));
    }
}
