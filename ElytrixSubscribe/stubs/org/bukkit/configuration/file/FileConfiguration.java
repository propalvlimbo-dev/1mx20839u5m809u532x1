package org.bukkit.configuration.file;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
public class FileConfiguration {
    public String getString(String path) { return null; }
    public String getString(String path, String def) { return def; }
    public boolean getBoolean(String path) { return false; }
    public boolean getBoolean(String path, boolean def) { return def; }
    public int getInt(String path, int def) { return def; }
    public double getDouble(String path, double def) { return def; }
    public long getLong(String path, long def) { return def; }
    public List<String> getStringList(String path) { return null; }
    public boolean isString(String path) { return false; }
    public void set(String path, Object value) { }
    public boolean isSet(String path) { return false; }
    public boolean isConfigurationSection(String path) { return false; }
    public ConfigurationSection getConfigurationSection(String path) { return null; }
    public ConfigurationSection createSection(String path) { return null; }
    public Object get(String path) { return null; }
    public java.util.Set<String> getKeys(boolean deep) { return java.util.Collections.emptySet(); }
    public String saveToString() { return ""; }
    public void save(java.io.File file) throws java.io.IOException { }
}
