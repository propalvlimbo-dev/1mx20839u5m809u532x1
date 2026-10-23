package ru.rooyzee.elytrixcasino.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixcasino.Main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Хранение рулеток, автоматов и NPC Минёров в data.yml.
 */
public class DataManager {

    private final Main plugin;
    private final File file;
    private FileConfiguration config;

    public DataManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина!");
            }
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Не удалось создать data.yml!");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось создать data.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить data.yml", e);
        }
    }

    public void saveRoulette(String id, Location npc, Location wheel) {
        String path = "roulettes." + id;
        config.set(path + ".npc", serialize(npc));
        config.set(path + ".wheel", serialize(wheel));
        save();
    }

    public void removeRoulette(String id) {
        config.set("roulettes." + id, null);
        save();
    }

    public void saveAutomate(String id, Location bottom, long bet) {
        config.set("automates." + id + ".bottom", serialize(bottom));
        config.set("automates." + id + ".bet", bet);
        save();
    }

    public void removeAutomate(String id) {
        config.set("automates." + id, null);
        save();
    }

    public void saveMiner(String id, Location npc) {
        config.set("miners." + id + ".npc", serialize(npc));
        save();
    }

    public void removeMiner(String id) {
        config.set("miners." + id, null);
        save();
    }

    public List<StoredRoulette> readRoulettes() {
        ConfigurationSection section = config.getConfigurationSection("roulettes");
        if (section == null) {
            return Collections.emptyList();
        }
        List<StoredRoulette> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String npc = section.getString(key + ".npc", section.getString(key + ".villager"));
            String wheel = section.getString(key + ".wheel", section.getString(key + ".roulette"));
            result.add(new StoredRoulette(key, npc, wheel));
        }
        return result;
    }

    public List<StoredAutomate> readAutomates() {
        ConfigurationSection section = config.getConfigurationSection("automates");
        if (section == null) {
            return Collections.emptyList();
        }
        long defaultBet = plugin.getAutomateSettings() == null ? 1000L : plugin.getAutomateSettings().getBet();
        List<StoredAutomate> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            result.add(new StoredAutomate(key, section.getString(key + ".bottom"),
                    section.getLong(key + ".bet", defaultBet)));
        }
        return result;
    }

    public List<StoredMiner> readMiners() {
        ConfigurationSection section = config.getConfigurationSection("miners");
        if (section == null) {
            return Collections.emptyList();
        }
        List<StoredMiner> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            result.add(new StoredMiner(key, section.getString(key + ".npc")));
        }
        return result;
    }

    public static String serialize(Location location) {
        if (location == null) {
            return null;
        }
        World world = location.getWorld();
        return (world == null ? "world" : world.getName()) + ";"
                + location.getX() + ";"
                + location.getY() + ";"
                + location.getZ() + ";"
                + location.getYaw() + ";"
                + location.getPitch();
    }

    public static Location deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0.0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0.0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String worldName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        int index = raw.indexOf(';');
        String name = index < 0 ? raw : raw.substring(0, index);
        return name.isEmpty() ? null : name;
    }

    public void savePending(UUID playerId, long win, long bet, String combo) {
        String path = "pending." + playerId;
        config.set(path + ".win", win);
        config.set(path + ".bet", bet);
        config.set(path + ".combo", combo == null ? "" : combo);
        save();
    }

    public Pending takePending(UUID playerId) {
        String path = "pending." + playerId;
        if (!config.contains(path)) {
            return null;
        }
        Pending pending = new Pending(
                config.getLong(path + ".win", 0L),
                config.getLong(path + ".bet", 0L),
                config.getString(path + ".combo", ""));
        config.set(path, null);
        save();
        return pending;
    }

    public static final class Pending {
        public final long win;
        public final long bet;
        public final String combo;

        public Pending(long win, long bet, String combo) {
            this.win = win;
            this.bet = bet;
            this.combo = combo;
        }
    }

    public static final class StoredRoulette {
        private final String id;
        private final String npc;
        private final String wheel;

        public StoredRoulette(String id, String npc, String wheel) {
            this.id = id;
            this.npc = npc;
            this.wheel = wheel;
        }

        public String getId() {
            return id;
        }

        public String getNpc() {
            return npc;
        }

        public String getWheel() {
            return wheel;
        }
    }

    public static final class StoredAutomate {
        private final String id;
        private final String bottom;
        private final long bet;

        public StoredAutomate(String id, String bottom, long bet) {
            this.id = id;
            this.bottom = bottom;
            this.bet = bet;
        }

        public String getId() {
            return id;
        }

        public String getBottom() {
            return bottom;
        }

        public long getBet() {
            return bet;
        }
    }

    public static final class StoredMiner {
        private final String id;
        private final String npc;

        public StoredMiner(String id, String npc) {
            this.id = id;
            this.npc = npc;
        }

        public String getId() {
            return id;
        }

        public String getNpc() {
            return npc;
        }
    }

    public String newId() {
        String id;
        do {
            id = UUID.randomUUID().toString().substring(0, 8);
        } while (config.contains("roulettes." + id)
                || config.contains("automates." + id)
                || config.contains("miners." + id));
        return id;
    }
}
