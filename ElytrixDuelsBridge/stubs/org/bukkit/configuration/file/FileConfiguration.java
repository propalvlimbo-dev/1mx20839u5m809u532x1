package org.bukkit.configuration.file;
import java.util.List;
public class FileConfiguration {
    public String getString(String path) { return null; }
    public String getString(String path, String def) { return def; }
    public boolean getBoolean(String path) { return false; }
    public boolean getBoolean(String path, boolean def) { return def; }
    public int getInt(String path, int def) { return def; }
    public double getDouble(String path, double def) { return def; }
    public List<String> getStringList(String path) { return null; }
    public boolean isString(String path) { return false; }
    public void set(String path, Object value) { }
}
