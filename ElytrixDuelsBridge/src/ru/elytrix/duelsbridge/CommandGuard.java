package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * Замена PLHide для 1.21.x:
 *   * hidden  — команды не отправляются клиенту, их не видно в автодополнении;
 *   * blocked — команды нельзя выполнить (в том числе через /bukkit:имя).
 *
 * Админы (право epicduels.admin, elytrix.guard.bypass или OP) видят всё.
 * Настраивается в config.yml, раздел command-guard.
 */
final class CommandGuard implements Listener {

    private final ElytrixDuelsBridge plugin;

    CommandGuard(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("command-guard.enabled", true);
    }

    private static String plain(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    private Set<String> list(String path, String... fallback) {
        List<String> raw = plugin.getConfig().getStringList(path);
        if (raw == null || raw.isEmpty()) raw = Arrays.asList(fallback);
        Set<String> out = new HashSet<String>();
        for (String value : raw) {
            if (value == null) continue;
            String clean = plain(value.trim());
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }

    private Set<String> hidden() {
        return list("command-guard.hidden",
                "plugins", "pl", "version", "ver", "about", "icanhasbukkit", "help",
                "bmenu", "epicduels", "duelx", "dx", "party", "p");
    }

    private Set<String> blocked() {
        return list("command-guard.blocked",
                "plugins", "pl", "version", "ver", "about", "icanhasbukkit", "help");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        if (player == null || plugin.isAdmin(player)) return;
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) return;
        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length == 0) return;
        String command = plain(parts[0]);
        if (!blocked().contains(command)) return;
        event.setCancelled(true);
        plugin.msg(player, plugin.string("messages.command-blocked",
                "&#F8BEFBДуэли &8» &cНеизвестная команда."));
    }

    /**
     * Страховка: даже если /duel перехватил кто-то другой (или команда пришла
     * как /minecraft:duel), команды арен обслуживаем сами. Родные
     * setspawn1/setspawn2/save в общем мире не работают вообще — они ищут
     * мир arena_template_<имя> и отвечают «Нужно быть в мире арены».
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onArenaCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player == null || !plugin.isAdmin(player)) return;
        if (plugin.sharedArenaWorld().isEmpty()) return;
        String message = event.getMessage();
        if (message == null || message.length() < 7 || message.charAt(0) != '/') return;
        String[] parts = message.substring(1).trim().split("\\s+");
        if (parts.length < 2) return;
        String command = plain(parts[0]);
        if (!command.equals("duel") && !command.equals("duelx")) return;
        if (!"arena".equals(plain(parts[1]))) return;
        if (parts.length > 2) {
            String action = plain(parts[2]);
            // rename и seticon не зависят от мира — пусть делает EpicDuels
            if (action.equals("rename") || action.equals("seticon")) return;
        }
        String[] rest = new String[Math.max(0, parts.length - 2)];
        System.arraycopy(parts, 2, rest, 0, rest.length);
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.arenaCommand(player, rest);
            }
        });
    }

    @EventHandler
    public void onSend(PlayerCommandSendEvent event) {
        if (!enabled() || !plugin.getConfig().getBoolean("command-guard.hide-tab", true)) return;
        Player player = event.getPlayer();
        if (player == null || plugin.isAdmin(player)) return;
        Set<String> hidden = hidden();
        try {
            event.getCommands().removeIf(name -> name != null && hidden.contains(plain(name)));
        } catch (UnsupportedOperationException error) {
            plugin.getLogger().warning("command-guard: список команд клиента только для чтения");
        }
    }
}
