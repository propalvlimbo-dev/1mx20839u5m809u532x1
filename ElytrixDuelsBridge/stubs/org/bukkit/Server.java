package org.bukkit;
import java.util.Collection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
public interface Server {
    PluginManager getPluginManager();
    Collection<? extends Player> getOnlinePlayers();
}
