package org.bukkit.plugin;
public class RegisteredServiceProvider<T> {
    public T getProvider() { return null; }
    public Class<T> getService() { return null; }
    public Plugin getPlugin() { return null; }
    public int getPriority() { return 0; }
}
