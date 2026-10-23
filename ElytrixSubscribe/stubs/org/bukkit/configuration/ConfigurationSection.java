package org.bukkit.configuration;
import java.util.List;
public interface ConfigurationSection {
    String getString(String path);
    String getString(String path, String def);
    int getInt(String path, int def);
    long getLong(String path, long def);
    boolean getBoolean(String path, boolean def);
    List<String> getStringList(String path);
    Object get(String path);
    void set(String path, Object value);
    boolean isSet(String path);
    boolean isConfigurationSection(String path);
    ConfigurationSection getConfigurationSection(String path);
    ConfigurationSection createSection(String path);
}
