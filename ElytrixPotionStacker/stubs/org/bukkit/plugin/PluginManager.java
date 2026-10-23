package org.bukkit.plugin;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
public interface PluginManager {
    Plugin getPlugin(String name);
    boolean isPluginEnabled(String name);
    void registerEvents(Listener listener, Plugin plugin);
    void callEvent(Event event);
}
