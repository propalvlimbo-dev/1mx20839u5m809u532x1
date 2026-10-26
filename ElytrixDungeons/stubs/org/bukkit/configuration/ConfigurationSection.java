package org.bukkit.configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.ItemStack;

public interface ConfigurationSection {
    String getString(String path);
    String getString(String path, String def);
    int getInt(String path);
    int getInt(String path, int def);
    long getLong(String path);
    long getLong(String path, long def);
    double getDouble(String path);
    double getDouble(String path, double def);
    boolean getBoolean(String path);
    boolean getBoolean(String path, boolean def);
    List<String> getStringList(String path);
    List<Integer> getIntegerList(String path);
    List<Double> getDoubleList(String path);
    List<Map<?, ?>> getMapList(String path);
    default ItemStack getItemStack(String path) { return null; }
    Object get(String path);
    Object get(String path, Object def);
    void set(String path, Object value);
    boolean isSet(String path);
    boolean isConfigurationSection(String path);
    boolean isList(String path);
    boolean contains(String path);
    ConfigurationSection getConfigurationSection(String path);
    ConfigurationSection createSection(String path);
    Set<String> getKeys(boolean deep);
    Map<String, Object> getValues(boolean deep);
    void addDefault(String path, Object value);
}
