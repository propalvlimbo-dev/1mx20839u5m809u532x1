package ru.rooyzee.elytrixsubscribe.utils;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.rooyzee.elytrixsubscribe.Main;

/**
 * Ввод текста в чате вместо таблички: игрок нажимает кнопку в меню, пишет
 * имя питомца в чат, сообщение не уходит в общий чат.
 *
 * Слушатель подключается только если сервер отдал AsyncChatEvent (Paper).
 * На чистом Spigot имя задаётся командой: /subscribe pet name <питомец> <имя>.
 */
public class ChatInput implements Listener {
    private final Main plugin;
    private final Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<UUID, Consumer<String>>();

    public ChatInput(Main plugin) {
        this.plugin = plugin;
    }

    /** Ждём строку от игрока. Повторный вызов заменяет прошлое ожидание. */
    public void request(Player player, String prompt, Consumer<String> callback) {
        if (player == null) return;
        if (!this.supported()) {
            this.plugin.msg(player, this.plugin.message("input-unsupported",
                    "&7● &fНапиши имя командой: &f/subscribe pet <питомец> name <имя>"));
            return;
        }
        this.waiting.put(player.getUniqueId(), callback);
        this.plugin.msg(player, prompt);
        this.plugin.msg(player, this.plugin.message("input-cancel",
                "&7● &fЧтобы отменить — напиши &fотмена&7."));
    }

    /** Есть ли на сервере Paper-событие чата. */
    public boolean supported() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    public void cancel(UUID uuid) {
        this.waiting.remove(uuid);
    }

    public boolean isWaiting(UUID uuid) {
        return this.waiting.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = this.waiting.remove(player.getUniqueId());
        if (callback == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
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
}
