package ru.rooyzee.elytrixtrader.expansion;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Numbers;

import java.util.List;
import java.util.Locale;

public class TraderExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final String identifier;

    public TraderExpansion(Main plugin) {
        this(plugin, "elytrixtrader");
    }

    public TraderExpansion(Main plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String getIdentifier() { return identifier; }

    @Override
    public String getAuthor() { return "rooyzee"; }

    @Override
    public String getVersion() { return "1.1.0"; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return handle(params);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return handle(params);
    }

    private String handle(String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT).replace('-', '_');
        String field = key;
        String traderId = null;

        // Supports both the legacy global placeholders and trader-specific forms such as
        // %elytrixtrader_ghost_location% / %elytrixtrader_experienced_x%.
        int longestPrefix = -1;
        for (TraderConfig config : plugin.traderConfigs().values()) {
            if (config == null || config.id() == null) continue;
            String id = config.id().toLowerCase(Locale.ROOT);
            if (key.equals(id)) {
                traderId = id;
                field = "formatted";
                longestPrefix = id.length();
            } else if (key.startsWith(id + "_") && id.length() > longestPrefix) {
                traderId = id;
                field = key.substring(id.length() + 1);
                longestPrefix = id.length();
            }
        }

        List<TraderInstance> instances = plugin.traders().instances();
        TraderInstance active = activeInstance(instances, traderId);

        if (field.isEmpty() || field.equals("formatted") || field.equals("tracker")) {
            if (active != null && active.location() != null) {
                return ColorUtil.translate("&aАктивен &8(&f" + locationText(active.location()) + "&8)");
            }
            TraderConfig config = traderId == null ? null : config(traderId);
            if (config != null && !config.enabled()) return ColorUtil.translate("&cВыключен");
            if (!plugin.config().scheduleEnabled() || plugin.config().scheduleTimes().isEmpty()) {
                return ColorUtil.translate("&cВыключен");
            }
            return ColorUtil.translate("&#F8BEFB" + plugin.traders().nextEventTime());
        }

        switch (field) {
            case "status":
                if (active != null) return ColorUtil.translate("&aАктивен");
                TraderConfig statusConfig = traderId == null ? null : config(traderId);
                if (statusConfig != null && !statusConfig.enabled()) return ColorUtil.translate("&cВыключен");
                return ColorUtil.translate("&cНе активен");

            case "time":
            case "timer":
            case "next":
            case "next_time":
                if (active != null) {
                    return ColorUtil.translate("&aИдёт сейчас &8| &fдо исчезновения: "
                            + Numbers.duration(active.remainingSeconds()));
                }
                TraderConfig timeConfig = traderId == null ? null : config(traderId);
                if (timeConfig != null && !timeConfig.enabled()) return ColorUtil.translate("&cВыключен");
                return ColorUtil.translate("&#F8BEFB" + plugin.traders().nextEventTime());

            case "location":
            case "coords":
            case "coordinates":
                return locationPlaceholder(traderId, active, false);

            case "location_full":
                return locationPlaceholder(traderId, active, true);

            case "x":
                return coordinate(traderId, active, 0);
            case "y":
                return coordinate(traderId, active, 1);
            case "z":
                return coordinate(traderId, active, 2);

            case "world":
                if (active != null && active.location() != null && active.location().getWorld() != null) {
                    return active.location().getWorld().getName();
                }
                TraderConfig worldConfig = traderId == null ? null : config(traderId);
                if (worldConfig != null && worldConfig.hasFixedSpawnLocation()) return worldConfig.fixedSpawnWorld();
                return "world";

            case "count":
            case "total":
                return String.valueOf(traderId == null
                        ? plugin.traders().total() : plugin.traders().count(traderId));

            case "name":
            case "trader_name":
                if (active != null && active.config() != null) {
                    return ColorUtil.translate(active.config().displayName());
                }
                TraderConfig nameConfig = traderId == null ? null : config(traderId);
                if (nameConfig != null) return ColorUtil.translate(nameConfig.displayName());
                return ColorUtil.translate("&aТорговец опытом");

            default:
                return null;
        }
    }

    private TraderInstance activeInstance(List<TraderInstance> instances, String traderId) {
        if (instances == null) return null;
        for (TraderInstance instance : instances) {
            if (instance == null || !instance.alive() || instance.config() == null) continue;
            if (traderId == null || instance.config().id().equalsIgnoreCase(traderId)) return instance;
        }
        return null;
    }

    private TraderConfig config(String traderId) {
        if (traderId == null) return null;
        for (TraderConfig config : plugin.traderConfigs().values()) {
            if (config != null && config.id() != null && config.id().equalsIgnoreCase(traderId)) return config;
        }
        return null;
    }

    private String locationPlaceholder(String traderId, TraderInstance active, boolean full) {
        if (active != null && active.location() != null) {
            Location location = active.location();
            String coords = locationText(location);
            String world = location.getWorld() == null ? "world" : location.getWorld().getName();
            return full ? world + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ()
                    : coords;
        }
        TraderConfig config = traderId == null ? null : config(traderId);
        if (config != null && config.hasFixedSpawnLocation()) {
            int x = (int) Math.floor(config.fixedSpawnX());
            int y = (int) Math.floor(config.fixedSpawnY());
            int z = (int) Math.floor(config.fixedSpawnZ());
            return full ? config.fixedSpawnWorld() + " " + x + " " + y + " " + z
                    : "X: " + x + ", Y: " + y + ", Z: " + z;
        }
        if (traderId == null) return ColorUtil.translate("&#F8BEFBСлучайные координаты");
        return ColorUtil.translate("&7Не заспавнен");
    }

    private String coordinate(String traderId, TraderInstance active, int axis) {
        if (active != null && active.location() != null) {
            Location location = active.location();
            switch (axis) {
                case 0: return String.valueOf(location.getBlockX());
                case 1: return String.valueOf(location.getBlockY());
                default: return String.valueOf(location.getBlockZ());
            }
        }
        TraderConfig config = traderId == null ? null : config(traderId);
        if (config == null || !config.hasFixedSpawnLocation()) return "0";
        switch (axis) {
            case 0: return String.valueOf((int) Math.floor(config.fixedSpawnX()));
            case 1: return String.valueOf((int) Math.floor(config.fixedSpawnY()));
            default: return String.valueOf((int) Math.floor(config.fixedSpawnZ()));
        }
    }

    private String locationText(Location location) {
        return "X: " + location.getBlockX() + ", Y: " + location.getBlockY() + ", Z: " + location.getBlockZ();
    }
}
