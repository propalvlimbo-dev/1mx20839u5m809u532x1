package org.bukkit.plugin;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
public interface Plugin {
    File getDataFolder();
    PluginDescriptionFile getDescription();
    FileConfiguration getConfig();
    void saveResource(String resourcePath, boolean replace);
    void saveDefaultConfig();
    void saveConfig();
    void reloadConfig();
    boolean isEnabled();
    Logger getLogger();
    String getName();
}
