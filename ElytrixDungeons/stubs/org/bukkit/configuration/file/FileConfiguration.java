package org.bukkit.configuration.file;

import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;

public class FileConfiguration implements org.bukkit.configuration.Configuration {
    public String getString(String path) { return null; }
    public String getString(String path, String def) { return def; }
    public int getInt(String path) { return 0; }
    public int getInt(String path, int def) { return def; }
    public long getLong(String path) { return 0L; }
    public long getLong(String path, long def) { return def; }
    public double getDouble(String path) { return 0D; }
    public double getDouble(String path, double def) { return def; }
    public boolean getBoolean(String path) { return false; }
    public boolean getBoolean(String path, boolean def) { return def; }
    public java.util.List<String> getStringList(String path) { return java.util.Collections.emptyList(); }
    public java.util.List<Integer> getIntegerList(String path) { return java.util.Collections.emptyList(); }
    public java.util.List<java.util.Map<?, ?>> getMapList(String path) { return java.util.Collections.emptyList(); }
    public Object get(String path) { return null; }
    public Object get(String path, Object def) { return def; }
    public void set(String path, Object value) { }
    public boolean isSet(String path) { return false; }
    public boolean isConfigurationSection(String path) { return false; }
    public boolean contains(String path) { return false; }
    public ConfigurationSection getConfigurationSection(String path) { return null; }
    public ConfigurationSection createSection(String path) { return null; }
    public java.util.Set<String> getKeys(boolean deep) { return java.util.Collections.emptySet(); }
    public java.util.Map<String, Object> getValues(boolean deep) { return java.util.Collections.emptyMap(); }
    public void addDefault(String path, Object value) { }
    public void setDefaults(org.bukkit.configuration.Configuration defaults) { }
    public org.bukkit.configuration.Configuration getDefaults() { return null; }
    public void addDefaults(org.bukkit.configuration.Configuration defaults) { }
    public void save(File file) throws IOException { }
    public void saveAs(File file) throws IOException { }
    public String saveToString() { return ""; }
    public void load(File file) throws IOException { }
    public void loadFromString(String contents) { }
    public boolean isSet() { return false; }
    public java.util.List<Double> getDoubleList(String path) { return java.util.Collections.emptyList(); }
    public boolean isList(String path) { return false; }
    public FileConfigurationOptions options() { return null; }
}
