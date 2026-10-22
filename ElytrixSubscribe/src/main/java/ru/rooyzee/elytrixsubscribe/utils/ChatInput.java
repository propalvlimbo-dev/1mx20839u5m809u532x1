package ru.rooyzee.elytrixsubscribe.utils;

import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
 * Если чат перехватывает другой плагин, имя всегда можно задать командой:
 * /subscribe pet <питомец> name <имя>.
 */
public class ChatInput implements Listener {
    private final Main plugin;
    private final Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<UUID, Consumer<String>>();
    /** Слушатель реально зарегистрирован (на Paper всегда да). */
    private volatile boolean registered;

    public ChatInput(Main plugin) {
        this.plugin = plugin;
    }

    public void markRegistered() {
        this.registered = true;
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
        this.plugin.msg(player, "");
        this.plugin.msg(player, prompt);
        this.plugin.msg(player, this.plugin.message("input-cancel",
                "&7● &fЧтобы отменить — напиши &fотмена&7."));
        this.plugin.msg(player, "");
        try {
            player.sendTitle(Utils.color("&fИмя питомца"),
                    Utils.color("&7напиши в чат · отмена — «отмена»"), 5, 100, 10);
        } catch (Throwable ignored) {
            // старые ядра без title — хватит сообщения в чате
        }
        this.plugin.getLogger().info("Жду имя питомца в чате от " + player.getName());
    }

    /** Работает ли ввод в чате: слушатель зарегистрирован. */
    public boolean supported() {
        return this.registered;
    }

    public void cancel(UUID uuid) {
        this.waiting.remove(uuid);
    }

    public boolean isWaiting(UUID uuid) {
        return this.waiting.containsKey(uuid);
    }

    /**
     * Ловим обычный чат. Именно AsyncPlayerChatEvent, а не Paper-событие:
     * BMenu и большинство чат-плагинов слушают его, поэтому он гарантированно
     * приходит на любой сборке (на Paper тоже).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = this.waiting.remove(player.getUniqueId());
        if (callback == null) return;
        event.setCancelled(true);
        String text = event.getMessage() == null ? "" : event.getMessage().trim();
        this.plugin.getLogger().info("Имя из чата от " + player.getName() + ": " + text);
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
