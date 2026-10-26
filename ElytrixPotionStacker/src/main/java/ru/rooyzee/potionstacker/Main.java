package ru.rooyzee.potionstacker;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ElytrixPotionStacker — стакер зелий на нативном компоненте max_stack_size
 * без виртуальных счётчиков, дюпов и потери зелий в автозельеварках.
 */
public final class Main extends JavaPlugin implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "stack", "info");

    private Stacker stacker;
    private Messages messages;
    private BukkitTask refreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.stacker = new Stacker(this);
        stacker.load();

        getServer().getPluginManager().registerEvents(new StackerListener(this), this);

        PluginCommand cmd = getCommand("potionstacker");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        for (Player player : getServer().getOnlinePlayers()) {
            stacker.normalize(player.getInventory());
            stacker.normalize(player.getEnderChest());
        }

        startRefresh();
    }

    @Override
    public void onDisable() {
        stopRefresh();
    }

    public Stacker getStacker() {
        return stacker;
    }

    public Messages getMessages() {
        return messages;
    }

    private void startRefresh() {
        stopRefresh();
        if (stacker.getRefreshTicks() <= 0) {
            return;
        }

        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                stacker.normalize(player.getInventory());
            }
        }, stacker.getRefreshTicks(), stacker.getRefreshTicks());
    }

    private void stopRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void reload() {
        reloadConfig();
        stacker.load();
        messages.reload();
        for (Player player : getServer().getOnlinePlayers()) {
            stacker.normalize(player.getInventory());
            stacker.normalize(player.getEnderChest());
        }
        startRefresh();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elytrix.potionstacker.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                reload();
                messages.send(sender, "reloaded");
            }
            case "stack" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "only-player");
                    return true;
                }
                int changed = stacker.normalize(player.getInventory())
                        + stacker.normalize(player.getEnderChest());
                messages.send(sender, "stacked", "%count%", String.valueOf(changed));
            }
            case "info" -> {
                messages.send(sender, "info");
                StringBuilder limits = new StringBuilder();
                for (Map.Entry<Material, Integer> entry : stacker.getMaterialLimits().entrySet()) {
                    if (limits.length() > 0) {
                        limits.append("&7, ");
                    }
                    limits.append("&f").append(entry.getKey().name())
                            .append(" &7x&#F8BEFB").append(entry.getValue());
                }
                sender.sendMessage(Colors.color("&#F8BEFB&l┃ &7Лимиты: " + limits));
                sender.sendMessage(Colors.color("&#F8BEFB&l┃ &7Проверка: &#F8BEFB"
                        + stacker.getRefreshTicks() + " &7тиков"));
            }
            default -> messages.send(sender, "usage");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("elytrix.potionstacker.admin") || args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(prefix)) {
                result.add(sub);
            }
        }
        return result;
    }
}
