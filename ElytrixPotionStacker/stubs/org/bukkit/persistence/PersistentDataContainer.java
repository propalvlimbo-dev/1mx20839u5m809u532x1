package org.bukkit.persistence;
import org.bukkit.NamespacedKey;
public interface PersistentDataContainer {
    <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value);
    <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type);
    <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type);
    boolean has(NamespacedKey key);
    void remove(NamespacedKey key);
    java.util.Set<NamespacedKey> getKeys();
}
