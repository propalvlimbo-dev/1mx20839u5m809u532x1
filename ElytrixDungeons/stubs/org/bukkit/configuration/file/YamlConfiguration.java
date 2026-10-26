package org.bukkit.configuration.file;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }
    public static YamlConfiguration loadConfiguration(Reader reader) { return new YamlConfiguration(); }
    public static YamlConfiguration loadConfiguration(String contents) { return new YamlConfiguration(); }
    public static YamlConfiguration loadConfiguration() { return new YamlConfiguration(); }
}
