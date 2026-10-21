package org.bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    public static PluginManager getPluginManager() { return null; }
    public static ConsoleCommandSender getConsoleSender() { return null; }
    public static BukkitScheduler getScheduler() { return null; }
    public static boolean dispatchCommand(CommandSender sender, String commandLine) { return false; }
}
