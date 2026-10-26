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
    private String pending;
    /** Эти поля защищены монитором Storage.this. */
    private boolean writing;
    private boolean closed;
    /** Не даёт отложенной записи затереть финальный снимок из saveNow(). */
    private final Object fileLock = new Object();

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
            if (this.closed) return;
            this.pending = this.data.saveToString();
            if (this.writing) return;

            this.writing = true;
            try {
                // Планируем задачу под тем же монитором: при ошибке запуска другой
                // saveAsync не сможет оставить снимок между writing=true/false.
                Bukkit.getScheduler().runTaskAsynchronously(this.plugin, this::writePending);
            } catch (RuntimeException error) {
                this.writing = false;
                this.plugin.getLogger().warning("Не удалось запустить сохранение data.yml: " + error);
            }
        }
    }

    private void writePending() {
        while (true) {
            String snapshot;
            synchronized (this) {
                if (this.closed) {
                    this.pending = null;
                    this.writing = false;
                    return;
                }
                snapshot = this.pending;
                if (snapshot == null) {
                    // Сброс writing выполняется в том же мониторе, что и saveAsync,
                    // поэтому новый снимок не потеряется на границе завершения потока.
                    this.writing = false;
                    return;
                }
                this.pending = null;
            }

            try {
                synchronized (this.fileLock) {
                    synchronized (this) {
                        if (this.closed) {
                            this.pending = null;
                            this.writing = false;
                            return;
                        }
                    }
                    java.nio.file.Files.write(this.file.toPath(),
                            snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException error) {
                boolean retryNewerSnapshot;
                synchronized (this) {
                    if (this.closed) {
                        this.pending = null;
                        this.writing = false;
                        return;
                    }
                    if (this.pending == null) this.pending = snapshot;
                    retryNewerSnapshot = !this.pending.equals(snapshot);
                    if (!retryNewerSnapshot) this.writing = false;
                }
                this.plugin.getLogger().warning("Не удалось сохранить data.yml: " + error);
                if (!retryNewerSnapshot) return;
            }
        }
    }

    /** Сохранить финальный снимок и запретить отложенным задачам его перезаписать. */
    public void saveNow() {
        String snapshot;
        synchronized (this) {
            this.closed = true;
            this.pending = null;
            snapshot = this.data.saveToString();
        }
        synchronized (this.fileLock) {
            try {
                java.nio.file.Files.write(this.file.toPath(),
                        snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException error) {
                this.plugin.getLogger().warning("Не удалось сохранить data.yml: " + error);
            }
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
