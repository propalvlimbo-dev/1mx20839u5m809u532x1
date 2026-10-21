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
 *   /duelx party <team|tournament> режим группы
 *   /duelx size <2|3|4>            размер команд, затем выбор кита
 *   /duelx confirm | cancel        начать бой по группе / отменить
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
            usage(player, "/duelx <target|team|kit|rounds|party|size|confirm|open>");
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
            if (sub.equals("party")) {
                if (args.length < 2) return usage(player, "/duelx party <team|tournament>");
                String mode = plugin.lower(args[1]);
                if (mode.equals("team") || mode.equals("команда")) return partyTeam(player);
                if (mode.equals("tournament") || mode.equals("турнир")) return partyTournament(player);
                return usage(player, "/duelx party <team|tournament>");
            }
            if (sub.equals("size")) {
                if (args.length < 2) return usage(player, "/duelx size <2|3|4>");
                int perTeam;
                try {
                    perTeam = Integer.parseInt(args[1]);
                } catch (NumberFormatException error) {
                    return usage(player, "/duelx size <2|3|4>");
                }
                if (perTeam < 2 || perTeam > 4) return usage(player, "/duelx size <2|3|4>");
                return partySize(player, perTeam);
            }
            if (sub.equals("open")) {
                if (args.length < 2) return usage(player, "/duelx open <menu>");
                plugin.menus().open(player, args[1]);
                return true;
            }
        } catch (RuntimeException error) {
            plugin.getLogger().warning("duelx " + sub + ": " + error);
            plugin.msg(player, "&f☁ &dᴇʟʏᴛʀɪx &7» &cНе удалось продолжить: " + error.getClass().getSimpleName());
            return true;
        }
        return usage(player, "/duelx <target|team|kit|rounds|party|size|confirm|open>");
    }

    private boolean usage(Player player, String text) {
        plugin.msg(player, "&f☁ &dᴇʟʏᴛʀɪx &7» &fИспользование: &d" + text);
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
        Duels.call(gui, "openArenaSelect", player, target, kit);
        return true;
    }

    private boolean partyTeam(Player player) {
        if (!plugin.duels().feature("team-duels")) return off(player, "Групповые дуэли");
        Object gui = plugin.duels().gui();
        player.closeInventory();
        Duels.call(gui, "openPartyTeamSizeMenu", player);
        return true;
    }

    private boolean partyTournament(Player player) {
        if (!plugin.duels().feature("tournaments")) return off(player, "Турниры");
        UUID id = player.getUniqueId();
        plugin.duels().flowMode(id, "TOURNAMENT");
        player.closeInventory();
        Object gui = plugin.duels().gui();
        Duels.call(gui, "openPartyKitMenu", player, 0);
        return true;
    }

    private boolean partySize(Player player, int perTeam) {
        String sizeName = perTeam == 2 ? "TWO_VS_TWO" : perTeam == 3 ? "THREE_VS_THREE" : "FOUR_VS_FOUR";
        UUID id = player.getUniqueId();
        Object party = plugin.duels().partyOf(id);
        int need = perTeam * 2;
        int have = party == null ? 0 : plugin.duels().partySize(party);
        if (have < need) {
            plugin.msg(player, plugin.string("messages.not-enough-members",
                    "&f☁ &dᴇʟʏᴛʀɪx &7» &cВ группе мало игроков: нужно &f%need%&c, сейчас &f%have%&c.")
                    .replace("%need%", String.valueOf(need)).replace("%have%", String.valueOf(have)));
            return true;
        }
        plugin.duels().flowMode(id, "TEAM_DUEL");
        plugin.duels().flowSize(id, sizeName);
        player.closeInventory();
        Duels.call(plugin.duels().gui(), "openPartyKitMenu", player, 0);
        return true;
    }

    private boolean off(Player player, String what) {
        plugin.msg(player, plugin.string("messages.feature-off",
                "&f☁ &dᴇʟʏᴛʀɪx &7» &cВыключено на сервере: &f%name%").replace("%name%", what));
        return true;
    }

    // ------------------------------------------------------------- tab list

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("target", "team", "kit", "rounds", "party",
                    "size", "confirm", "cancel", "open"));
        }
        if (args.length == 2) {
            String sub = plugin.lower(args[0]);
            if (sub.equals("rounds")) return filter(args[1], Arrays.asList("1", "3", "5"));
            if (sub.equals("party")) return filter(args[1], Arrays.asList("team", "tournament"));
            if (sub.equals("size")) return filter(args[1], Arrays.asList("2", "3", "4"));
            if (sub.equals("kit")) {
                List<String> kits = plugin.getConfig().getStringList("kits");
                if (kits == null || kits.isEmpty()) {
                    kits = Arrays.asList("sword", "nodebuff", "uhc", "sumo", "gapple",
                            "archer", "mace", "crystal", "pot");
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
