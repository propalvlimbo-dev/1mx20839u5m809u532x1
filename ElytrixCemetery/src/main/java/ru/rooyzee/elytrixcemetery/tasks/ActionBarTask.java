package ru.rooyzee.elytrixcemetery.tasks;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBarTask extends BukkitRunnable implements Listener {

    private static final long DIG_PROGRESS_TIMEOUT_MILLIS = 3_000L;

    private final Main plugin;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DigStatus> diggingPlayers = new ConcurrentHashMap<>();

    private static final class DigStatus {
        private final int completedCycles;
        private final long lastActivityMillis;

        private DigStatus(int completedCycles, long lastActivityMillis) {
            this.completedCycles = completedCycles;
            this.lastActivityMillis = lastActivityMillis;
        }
    }

    public ActionBarTask(Main plugin) {
        this.plugin = plugin;
    }

    public void togglePlayer(Player player) {
        setPlayerEnabled(player, disabledPlayers.contains(player.getUniqueId()));
    }

    public void setPlayerEnabled(Player player, boolean enabled) {
        UUID playerId = player.getUniqueId();
        if (enabled) {
            if (disabledPlayers.remove(playerId)) {
                player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aActionbar включен!"));
            }
        } else if (disabledPlayers.add(playerId)) {
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cActionbar отключен!"));
        }
    }

    /** Updates the temporary digging bar without counting hits; callers pass completed cycles only. */
    public void showDigProgress(Player player, int completedCycles) {
        if (player == null) return;
        int completed = Math.max(0, Math.min(Grave.REQUIRED_DIG_CYCLES, completedCycles));
        DigStatus status = new DigStatus(completed, System.currentTimeMillis());
        diggingPlayers.put(player.getUniqueId(), status);
        if (!disabledPlayers.contains(player.getUniqueId())) sendDigProgress(player, status);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        disabledPlayers.remove(playerId);
        diggingPlayers.remove(playerId);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, DigStatus> entry : diggingPlayers.entrySet()) {
            if (now - entry.getValue().lastActivityMillis > DIG_PROGRESS_TIMEOUT_MILLIS) {
                diggingPlayers.remove(entry.getKey(), entry.getValue());
            }
        }

        Location center = plugin.getConfigManager().getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        int r = plugin.getConfigManager().getBoundaryRadius();
        int dug = plugin.getGraveManager().getDugCount();
        int total = plugin.getGraveManager().getTotalCount();
        int ghosts = plugin.getGhostController().getAliveCount();
        String time = plugin.getGraveManager().formatTime(plugin.getGraveManager().getRemainingRefillSeconds());

        String bar = ColorUtil.colorize("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» &#F8BEFBКладбище &8┃ &fРаскопано сейчас: &#F8BEFB" + dug + "&f из &#F8BEFB" + total
                + " &8┃ &fЖивых призраков: &#F8BEFB" + ghosts
                + " &8┃ &fДо закапывания могил: &#96FF91" + time);

        for (Player player : center.getWorld().getPlayers()) {
            UUID playerId = player.getUniqueId();
            DigStatus digStatus = diggingPlayers.get(playerId);
            if (digStatus != null) {
                if (now - digStatus.lastActivityMillis <= DIG_PROGRESS_TIMEOUT_MILLIS) {
                    // Digging progress takes priority over the regular cemetery status bar.
                    if (!disabledPlayers.contains(playerId)) sendDigProgress(player, digStatus);
                    continue;
                }
                diggingPlayers.remove(playerId, digStatus);
            }

            if (disabledPlayers.contains(playerId)) continue;
            Location playerLocation = player.getLocation();
            int dx = Math.abs(playerLocation.getBlockX() - center.getBlockX());
            int dz = Math.abs(playerLocation.getBlockZ() - center.getBlockZ());
            if (dx <= r + 15 && dz <= r + 15) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(bar));
            }
        }
    }

    private void sendDigProgress(Player player, DigStatus status) {
        int remaining = Grave.REQUIRED_DIG_CYCLES - status.completedCycles;
        String text = ColorUtil.colorize("&f☁ &#F8BEFBРаскопка могилы &8┃ &fОсталось циклов: &#F8BEFB"
                + remaining + " &8┃ &7Выполнено: " + status.completedCycles + "/" + Grave.REQUIRED_DIG_CYCLES);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }
}
