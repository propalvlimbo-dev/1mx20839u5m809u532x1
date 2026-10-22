package org.bukkit.plugin.java;
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
    public void saveDefaultConfig() { }
    public void saveConfig() { }
    public void reloadConfig() { }
    public InputStream getResource(String path) { return null; }
    public FileConfiguration getConfig() { return null; }
    public Logger getLogger() { return null; }
    public Server getServer() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public java.io.File getDataFolder() { return null; }
}
