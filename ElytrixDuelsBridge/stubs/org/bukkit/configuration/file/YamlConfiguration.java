package org.bukkit.configuration.file;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(File file) { return new YamlConfiguration(); }

    public void save(File file) throws IOException { }

    public Set<String> getKeys(boolean deep) { return null; }
}
