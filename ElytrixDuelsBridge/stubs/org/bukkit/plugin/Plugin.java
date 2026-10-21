package org.bukkit.plugin;
import java.util.logging.Logger;
public interface Plugin {
    java.io.File getDataFolder(); String getName(); Logger getLogger(); }
