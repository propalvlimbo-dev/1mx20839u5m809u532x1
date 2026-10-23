package ru.rooyzee.potionstacker;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** Сообщения из config.yml. */
public final class Messages {

    private final Main plugin;
    private String prefix = "";

    public Messages(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        prefix = Colors.color(config.getString("messages.prefix", ""));
    }

    public String raw(String key) {
        String text = plugin.getConfig().getString("messages." + key);
        return text == null ? "" : Colors.color(text);
    }

    public void send(CommandSender target, String key) {
        send(target, key, new String[0]);
    }

    /** Подстановки парами: send(player, "stacked", "%count%", "12"). */
    public void send(CommandSender target, String key, String... replacements) {
        String text = raw(key);
        if (text.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        target.sendMessage(prefix + text);
    }

    public String prefixed(String key) {
        return prefix + raw(key);
    }
}
