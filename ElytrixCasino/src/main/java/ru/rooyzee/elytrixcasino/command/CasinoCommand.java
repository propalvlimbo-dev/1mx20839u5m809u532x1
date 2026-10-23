package ru.rooyzee.elytrixcasino.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.automate.Automate;
import ru.rooyzee.elytrixcasino.miner.Miner;
import ru.rooyzee.elytrixcasino.roulette.Roulette;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CasinoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBCOMMANDS =
            Arrays.asList("reload", "create", "remove", "list", "tp", "debug");
    private static final List<String> CREATE_TYPES =
            Arrays.asList("roulette", "automate", "miner");

    private final Main plugin;

    public CasinoCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        if (!sender.hasPermission("elytrixcasino.admin")) {
            plugin.send(sender, "no-perm");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload":
                plugin.reload();
                plugin.send(sender, "reload");
                return true;

            case "create": {
                if (!(sender instanceof Player)) {
                    plugin.send(sender, "players-only");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.color("&c/" + label + " create roulette|automate|miner"));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (type.equals("roulette") || type.equals("рулетка")) {
                    Roulette roulette = plugin.getRouletteManager().create((Player) sender);
                    if (roulette == null) {
                        plugin.send(sender, "create-failed");
                        return true;
                    }
                    plugin.send(sender, "created", "%id%", roulette.getId(), "%type%", "Рулетка");
                    return true;
                }
                if (type.equals("miner") || type.equals("минёр") || type.equals("минер")) {
                    Miner miner = plugin.getMinerManager().create((Player) sender);
                    if (miner == null) {
                        plugin.send(sender, "create-failed");
                        return true;
                    }
                    plugin.send(sender, "created", "%id%", miner.getId(), "%type%", "Минёр");
                    return true;
                }
                if (type.equals("automate") || type.equals("authomate") || type.equals("автомат")) {
                    long bet = plugin.getAutomateSettings().getBet();
                    if (args.length >= 3) {
                        try {
                            bet = Long.parseLong(args[2]);
                        } catch (NumberFormatException e) {
                            plugin.send(sender, "create-failed");
                            return true;
                        }
                    }
                    if (bet < 1L) {
                        plugin.send(sender, "create-failed");
                        return true;
                    }
                    plugin.getAutomateManager().beginCreate((Player) sender, bet);
                    plugin.send(sender, "automate-bind", "%bet%", Text.formatMoney(bet));
                    return true;
                }
                sender.sendMessage(Text.color("&c/" + label + " create roulette|automate|miner"));
                return true;
            }

            case "remove": {
                if (args.length >= 2) {
                    String id = args[1];
                    if (plugin.getRouletteManager().remove(id)
                            || plugin.getAutomateManager().remove(id)
                            || plugin.getMinerManager().remove(id)) {
                        plugin.send(sender, "removed", "%id%", id);
                    } else {
                        plugin.send(sender, "not-found");
                    }
                    return true;
                }
                if (!(sender instanceof Player)) {
                    plugin.send(sender, "players-only");
                    return true;
                }
                boolean enabled = plugin.getRouletteManager().toggleRemoveMode((Player) sender);
                plugin.send(sender, enabled ? "remove-mode" : "remove-mode-off");
                return true;
            }

            case "list": {
                int count = plugin.getRouletteManager().getAll().size()
                        + plugin.getAutomateManager().getAll().size()
                        + plugin.getMinerManager().getAll().size();
                int pending = plugin.getLoader() == null ? 0 : plugin.getLoader().pendingCount();
                if (count == 0 && pending == 0) {
                    plugin.send(sender, "list-empty");
                    return true;
                }
                plugin.send(sender, "list-header", "%count%", String.valueOf(count));
                for (Roulette roulette : plugin.getRouletteManager().getAll()) {
                    sendEntry(sender, "Рулетка", roulette.getId(), roulette.getHomeLocation(), roulette.getState().name());
                }
                for (Automate automate : plugin.getAutomateManager().getAll()) {
                    String state = automate.isBusy() ? "BUSY" : "IDLE";
                    if (!automate.isHologramShown()) {
                        state += " (чанк не загружен)";
                    }
                    sendEntry(sender, "Автомат", automate.getId(), automate.getLocation(), state);
                }
                for (Miner miner : plugin.getMinerManager().getAll()) {
                    sendEntry(sender, "Минёр", miner.getId(), miner.getLocation(),
                            miner.isSpawned() ? "ACTIVE" : "UNLOADED");
                }
                if (pending > 0) {
                    sender.sendMessage(Text.color("&7● &eЕщё " + pending
                            + " не восстановлено: мир не загружен (записи целы в data.yml)"));
                }
                return true;
            }

            case "tp": {
                if (!(sender instanceof Player)) {
                    plugin.send(sender, "players-only");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.color("&c/" + label + " tp <id>"));
                    return true;
                }
                Roulette roulette = plugin.getRouletteManager().get(args[1]);
                if (roulette != null) {
                    ((Player) sender).teleport(roulette.getHomeLocation());
                    plugin.send(sender, "teleported", "%id%", roulette.getId());
                    return true;
                }
                Automate automate = plugin.getAutomateManager().get(args[1]);
                if (automate != null) {
                    ((Player) sender).teleport(automate.getLocation().add(0.5D, 0.0D, 0.5D));
                    plugin.send(sender, "teleported", "%id%", automate.getId());
                    return true;
                }
                Miner miner = plugin.getMinerManager().get(args[1]);
                if (miner != null) {
                    ((Player) sender).teleport(miner.getLocation());
                    plugin.send(sender, "teleported", "%id%", miner.getId());
                    return true;
                }
                plugin.send(sender, "not-found");
                return true;
            }

            case "debug": {
                if (!(sender instanceof Player)) {
                    plugin.send(sender, "players-only");
                    return true;
                }
                if (plugin.getCasinoListener() == null) {
                    return true;
                }
                boolean enabled = plugin.getCasinoListener().toggleDebug((Player) sender);
                sender.sendMessage(Text.color(enabled
                        ? "&#F8BEFBДиагностика кликов включена. &7Кликайте по объекту — в чат будет писаться, "
                        + "что видит плагин."
                        : "&#F8BEFBДиагностика кликов выключена."));
                return true;
            }

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendEntry(CommandSender sender, String type, String id, Location location, String state) {
        sender.sendMessage(Text.color(plugin.rawMsg("list-entry")
                .replace("%type%", type)
                .replace("%id%", id)
                .replace("%world%", String.valueOf(location.getWorld() == null ? "?" : location.getWorld().getName()))
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ()))
                .replace("%state%", state)));
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getMessages().list("help")) {
            sender.sendMessage(Text.color(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("elytrixcasino.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(ADMIN_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                return filter(CREATE_TYPES, args[1]);
            }
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("tp")) {
                List<String> ids = new ArrayList<>(plugin.getRouletteManager().getIds());
                ids.addAll(plugin.getAutomateManager().getIds());
                ids.addAll(plugin.getMinerManager().getIds());
                return filter(ids, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
