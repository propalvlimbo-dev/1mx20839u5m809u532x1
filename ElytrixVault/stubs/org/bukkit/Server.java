package org.bukkit;
import java.util.Collection;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
public interface Server {
    org.bukkit.plugin.ServicesManager getServicesManager();
    org.bukkit.scheduler.BukkitScheduler getScheduler();
    org.bukkit.plugin.PluginManager getPluginManager();
    java.util.logging.Logger getLogger();
    java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers();
    org.bukkit.entity.Player getPlayer(String name);
    org.bukkit.entity.Player getPlayerExact(String name);
    void broadcastMessage(String message);
    org.bukkit.World getWorld(String name);
    java.util.List<org.bukkit.World> getWorlds();
    String getVersion();
    String getName();
    String getBukkitVersion();
    int getMaxPlayers();
    java.io.File getUpdateFolder();
    org.bukkit.command.PluginCommand getPluginCommand(String name);
    void dispatchCommand(org.bukkit.command.CommandSender sender, String commandLine);

}
