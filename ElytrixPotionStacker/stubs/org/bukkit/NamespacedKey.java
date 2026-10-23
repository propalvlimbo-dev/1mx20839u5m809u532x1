package org.bukkit;
public class NamespacedKey {
    public NamespacedKey(org.bukkit.plugin.Plugin plugin, String key) { }
    public NamespacedKey(String namespace, String key) { }
    public String getKey() { return ""; }
    public String getNamespace() { return ""; }
    public static NamespacedKey minecraft(String key) { return new NamespacedKey("minecraft", key); }
    @Override public boolean equals(Object other) { return other instanceof NamespacedKey; }
    @Override public int hashCode() { return 0; }
}
