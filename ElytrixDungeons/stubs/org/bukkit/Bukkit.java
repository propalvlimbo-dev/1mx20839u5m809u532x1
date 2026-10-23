package org.bukkit;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitScheduler;
public final class Bukkit {
    public static org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material) { return null; }
    public static org.bukkit.block.data.BlockData createBlockData(String data) { return null; }
    public static BossBar createBossBar(String title, BarColor color, BarStyle style, BarFlag... flags) { return null; }
    public static PluginManager getPluginManager() { return null; }
    public static ConsoleCommandSender getConsoleSender() { return null; }
    public static BukkitScheduler getScheduler() { return null; }
    public static org.bukkit.profile.PlayerProfile createPlayerProfile(java.util.UUID uniqueId, String name) { return null; }
    public static boolean dispatchCommand(CommandSender sender, String commandLine) { return false; }
    public static Inventory createInventory(InventoryHolder owner, int size, String title) { return null; }
    public static Collection<? extends Player> getOnlinePlayers() { return java.util.Collections.emptyList(); }
    public static Player getPlayerExact(String name) { return null; }
    public static World getWorld(String name) { return null; }
    public static java.io.File getWorldContainer() { return new java.io.File("."); }
    public static java.util.List<World> getWorlds() { return java.util.Collections.emptyList(); }
    public static Player getPlayer(UUID id) { return null; }
    public static OfflinePlayer getOfflinePlayer(UUID id) { return null; }
    public static OfflinePlayer getOfflinePlayer(String name) { return null; }
    public static String getBukkitVersion() { return ""; }
    public static org.bukkit.scoreboard.ScoreboardManager getScoreboardManager() { return null; }
}
