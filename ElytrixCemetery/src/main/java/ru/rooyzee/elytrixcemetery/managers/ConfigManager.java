package ru.rooyzee.elytrixcemetery.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        migrateLegacyGuardianKeySettings();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reload() {
        loadAll();
    }

    private void migrateLegacyGuardianKeySettings() {
        boolean changed = false;
        if (!config.isSet("guardian-key-weights")) {
            org.bukkit.configuration.ConfigurationSection legacy = config.getConfigurationSection("ghosts.key-weights");
            if (legacy != null) {
                config.set("guardian-key-weights", legacy.getValues(false));
                changed = true;
            }
        }
        if (config.isSet("ghosts.key-drop-chance")) {
            config.set("ghosts.key-drop-chance", null);
            changed = true;
        }
        if (config.isSet("ghosts.key-weights")) {
            config.set("ghosts.key-weights", null);
            changed = true;
        }
        if (changed) plugin.saveConfig();
    }

    public Location getCenterLocation() {
        String worldName = config.getString("center.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        int x = config.getInt("center.x", 0);
        int y = config.getInt("center.y", 70);
        int z = config.getInt("center.z", 0);
        return world != null ? new Location(world, x, y, z) : null;
    }

    public int getBoundaryRadius() {
        return Math.max(0, config.getInt("boundary-radius", 50));
    }

    public int getRefillIntervalSeconds() {
        return Math.max(1, config.getInt("refill-interval-seconds", 1800)); // 30 минут
    }

    public int getGhostCount() {
        return Math.max(0, Math.min(64, config.getInt("ghosts.count", 8)));
    }

    public double getGhostHealth() {
        double health = config.getDouble("ghosts.health", 16.0);
        return Double.isFinite(health) ? Math.max(1.0, Math.min(2048.0, health)) : 16.0;
    }

    public int getGhostRespawnCooldownSeconds() {
        return Math.max(1, Math.min(86400, config.getInt("ghosts.respawn-cooldown-seconds", 60)));
    }

    public boolean isGraveHologramsEnabled() {
        return config.getBoolean("grave-holograms-enabled", true);
    }

    public boolean isMainHologramEnabled() {
        return config.getBoolean("main-hologram-enabled", false);
    }

    public double getMainHologramOffsetY() {
        return config.getDouble("main-hologram-offset-y", 4.5);
    }

    public int getHologramRefreshIntervalSeconds() {
        return Math.max(1, config.getInt("hologram-refresh-interval-seconds", 5));
    }

    public double getGuardianSpawnChance() {
        double chance = config.getDouble("wither-skeleton-chance", 25.0);
        return Double.isFinite(chance) ? Math.max(0.0, Math.min(100.0, chance)) : 25.0;
    }

    public double getGuardianKeyDropChance() {
        double chance = config.getDouble("guardian-key-drop-chance", 50.0);
        return Double.isFinite(chance) ? Math.max(0.0, Math.min(100.0, chance)) : 50.0;
    }

    public String getAdminPrefix() {
        return messages.getString("prefix.admin", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» ");
    }

    public String getPlayerPrefix() {
        return messages.getString("prefix.player", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» ");
    }

    public List<String> getMessageList(String path) {
        List<String> list = messages.getStringList(path);
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(ColorUtil.colorize(s));
        }
        return colored;
    }
}
