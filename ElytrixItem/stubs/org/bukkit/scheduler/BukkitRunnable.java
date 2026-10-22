package org.bukkit.scheduler;
public abstract class BukkitRunnable implements Runnable {
    public synchronized void cancel() throws IllegalStateException { }
    public BukkitTask runTask(org.bukkit.plugin.Plugin plugin) { return null; }
    public BukkitTask runTaskAsynchronously(org.bukkit.plugin.Plugin plugin) { return null; }
    public BukkitTask runTaskLater(org.bukkit.plugin.Plugin plugin, long delay) { return null; }
    public BukkitTask runTaskLaterAsynchronously(org.bukkit.plugin.Plugin plugin, long delay) { return null; }
    public BukkitTask runTaskTimer(org.bukkit.plugin.Plugin plugin, long delay, long period) { return null; }
    public BukkitTask runTaskTimerAsynchronously(org.bukkit.plugin.Plugin plugin, long delay, long period) { return null; }
    public int getTaskId() throws IllegalStateException { return -1; }
}
