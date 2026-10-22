package ru.rooyzee.elytrixsubscribe.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.pet.PetData;
import ru.rooyzee.elytrixsubscribe.pet.PetType;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

/**
 * /subscribe — меню подписки и служебные команды.
 *
 * Из меню BMenu кнопки вызывают эти же подкоманды от имени игрока:
 *   /subscribe menu pets
 *   /subscribe pet enable parrot
 *   /subscribe pet behavior parrot
 *   /subscribe toggle auto-totem
 */
public class SubscribeCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;

    public SubscribeCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                this.plugin.getMenuService().openMain((Player) sender);
            } else {
                this.help(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("add") || sub.equals("remove")) {
            this.admin(sender, sub, args);
            return true;
        }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("elytrixsubscribe.add")) {
                this.noPermission(sender);
                return true;
            }
            this.plugin.reloadConfig();
            this.plugin.msg(sender instanceof Player ? (Player) sender : null,
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fКонфиг перечитан.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player player = (Player) sender;

        if (sub.equals("menu")) {
            if (args.length < 2) {
                this.plugin.getMenuService().openMain(player);
                return true;
            }
            String what = args[1].toLowerCase(Locale.ROOT);
            PetType pet = PetType.byId(what);
            if (pet != null) {
                this.plugin.getMenuService().openPet(player, pet);
            } else if (what.equals("pets") || what.equals("питомцы")) {
                this.plugin.getMenuService().openPets(player);
            } else if (what.equals("functions") || what.equals("функции")) {
                this.plugin.getMenuService().openFunctions(player);
            } else {
                this.plugin.getMenuService().openMain(player);
            }
            return true;
        }
        if (sub.equals("pets") || sub.equals("питомцы")) {
            this.plugin.getMenuService().openPets(player);
            return true;
        }
        if (sub.equals("pet") || sub.equals("питомец")) {
            this.pet(player, args);
            return true;
        }
        if (sub.equals("delay")) {
            this.delay(player);
            return true;
        }
        if (sub.equals("toggle") || sub.equals("переключить")) {
            if (args.length < 2) {
                this.plugin.msg(player, "&7Используй: &f/subscribe toggle <функция>");
                return true;
            }
            PetType pet = PetType.byId(args[1]);
            if (pet != null) {
                this.plugin.getPetManager().toggle(player, pet);
                return true;
            }
            this.toggleFunction(player, args[1]);
            return true;
        }
        this.help(player);
        return true;
    }

    // -------------------------------------------------------------- питомцы

    private void pet(Player player, String[] args) {
        if (args.length < 2) {
            this.plugin.getMenuService().openPets(player);
            return;
        }
        // пишем и «pet parrot toggle», и «pet toggle parrot» — меню шлёт оба варианта
        PetType type = PetType.byId(args[1]);
        String action;
        if (type != null) {
            action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "menu";
        } else {
            PetType swapped = args.length > 2 ? PetType.byId(args[2]) : null;
            if (swapped == null) {
                this.plugin.msg(player, "&7Питомцы: &f" + PetType.ids()
                        + " &7— /subscribe pet <питомец> toggle");
                return;
            }
            type = swapped;
            action = args[1].toLowerCase(Locale.ROOT);
        }
        if (!this.plugin.getSubManager().hasSub(player)) {
            this.plugin.msg(player, this.plugin.message("noneSubscribe",
                    "&fУ вас нет активной подписки."));
            return;
        }

        if (action.equals("enable") || action.equals("включить")) {
            this.plugin.getPetManager().enable(player, type);
            return;
        }
        if (action.equals("disable") || action.equals("выключить")) {
            this.plugin.getPetManager().disable(player, type);
            return;
        }
        if (action.equals("toggle")) {
            this.plugin.getPetManager().toggle(player, type);
            return;
        }
        if (action.equals("behavior") || action.equals("режим")) {
            this.plugin.getPetManager().cycleBehavior(player, type);
            this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                    + "&fПоведение: &#F8BEFB" + this.plugin.getPetManager().behaviorLine(player, type));
            this.plugin.getMenuService().openPet(player, type);
            return;
        }
        if (action.equals("variant") || action.equals("look") || action.equals("окрас")
                || action.equals("glow") || action.equals("baby")) {
            this.plugin.getPetManager().cycleLook(player, type);
            this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                    + "&f" + type.lookName() + ": &#F8BEFB"
                    + this.plugin.getPetManager().lookLine(player, type));
            this.plugin.getMenuService().openPet(player, type);
            return;
        }
        if (action.equals("name") || action.equals("имя")) {
            this.petName(player, type, args);
            return;
        }
        this.plugin.getMenuService().openPet(player, type);
    }

    private void petName(Player player, PetType type, String[] args) {
        if (args.length > 3) {
            StringBuilder name = new StringBuilder();
            for (int index = 3; index < args.length; index++) {
                if (name.length() > 0) name.append(' ');
                name.append(args[index]);
            }
            String value = name.toString();
            if (value.equalsIgnoreCase("сброс") || value.equalsIgnoreCase("reset")) value = "";
            this.plugin.getPetManager().setName(player, type, value);
            this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                    + "&fИмя питомца: &#F8BEFB"
                    + (value.isEmpty() ? "сброшено" : value));
            this.plugin.getMenuService().openPet(player, type);
            return;
        }
        PetData data = this.plugin.getPetManager().data(player, type);
        player.closeInventory();
        this.plugin.getChatInput().request(player,
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                        + "&fНапиши имя для питомца &f" + type.displayName() + "&f в чат."
                        + "&7 (сейчас: " + (data.name() == null ? "не задано" : data.name()) + "). "
                        + "&7Не ловит чат — &f/subscribe pet " + type.id() + " name <имя>",
                name -> {
                    String value = name.equalsIgnoreCase("сброс") ? "" : name;
                    this.plugin.getPetManager().setName(player, type, value);
                    this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fИмя питомца: &#F8BEFB" + (value.isEmpty() ? "сброшено" : value));
                    this.plugin.getMenuService().openPet(player, type);
                });
    }

    // ------------------------------------------------------------- функции

    private void toggleFunction(Player player, String name) {
        Function function = this.plugin.getFunctionsManager().find(name);
        if (function == null) {
            this.plugin.msg(player, "&7Функции: &fauto-totem&7, &fspeed&7, &fanti-fall&7, &fantifood");
            return;
        }
        if (this.plugin.getSubManager().isFunctionEnabled(player, function)) {
            function.onDisable(player);
        } else {
            function.onEnable(player);
        }
    }

    // ------------------------------------------------------- админ-команды

    private void admin(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission("elytrixsubscribe.add")) {
            this.noPermission(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Utils.color("&7Используй: &f/subscribe " + action + " <ник> [дни]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                    + "&fИгрок не в сети!"));
            return;
        }
        if (action.equals("remove")) {
            this.plugin.getService().remSub(target);
            sender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                    + "&fПодписка забрана!"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Utils.color("&7Используй: &f/subscribe add <ник> <дни>"));
            return;
        }
        int days;
        try {
            days = Integer.parseInt(args[2]);
        } catch (NumberFormatException error) {
            sender.sendMessage(Utils.color(this.plugin.message("useNumberFormat",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fОшибка: используйте числа в аргументе времени.")));
            return;
        }
        long time = System.currentTimeMillis() + (long) days * 86400000L;
        this.plugin.getService().setSub(target, time);
        sender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                + "&fПодписка выдана: &f" + target.getName() + " &7на &f" + days + " дн."));
    }

    private void delay(Player player) {
        if (!this.plugin.getSubManager().hasSub(player)) {
            this.plugin.msg(player, this.plugin.message("noneSubscribe",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fУ вас нет активной подписки."));
            return;
        }
        this.plugin.msg(player, this.plugin.message("delayMessage",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                        + "&fДо конца вашей подписки осталось: &#F8BEFB%cooldown")
                .replace("%cooldown", this.plugin.getSubManager().timeLeft(player)));
    }

    private void help(CommandSender sender) {
        String help = this.plugin.getConfig().getString("messages.help");
        if (help == null) {
            sender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                    + "&f/subscribe menu&7, &f/subscribe pets&7, &f/subscribe delay"));
            return;
        }
        for (String line : help.split("\n")) sender.sendMessage(Utils.color(line));
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                + "&fНедостаточно прав!"));
    }

    // ---------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            for (String value : Arrays.asList("menu", "pets", "pet", "toggle", "delay",
                    "add", "remove", "reload")) {
                if (value.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(value);
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("pet") || args[0].equalsIgnoreCase("menu")
                || args[0].equalsIgnoreCase("toggle"))) {
            for (PetType type : PetType.values()) {
                if (type.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(type.id());
            }
            if (args[0].equalsIgnoreCase("toggle")) {
                for (Function function : this.plugin.getFunctionsManager().all()) {
                    if (function.getName().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(function.getName());
                    }
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("pet")) {
            for (String value : Arrays.asList("enable", "disable", "toggle", "behavior",
                    "variant", "name")) {
                if (value.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(value);
            }
        }
        return out;
    }
}
