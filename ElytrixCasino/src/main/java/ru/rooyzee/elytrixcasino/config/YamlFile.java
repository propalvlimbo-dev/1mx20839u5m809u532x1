package ru.rooyzee.elytrixcasino.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Отдельный yml-файл плагина: создаётся из ресурса при первом запуске,
 * недостающие ключи подтягиваются из версии в jar.
 */
public class YamlFile {

    private final JavaPlugin plugin;
    private final String resourcePath;
    private final File file;
    private FileConfiguration config;

    public YamlFile(JavaPlugin plugin, String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку " + parent.getPath());
            }
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("В jar нет файла " + resourcePath + ", создаю пустой.");
                try {
                    if (!file.createNewFile()) {
                        plugin.getLogger().warning("Не удалось создать " + resourcePath);
                    }
                } catch (IOException io) {
                    plugin.getLogger().log(Level.SEVERE, "Не удалось создать " + resourcePath, io);
                }
            }
        }
        reload();
    }

    public final void reload() {
        config = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource(resourcePath);
        if (stream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
            config.options().copyDefaults(false);
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить " + resourcePath, e);
        }
    }

    public FileConfiguration get() {
        return config;
    }

    public String getName() {
        return resourcePath;
    }
}
