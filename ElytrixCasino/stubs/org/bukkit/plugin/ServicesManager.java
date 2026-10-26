package org.bukkit.plugin;
public interface ServicesManager {
    <T> RegisteredServiceProvider<T> getRegistration(Class<T> service);
    <T> void register(Class<T> service, T provider, Plugin plugin);
}
