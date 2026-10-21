package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /duelx — продолжение потока EpicDuels после наших меню BMenu.
 *
 *   /duelx target <ник>            выбрать соперника и открыть выбор кита
 *   /duelx team                    открыть выбор тиммейта (быстрый 2 на 2)
 *   /duelx kit <кит>               кит выбран в нашем меню
 *   /duelx rounds <1|3|5>          число раундов и выбор карты
 *   /duelx pick                    открыть выбор игрока
 *   /duelx queue <кит|leave>       встать в очередь / выйти
 *   /duelx top                     таблица лидеров
 *   /duelx kits [reset]            создать 10 готовых наборов
 *   /duelx confirm | cancel        начать бой / отменить
 *   /duelx open <menu>             открыть меню BMenu
 */
public final class DuelXCommand implements CommandExecutor, TabCompleter {

    private final ElytrixDuelsBridge plugin;

    DuelXCommand(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            usage(player, "/duelx <pick|target|team|kit|rounds|queue|top|kits|confirm|cancel|open>");
            return true;
        }
        String sub = plugin.lower(args[0]);
        try {
            if (sub.equals("target")) {
                if (args.length < 2) return usage(player, "/duelx target <ник>");
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.msg(player, plugin.string("messages.player-gone",
                            "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cИгрок уже вышел."));
                    return true;
                }
                plugin.requestDuel(player, target);
                return true;
            }
            if (sub.equals("team")) {
                plugin.pickTeam(player);
                return true;
            }
            if (sub.equals("kit")) {
                if (args.length < 2) return usage(player, "/duelx kit <кит>");
                plugin.kit(player, args[1]);
                return true;
            }
            if (sub.equals("confirm")) {
                plugin.confirm(player);
                return true;
            }
            if (sub.equals("cancel")) {
                plugin.cancelFlow(player);
                return true;
            }
            if (sub.equals("rounds")) {
                if (args.length < 2) return usage(player, "/duelx rounds <1|3|5>");
                int bestOf;
                try {
                    bestOf = Integer.parseInt(args[1]);
                } catch (NumberFormatException error) {
                    return usage(player, "/duelx rounds <1|3|5>");
                }
                if (bestOf != 1 && bestOf != 3 && bestOf != 5) return usage(player, "/duelx rounds <1|3|5>");
                return rounds(player, bestOf);
            }
            if (sub.equals("pick")) {
                plugin.pickOpponent(player);
                return true;
            }
            if (sub.equals("top")) {
                plugin.runOriginal(player, "leaderboard", "wins");
                return true;
            }
            if (sub.equals("queue")) {
                if (args.length < 2) return usage(player, "/duelx queue <кит|leave>");
                plugin.runOriginal(player, "queue", args[1]);
                return true;
            }
            if (sub.equals("kits")) {
                plugin.installKits(player, args.length > 1
                        && plugin.lower(args[1]).equals("reset"));
                return true;
            }
            if (sub.equals("open")) {
                if (args.length < 2) return usage(player, "/duelx open <menu>");
                plugin.menus().open(player, args[1]);
                return true;
            }
        } catch (Throwable error) {
            plugin.getLogger().warning("duelx " + sub + ": " + error);
            plugin.msg(player, "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНе удалось продолжить: " + error.getClass().getSimpleName());
            return true;
        }
        return usage(player, "/duelx <pick|target|team|kit|rounds|queue|top|kits|confirm|cancel|open>");
    }

    private boolean usage(Player player, String text) {
        plugin.msg(player, "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fИспользование: &#F8BEFB" + text);
        return true;
    }

    // ------------------------------------------------------------------ flow

    private boolean rounds(Player player, int bestOf) {
        Object gui = plugin.duels().gui();
        UUID uuid = player.getUniqueId();
        Object target = Duels.call(gui, "getChallengeTarget", uuid);
        Object kit = Duels.call(gui, "getChallengeKit", uuid);
        if (target == null || kit == null) {
            plugin.msg(player, plugin.string("messages.no-challenge",
                    "&f☁ &dᴇʟʏᴛʀɪx &7» &cСначала выбери игрока и кит."));
            return true;
        }
        Duels.call(gui, "setChallengeRounds", uuid, bestOf);
        player.closeInventory();
        // выбор карты убран: вызов уходит на случайную готовую арену
        return plugin.duels().finishChallenge(player);
    }




    // ------------------------------------------------------------- tab list

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("pick", "target", "team", "kit", "rounds",
                    "queue", "top", "confirm", "cancel", "kits", "open"));
        }
        if (args.length == 2) {
            String sub = plugin.lower(args[0]);
            if (sub.equals("rounds")) return filter(args[1], Arrays.asList("1", "3", "5"));
            if (sub.equals("kits")) return filter(args[1], Arrays.asList("reset"));
            if (sub.equals("queue")) {
                List<String> kits = plugin.getConfig().getStringList("kits.names");
                if (kits == null || kits.isEmpty()) kits = Arrays.asList(Kits.NAMES);
                List<String> values = new ArrayList<String>(kits);
                values.add("leave");
                return filter(args[1], values);
            }
            if (sub.equals("kit")) {
                List<String> kits = plugin.getConfig().getStringList("kits.names");
                if (kits == null || kits.isEmpty()) {
                    kits = plugin.getConfig().getStringList("kits"); // старый формат
                }
                if (kits == null || kits.isEmpty()) {
                    kits = Arrays.asList(Kits.NAMES);
                }
                return filter(args[1], kits);
            }
            if (sub.equals("target")) {
                List<String> names = new ArrayList<String>();
                for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                return filter(args[1], names);
            }
        }
        return new ArrayList<String>();
    }

    private List<String> filter(String prefix, List<String> values) {
        List<String> out = new ArrayList<String>();
        String low = plugin.lower(prefix);
        for (String value : values) {
            if (low.isEmpty() || value.toLowerCase().startsWith(low)) out.add(value);
        }
        return out;
    }
}
