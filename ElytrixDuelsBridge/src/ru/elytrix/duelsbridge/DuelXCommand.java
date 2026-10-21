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
 *   /duelx rounds <1|3|5>          выбрать число раундов и открыть выбор карты
 *   /duelx party <team|tournament> режим групповой дуэли
 *   /duelx size <2|3|4>            размер команд, затем выбор кита
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
            plugin.msg(player, "&f☁ &dᴇʟʏᴛʀɪx &7» &fИспользование: &d/duelx <rounds|party|size|open>");
            return true;
        }
        String sub = plugin.lower(args[0]);
        try {
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
                plugin.open(player, args[1]);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            plugin.getLogger().warning("duelx " + sub + ": " + error);
            plugin.msg(player, "&f☁ &dᴇʟʏᴛʀɪx &7» &cНе удалось продолжить: " + error.getClass().getSimpleName());
            return true;
        }
        return usage(player, "/duelx <rounds|party|size|open>");
    }

    private boolean usage(Player player, String text) {
        plugin.msg(player, "&f☁ &dᴇʟʏᴛʀɪx &7» &fИспользование: &d" + text);
        return true;
    }

    // ------------------------------------------------------------------ flow

    private Object gui() throws ReflectiveOperationException {
        Object epic = Bukkit.getPluginManager().getPlugin("EpicDuels");
        if (epic == null) throw new IllegalStateException("EpicDuels не найден");
        return epic.getClass().getMethod("getGUIManager").invoke(epic);
    }

    private boolean rounds(Player player, int bestOf) throws ReflectiveOperationException {
        Object gui = gui();
        UUID uuid = player.getUniqueId();
        Object target = gui.getClass().getMethod("getChallengeTarget", UUID.class).invoke(gui, uuid);
        Object kit = gui.getClass().getMethod("getChallengeKit", UUID.class).invoke(gui, uuid);
        if (target == null || kit == null) {
            plugin.msg(player, plugin.getConfig().getString("messages.no-challenge",
                    "&f☁ &dᴇʟʏᴛʀɪx &7» &cСначала выбери игрока и кит."));
            return true;
        }
        gui.getClass().getMethod("setChallengeRounds", UUID.class, int.class).invoke(gui, uuid, bestOf);
        gui.getClass().getMethod("openArenaSelect", Player.class, UUID.class, String.class)
                .invoke(gui, player, target, kit);
        return true;
    }

    private boolean partyTeam(Player player) throws ReflectiveOperationException {
        if (!feature("team-duels")) return off(player, "Групповые дуэли");
        Object gui = gui();
        player.closeInventory();
        gui.getClass().getMethod("openPartyTeamSizeMenu", Player.class).invoke(gui, player);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean partyTournament(Player player) throws ReflectiveOperationException {
        if (!feature("tournaments")) return off(player, "Турниры");
        Object gui = gui();
        Class<?> modeClass = Class.forName("dev.epicduels.model.PartyMode");
        Object mode = Enum.valueOf((Class) modeClass, "TOURNAMENT");
        gui.getClass().getMethod("setPartyFlowMode", UUID.class, modeClass)
                .invoke(gui, player.getUniqueId(), mode);
        player.closeInventory();
        gui.getClass().getMethod("openPartyKitMenu", Player.class, int.class).invoke(gui, player, 0);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean partySize(Player player, int perTeam) throws ReflectiveOperationException {
        Object gui = gui();
        String sizeName = perTeam == 2 ? "TWO_VS_TWO" : perTeam == 3 ? "THREE_VS_THREE" : "FOUR_VS_FOUR";
        Class<?> sizeClass = Class.forName("dev.epicduels.model.TeamSize");
        Class<?> modeClass = Class.forName("dev.epicduels.model.PartyMode");
        Object size = Enum.valueOf((Class) sizeClass, sizeName);
        Object mode = Enum.valueOf((Class) modeClass, "TEAM_DUEL");

        Object party = party(player);
        int need = (Integer) size.getClass().getMethod("getTotalPlayers").invoke(size);
        int have = party == null ? 0 : (Integer) party.getClass().getMethod("size").invoke(party);
        if (have < need) {
            plugin.msg(player, plugin.getConfig().getString("messages.not-enough-members",
                    "&f☁ &dᴇʟʏᴛʀɪx &7» &cВ группе мало игроков: нужно &f%need%&c, сейчас &f%have%&c.")
                    .replace("%need%", String.valueOf(need)).replace("%have%", String.valueOf(have)));
            return true;
        }
        gui.getClass().getMethod("setPartyFlowMode", UUID.class, modeClass)
                .invoke(gui, player.getUniqueId(), mode);
        gui.getClass().getMethod("setPartyFlowTeamSize", UUID.class, sizeClass)
                .invoke(gui, player.getUniqueId(), size);
        player.closeInventory();
        gui.getClass().getMethod("openPartyKitMenu", Player.class, int.class).invoke(gui, player, 0);
        return true;
    }

    private Object party(Player player) throws ReflectiveOperationException {
        Object epic = Bukkit.getPluginManager().getPlugin("EpicDuels");
        if (epic == null) return null;
        Object manager = epic.getClass().getMethod("getPartyManager").invoke(epic);
        return manager.getClass().getMethod("getPartyOf", UUID.class).invoke(manager, player.getUniqueId());
    }

    private boolean feature(String name) throws ReflectiveOperationException {
        Object epic = Bukkit.getPluginManager().getPlugin("EpicDuels");
        if (epic == null) return true;
        Object value = epic.getClass().getMethod("isFeatureEnabled", String.class).invoke(epic, name);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    private boolean off(Player player, String what) {
        plugin.msg(player, plugin.getConfig().getString("messages.feature-off",
                "&f☁ &dᴇʟʏᴛʀɪx &7» &cВыключено на сервере: &f%name%").replace("%name%", what));
        return true;
    }

    // ------------------------------------------------------------- tab list

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], Arrays.asList("rounds", "party", "size", "open"));
        if (args.length == 2) {
            String sub = plugin.lower(args[0]);
            if (sub.equals("rounds")) return filter(args[1], Arrays.asList("1", "3", "5"));
            if (sub.equals("party")) return filter(args[1], Arrays.asList("team", "tournament"));
            if (sub.equals("size")) return filter(args[1], Arrays.asList("2", "3", "4"));
        }
        return new ArrayList<String>();
    }

    private List<String> filter(String prefix, List<String> values) {
        List<String> out = new ArrayList<String>();
        String low = plugin.lower(prefix);
        for (String value : values) {
            if (low.isEmpty() || value.startsWith(low)) out.add(value);
        }
        return out;
    }
}
