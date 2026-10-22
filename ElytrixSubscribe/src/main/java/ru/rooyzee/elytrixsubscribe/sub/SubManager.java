package ru.rooyzee.elytrixsubscribe.sub;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.events.PlayerGoneSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.PlayerReceivedSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.state.ReceivedSubscribeState;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.utils.LPUtil;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

import java.util.*;

public class SubManager implements Listener {
    private final Map<UUID, PlayerSub> playersHasSubscribe = new HashMap<>();
    private final Main plugin;

    public SubManager(Plugin plugin) {
        this.plugin = (Main) plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent e) {
        this.plugin.getService().loadToCache(e.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        this.playersHasSubscribe.remove(uuid);
        this.plugin.getService().removeFromCache(uuid);
    }

    @EventHandler
    public void onReceivedSub(PlayerReceivedSubscribeEvent e) {
        ReceivedSubscribeState state = e.getState();
        if (!state.equals(ReceivedSubscribeState.CANCEL)) {
            UUID uuid = e.getPlayer().getUniqueId();
            this.playersHasSubscribe.put(uuid, new PlayerSub(e.getPlayer(), e.getTimeToEnd()));

            String[] permissions = this.plugin.getConfig().getString("settings.permissions").split(";");
            LPUtil.addPermission(e.getPlayer(), permissions);
            e.getPlayer().sendMessage(Utils.color(this.plugin.getConfig().getString("messages.playerTakeItSubscribe")));
        }
    }

    @EventHandler
    public void onGoneSub(PlayerGoneSubscribeEvent e) {
        Player player = e.getPlayer();
        PlayerSub playerSub = this.playersHasSubscribe.remove(player.getUniqueId());

        if (playerSub != null) {
            List<Function> functionsCopy = new ArrayList<>(playerSub.getEnabledFunctions());
            for (Function function : functionsCopy) {
                try {
                    function.onDisable(player);
                } catch (Exception ignored) {}
            }
        }

        String[] permissions = this.plugin.getConfig().getString("settings.permissions").split(";");
        LPUtil.removePermissions(player, permissions);
        player.sendMessage(Utils.color(this.plugin.getConfig().getString("messages.subscribeHasExpired")));
    }

    public boolean hasSub(Player player) {
        UUID uuid = player.getUniqueId();
        boolean isCached = this.plugin.getService().hasSub(uuid);
        if (isCached) {
            long timeToEnd = this.plugin.getService().getTimeToEnd(uuid);
            if (System.currentTimeMillis() >= timeToEnd) {
                this.plugin.getService().remSub(player);
                return false;
            }
            if (!this.playersHasSubscribe.containsKey(uuid)) {
                this.playersHasSubscribe.put(uuid, new PlayerSub(player, timeToEnd));
            }
            return true;
        }
        return false;
    }

    // FIX: Метод для быстрой проверки без генерации исключений (для событий урона и т.д.)
    public boolean isFunctionEnabled(Player player, Function function) {
        if (!hasSub(player)) return false;
        PlayerSub sub = this.playersHasSubscribe.get(player.getUniqueId());
        return sub != null && sub.getEnabledFunctions().contains(function);
    }

    public PlayerSub convertPlayer(Player player) throws ConvertPlayerException {
        if (!this.hasSub(player)) {
            throw new ConvertPlayerException("No sub for " + player.getName());
        }
        PlayerSub sub = this.playersHasSubscribe.get(player.getUniqueId());
        if (sub == null) {
            throw new ConvertPlayerException("PlayerSub missing in map for " + player.getName());
        }
        return sub;
    }
}