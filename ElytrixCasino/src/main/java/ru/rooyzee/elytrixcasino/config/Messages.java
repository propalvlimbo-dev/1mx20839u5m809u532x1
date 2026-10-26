package ru.rooyzee.elytrixcasino.config;

import org.bukkit.command.CommandSender;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Messages {

    private final YamlFile file;

    public Messages(Main plugin) {
        this.file = new YamlFile(plugin, "messages.yml");
    }

    public void reload() {
        file.reload();
    }

    public String prefix() {
        return file.get().getString("prefix", "");
    }

    public String raw(String path) {
        if (file.get().isList(path)) {
            List<String> list = file.get().getStringList(path);
            return list.isEmpty() ? "" : list.get(0);
        }
        return file.get().getString(path, "");
    }

    public List<String> list(String path) {
        return file.get().getStringList(path);
    }

    public void send(CommandSender receiver, String path, String... replacements) {
        List<String> lines = lines(path);
        if (lines.isEmpty()) {
            return;
        }
        boolean first = true;
        for (String line : lines) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                line = line.replace(replacements[i], replacements[i + 1]);
            }
            if (line.isEmpty()) {
                receiver.sendMessage("");
                first = false;
                continue;
            }
            String out = first ? prefix() + line : line;
            receiver.sendMessage(Text.color(out));
            first = false;
        }
    }

    public String format(String path, String... replacements) {
        String message = raw(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return Text.color(message);
    }

    private List<String> lines(String path) {
        if (file.get().isList(path)) {
            return new ArrayList<>(file.get().getStringList(path));
        }
        String value = file.get().getString(path, "");
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(value);
    }
}
