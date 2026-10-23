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
 *   /duelx menu | stats            главное меню / статистика
 *   /duelx target <ник>            выбрать соперника и открыть выбор кита
 *   /duelx pick                    открыть выбор игрока
 *   /duelx kit <кит>               кит выбран в нашем меню
 *   /duelx kitmenu                 выбрать кит и встать в очередь
 *   /duelx rounds <1|3|5>          число раундов (арена — случайная)
 *   /duelx team                    выбор напарника 2 на 2
 *   /duelx team random             пригласить случайного игрока
 *   /duelx team accept|deny|leave  принять / отклонить / выйти из пары
 *   /duelx team solo              2 на 2 без напарника (рандом в бою)
 *   /duelx queue [кит]             очередь 1 на 1 / выбор кита
 *   /duelx queue team [кит]        очередь 2 на 2 без напарника
 *   /duelx queue leave             выйти из очереди
 *   /duelx top                     таблица лидеров
 *   /duelx bounds                  границы текущей арены (админ)
 *   /duelx kits [reset]            создать 10 готовых наборов
 *   /duelx confirm | cancel        начать бой / отменить
 *   /duelx open <menu>             открыть меню BMenu
 */
public final class DuelXCommand implements CommandExecutor, TabCompleter {

    private final ElytrixDuelsBridge plugin;

    /**
     * Подкоманды, которые доступны и как /duelx ..., и как /duel ...
     * Игроку в чате показываем вторую форму: /duel виден клиенту,
     * поэтому ванильного «команда не распознана» не бывает.
     */
    static final List<String> SUBS = Arrays.asList(
            "menu", "main", "stats", "stat", "pick", "target", "team", "kit",
            "kitmenu", "kitedit", "rounds", "queue", "top", "bounds", "confirm", "cancel",
            "kits", "open", "arena");

    DuelXCommand(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    /** Выполнить подкоманду из /duel (без отдельной регистрации команды). */
    boolean run(Player player, String[] args) {
        return onCommand(player, null, "duelx", args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            usage(player, "/duelx <pick|target|team|kit|rounds|queue|top|bounds|kits|confirm|cancel|open>");
            return true;
        }
        String sub = plugin.lower(args[0]);
        try {
            if (sub.equals("target")) {
                if (args.length < 2) return usage(player, "/duelx target <ник>");
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.msg(player, plugin.string("messages.player-gone",
                            "&#F8BEFBДуэли &8» &cИгрок уже вышел."));
                    return true;
                }
                plugin.requestDuel(player, target);
                return true;
            }
            if (sub.equals("menu") || sub.equals("main")) {
                plugin.openMainMenu(player);
                return true;
            }
            if (sub.equals("stats") || sub.equals("stat")) {
                plugin.openStats(player);
                return true;
            }
            if (sub.equals("arena")) {
                String[] rest = new String[Math.max(0, args.length - 1)];
                System.arraycopy(args, 1, rest, 0, rest.length);
                plugin.arenaCommand(player, rest);
                return true;
            }
            if (sub.equals("kitmenu")) {
                plugin.kitMenu(player, args.length > 1 ? plugin.lower(args[1]) : null);
                return true;
            }
            if (sub.equals("team")) {
                if (args.length == 1) {
                    plugin.pickTeam(player);
                    return true;
                }
                String action = plugin.lower(args[1]);
                if (action.equals("random") || action.equals("рандом")) {
                    plugin.pickTeamRandom(player);
                    return true;
                }
                if (action.equals("accept") || action.equals("принять")) {
                    plugin.teamAccept(player, args.length > 2 ? args[2] : null);
                    return true;
                }
                if (action.equals("deny") || action.equals("отклонить")) {
                    plugin.teamDeny(player);
                    return true;
                }
                if (action.equals("leave") || action.equals("выйти")) {
                    plugin.teamLeave(player);
                    return true;
                }
                if (action.equals("solo") || action.equals("random-solo")) {
                    plugin.pickSoloTeam(player);
                    return true;
                }
                return usage(player, "/duelx team <random|accept|deny|leave|solo>");
            }
            if (sub.equals("kit")) {
                if (args.length < 2) return usage(player, "/duelx kit <кит>");
                if (kitEditor(player, args[1], args)) return true;
                plugin.kit(player, args[1]);
                return true;
            }
            if (sub.equals("kitedit")) {
                if (!plugin.isAdmin(player)) {
                    plugin.msg(player, "&#F8BEFBДуэли &8» &cТолько администратор.");
                    return true;
                }
                if (args.length > 1) plugin.kits().edit(player, args[1]);
                else plugin.kits().editMenu(player);
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
                if (args.length < 2) {
                    plugin.queueCommand(player, new String[0]);
                    return true;
                }
                String[] rest = new String[args.length - 1];
                System.arraycopy(args, 1, rest, 0, rest.length);
                plugin.queueCommand(player, rest);
                return true;
            }
            if (sub.equals("bounds")) {
                plugin.boundsInfo(player);
                return true;
            }
            if (sub.equals("kits")) {
                plugin.installKits(player, args.length > 1
                        && plugin.lower(args[1]).equals("reset"));
                return true;
            }
            if (sub.equals("open")) {
                if (args.length < 2) return usage(player, "/duelx open <menu>");
                plugin.menus().open(player, args[1], plugin.menuArgsFor(player, args[1]));
                return true;
            }
        } catch (Throwable error) {
            plugin.getLogger().warning("duelx " + sub + ": " + error);
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНе удалось продолжить: " + error.getClass().getSimpleName());
            return true;
        }
        return usage(player, "/duelx <pick|target|team|kit|rounds|queue|top|bounds|kits|confirm|cancel|open>");
    }

