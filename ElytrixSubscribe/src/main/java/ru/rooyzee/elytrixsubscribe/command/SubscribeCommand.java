package ru.rooyzee.elytrixsubscribe.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.exceptions.NoneFunctionException;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.menu.ParrotMenu;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

public class SubscribeCommand implements CommandExecutor {

    // FIX: Убрали регистрацию из конструктора, теперь она в Main.onEnable()

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (args.length == 0) {
            commandSender.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.help")));
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "menu":
                if (!(commandSender instanceof Player)) return true;
                Player senderPlayer = (Player) commandSender;
                if (args.length > 1 && args[1].equalsIgnoreCase("parrot")) {
                    ParrotMenu.open(senderPlayer);
                }
                break;

            case "add":
                if (!commandSender.hasPermission("elytrixsubscribe.add")) {
                    commandSender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fНедостаточно прав!"));
                    return true;
                }
                if (args.length != 3) {
                    commandSender.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.help")));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    commandSender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fИгрок не в сети!"));
                    return true;
                }

                try {
                    int days = Integer.parseInt(args[2]);
                    long time = System.currentTimeMillis() + (long) days * 86400000L;
                    Main.getInstance().getService().setSub(target, time);
                    commandSender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fПодписка выдана!"));
                } catch (NumberFormatException e) {
                    commandSender.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.useNumberFormat")));
                }
                break;

            case "remove":
                if (!commandSender.hasPermission("elytrixsubscribe.add")) {
                    commandSender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fНедостаточно прав!"));
                    return true;
                }
                if (args.length != 2) {
                    commandSender.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.help")));
                    return true;
                }

                Player targetRemove = Bukkit.getPlayer(args[1]);
                if (targetRemove == null) {
                    commandSender.sendMessage(Utils.color("&f☁ &#F8BEFB&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fИгрок не в сети!"));
                    return true;
                }
                Main.getInstance().getService().remSub(targetRemove);
                commandSender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fПодписка забрана!"));
                break;

            case "delay":
                if (!(commandSender instanceof Player)) return true;
                Player sender = (Player) commandSender;
                try {
                    sender.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.delayMessage")
                            .replace("%cooldown", Utils.formatMillisToDate(
                                    Main.getInstance().getSubManager().convertPlayer(sender).getTimeToEnd()
                            ))));
                } catch (ConvertPlayerException var12) {
                    sender.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.noneSubscribe")));
                }
                break;

            case "toggle":
                if (!(commandSender instanceof Player)) return true;
                Player player = (Player) commandSender;
                if (args.length < 2) {
                    player.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fИспользуйте: &#F8BEFB/sub toggle [функция]"));
                    return true;
                }
                if (!Main.getInstance().getFunctionsManager().hasFunction(args[1])) {
                    player.sendMessage(Utils.color("&f☁ &#F8BEFB&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fФункция не найдена."));
                    return true;
                }
                try {
                    Function function = Main.getInstance().getFunctionsManager().getFunction(args[1]);
                    PlayerSub playerSub = Main.getInstance().getSubManager().convertPlayer(player);
                    if (playerSub.getEnabledFunctions().contains(function)) {
                        function.onDisable(player);
                    } else {
                        function.onEnable(player);
                    }
                } catch (NoneFunctionException | ConvertPlayerException var11) {
                    player.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.noneSubscribe")));
                }
                break;
        }
        return true;
    }
}