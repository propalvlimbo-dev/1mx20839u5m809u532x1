package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /duel для обычных игроков: только «/duel» (наше меню) и «/duel ник»
 * (сразу выбор кита). Полный набор подкоманд EpicDuels остаётся админам.
 *
 * Внутренние клики меню идут через /duelx, поэтому игроку не нужно знать
 * ни одной подкоманды. Подкоманды, на которые EpicDuels ставит кликабельные
 * кнопки в чате (accept / deny / forfeit / rematch), разрешены — иначе
 * нельзя принять вызов или сдаться.
 *
 * Таб-дополнение после «/duel » показывает ники онлайн-игроков.
 */
final class DuelCommandGuard implements CommandExecutor, TabCompleter {

    private static final Set<String> GAMEPLAY = new HashSet<String>(Arrays.asList(
            "accept", "deny", "cancel", "forfeit", "ff", "leave", "rematch", "toggle"));

    private final ElytrixDuelsBridge plugin;
    private final CommandExecutor original;
    private final TabCompleter completer;

    DuelCommandGuard(ElytrixDuelsBridge plugin, CommandExecutor original, TabCompleter completer) {
        this.plugin = plugin;
        this.original = original;
        this.completer = completer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return original != null && original.onCommand(sender, command, label, args);
        }
        Player player = (Player) sender;
        if (plugin.isAdmin(player)) {
            return original != null && original.onCommand(sender, command, label, args);
        }
        if (args.length == 0 || plugin.lower(args[0]).equals("menu")) {
            plugin.openMainMenu(player);
            return true;
        }
        String sub = plugin.lower(args[0]);
        // поиск пары и статистика — наши, с кнопками и подсказками
        if (sub.equals("stats") || sub.equals("stat")) {
            plugin.openStats(player);
            return true;
        }
        if (sub.equals("queue") || sub.equals("matchmaking")) {
            String[] rest = new String[Math.max(0, args.length - 1)];
            System.arraycopy(args, 1, rest, 0, rest.length);
            plugin.queueCommand(player, rest);
            return true;
        }
        if (sub.equals("team") || sub.equals("party") || sub.equals("duo")) {
            plugin.pickTeam(player);
            return true;
        }
        if (GAMEPLAY.contains(sub) && original != null) {
            return original.onCommand(sender, command, label, args);
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            plugin.requestDuel(player, target);
            return true;
        }
        plugin.msg(player, plugin.string("messages.duel-usage",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fДоступно: &f/duel &7и &f/duel ник"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player && plugin.isAdmin((Player) sender)) {
            return completer == null
                    ? new ArrayList<String>()
                    : completer.onTabComplete(sender, command, alias, args);
        }
        List<String> out = new ArrayList<String>();
        if (args.length != 1) return out;
        String prefix = plugin.lower(args[0]);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (name == null) continue;
            if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
        }
        return out;
    }
}
