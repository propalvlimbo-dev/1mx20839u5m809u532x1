package ru.rooyzee.elytrixsubscribe.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import ru.rooyzee.elytrixsubscribe.Main;

/**
 * Ввод имени питомца прямо в чат: нажал кнопку, написал строку — она стала
 * именем. Цветокоды и hex сохраняются (&#F8BEFB, &c, #F8BEFB).
 *
 * Слушаем два события сразу: старое AsyncPlayerChatEvent (его видят BMenu и
 * чат-плагины) и Paper-овское AsyncChatEvent. Кто первый поймал — тот и
 * забирает строку, второе событие уже ничего не находит.
 */
public class ChatInput implements Listener {
    private final Main plugin;
    private final Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<UUID, Consumer<String>>();
    private volatile boolean registered;

    public ChatInput(Main plugin) {
        this.plugin = plugin;
    }

    public void markRegistered() {
        this.registered = true;
    }

    /** Дополнительно подписаться на Paper-событие чата, если оно есть. */
    public void registerPaper(PluginManager manager) {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            manager.registerEvents(new PaperChat(this.plugin), this.plugin);
        } catch (Throwable ignored) {
            // не Paper — хватит старого события
        }
    }

    /** Ждём строку от игрока. Повторный вызов заменяет прошлое ожидание. */
    public void request(Player player, String prompt, Consumer<String> callback) {
        if (player == null) return;
        if (!this.registered) {
            this.plugin.msg(player, this.plugin.message("input-unsupported",
                    "&7● &fНапиши имя командой: &f/subscribe pet <питомец> name <имя>"));
            return;
        }
        this.waiting.put(player.getUniqueId(), callback);
        this.plugin.msg(player, "");
        this.plugin.msg(player, prompt);
        this.plugin.msg(player, this.plugin.message("input-colors",
                "&7● &fМожно цветокоды: &f&a &f&c &f&#F8BEFB &7и hex &f#F8BEFB"));
        this.plugin.msg(player, this.plugin.message("input-cancel",
                "&7● &fЧтобы отменить — напиши &fотмена&7."));
        this.plugin.msg(player, "");
        try {
            player.sendTitle(Utils.color("&fИмя питомца"),
                    Utils.color("&7напиши в чат · отмена — «отмена»"), 5, 100, 10);
        } catch (Throwable ignored) {
            // старые ядра без title — хватит сообщения в чате
        }
    }

    public boolean supported() {
        return this.registered;
    }

    public void cancel(UUID uuid) {
        this.waiting.remove(uuid);
    }

    public boolean isWaiting(UUID uuid) {
        return this.waiting.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = this.waiting.remove(player.getUniqueId());
        if (callback == null) return;
        event.setCancelled(true);
        String text = event.getMessage() == null ? "" : event.getMessage().trim();
        this.finish(player, text, callback);
    }

    private void finish(Player player, String text, Consumer<String> callback) {
        if (text.isEmpty() || text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(this.plugin, () ->
                    this.plugin.msg(player, this.plugin.message("input-aborted", "&7Ввод отменён.")));
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> callback.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.waiting.remove(event.getPlayer().getUniqueId());
    }

    /** Paper-событие чата (компоненты Adventure). */
    public static final class PaperChat implements Listener {
        private final Main plugin;

        PaperChat(Main plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
            Player player = event.getPlayer();
            ChatInput input = this.plugin.getChatInput();
            Consumer<String> callback = input == null ? null : input.waiting.remove(player.getUniqueId());
            if (callback == null) return;
            event.setCancelled(true);
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(event.message()).trim();
            input.finish(player, text, callback);
        }
    }
}