    private boolean usage(Player player, String text) {
        plugin.msg(player, "&#F8BEFBДуэли &8» &fИспользование: &#F8BEFB" + text);
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
                    "&#F8BEFBДуэли &8» &cСначала выбери игрока и кит."));
            return true;
        }
        Duels.call(gui, "setChallengeRounds", uuid, bestOf);
        player.closeInventory();
        // выбор карты убран: вызов уходит на случайную готовую арену
        return plugin.duels().finishChallenge(player);
    }




    // ------------------------------------------------------------- tab list

    /**
     * Редактор наборов: /duelx kit edit|save|create|delete|rename|icon|reset|list.
     * Действия — только для администраторов, иначе то же имя значит «выбрать кит».
     */
    private boolean kitEditor(Player player, String action, String[] args) {
        String sub = plugin.lower(action);
        boolean known = sub.equals("edit") || sub.equals("редактор") || sub.equals("save")
                || sub.equals("create") || sub.equals("сохранить") || sub.equals("delete")
                || sub.equals("remove") || sub.equals("удалить") || sub.equals("rename")
                || sub.equals("переименовать") || sub.equals("icon") || sub.equals("иконка")
                || sub.equals("reset") || sub.equals("сброс") || sub.equals("list")
                || sub.equals("список");
        if (!known) return false;
        if (!plugin.isAdmin(player)) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТолько администратор.");
            return true;
        }
        String name = args.length > 2 ? args[2] : null;
        if (sub.equals("list") || sub.equals("список")) {
            plugin.kits().list(player);
            return true;
        }
        if (sub.equals("edit") || sub.equals("редактор")) {
            if (name == null) plugin.kits().editMenu(player);
            else plugin.kits().edit(player, name);
            return true;
        }
        if (name == null) {
            usage(player, "/duelx kit " + sub + " <кит>");
            return true;
        }
        if (sub.equals("save") || sub.equals("create") || sub.equals("сохранить")) {
            plugin.kits().save(player, name);
            return true;
        }
        if (sub.equals("delete") || sub.equals("remove") || sub.equals("удалить")) {
            plugin.kits().remove(player, name);
            return true;
        }
        if (sub.equals("rename") || sub.equals("переименовать")) {
            plugin.kits().rename(player, name, args.length > 3 ? args[3] : null);
            return true;
        }
        if (sub.equals("icon") || sub.equals("иконка")) {
            plugin.kits().icon(player, name);
            return true;
        }
        plugin.kits().reset(player, name);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("menu", "stats", "pick", "target", "team", "kit",
                    "kitmenu", "kitedit", "rounds", "queue", "top", "bounds", "confirm", "cancel",
                    "kits", "open", "arena"));
        }
        if (args.length == 2) {
            String sub = plugin.lower(args[0]);
            if (sub.equals("rounds")) return filter(args[1], Arrays.asList("1", "3", "5"));
            if (sub.equals("kits")) return filter(args[1], Arrays.asList("reset"));
            if (sub.equals("team")) return filter(args[1], Arrays.asList("random", "accept", "deny", "leave", "solo"));
            if (sub.equals("queue")) {
                List<String> k = kits();
                List<String> values = new ArrayList<String>(k);
                values.add("leave");
                values.add("team");
                return filter(args[1], values);
            }
            if (sub.equals("kit")) {
                return filter(args[1], kits());
            }
            if (sub.equals("arena")) {
                return filter(args[1], Arrays.asList("create", "spawn1", "spawn2", "ready",
                        "list", "remove", "tp"));
            }
            if (sub.equals("target")) {
                List<String> names = new ArrayList<String>();
                for (Player online : Bukkit.getOnlinePlayers()) names.add(online.getName());
                return filter(args[1], names);
            }
        }
        return new ArrayList<String>();
    }

    private List<String> kits() {
        List<String> names = plugin.getConfig().getStringList("kits.names");
        if (names == null || names.isEmpty()) names = Arrays.asList(Kits.NAMES);
        List<String> out = new ArrayList<String>();
        for (String name : names) out.add(Kits.canonical(name));
        return out;
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
