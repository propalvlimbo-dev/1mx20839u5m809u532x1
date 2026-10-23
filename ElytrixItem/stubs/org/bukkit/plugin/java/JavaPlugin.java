package org.bukkit.plugin.java;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public abstract class JavaPlugin implements Plugin {
    public String getName() { return ""; }
    public boolean isEnabled() { return true; }
    public void setEnabled(boolean enabled) { }
    public void onEnable() { }
    public void onDisable() { }
    public Logger getLogger() { return null; }
    public Server getServer() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public InputStream getResource(String filename) { return null; }
    public void saveResource(String resourcePath, boolean replace) { }
    public File getDataFolder() { return new File("."); }
    public FileConfiguration getConfig() { return null; }
    public void saveDefaultConfig() { }
    public void reloadConfig() { }
    public void saveConfig() { }
    public File getFile() { return new File("."); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return null; }
}
