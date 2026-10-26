package org.bukkit.scheduler;
public interface BukkitTask {
    int getTaskId();
    boolean isSync();
    boolean isCancelled();
    void cancel();
}
