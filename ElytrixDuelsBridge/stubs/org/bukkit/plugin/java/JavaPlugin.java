package org.bukkit.plugin.java;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
public abstract class JavaPlugin implements Plugin {
    public String getName() { return ""; }
    public boolean isEnabled() { return true; }
    public void onEnable() { }
    public void onDisable() { }
    public void saveDefaultConfig() { }
    public void saveConfig() { }
    public FileConfiguration getConfig() { return null; }
    public Logger getLogger() { return null; }
    public Server getServer() { return null; }
    public PluginManager getPluginManager() { return null; }
    public PluginCommand getCommand(String name) { return null; }
    public java.io.File getDataFolder() { return null; }
}
