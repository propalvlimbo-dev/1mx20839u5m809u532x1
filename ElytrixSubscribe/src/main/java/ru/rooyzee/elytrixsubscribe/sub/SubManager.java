package ru.rooyzee.elytrixsubscribe.sub;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.events.PlayerGoneSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.PlayerReceivedSubscribeEvent;
import ru.rooyzee.elytrixsubscribe.events.state.ReceivedSubscribeState;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.utils.LPUtil;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

/**
 * Кто с подпиской и какие функции включены.
 *
 * Сама подписка (срок) живёт в {@link ru.rooyzee.elytrixsubscribe.service.SubService},
 * включённые функции — отдельная таблица: они должны переживать перезаход.
 */
public class SubManager implements Listener {
    private final Main plugin;
    private final Map<UUID, PlayerSub> playersHasSubscribe = new ConcurrentHashMap<UUID, PlayerSub>();
    /** Кэш до входа: какие функции включены (игрока ещё нет в сети). */
    private final Map<UUID, Set<String>> pending = new ConcurrentHashMap<UUID, Set<String>>();
    /** Кому уже сказали, что подписка кончилась — чтобы не спамить. */
    private final Set<UUID> expiredNotified = ConcurrentHashMap.newKeySet();

    public SubManager(Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------------- вход/выход

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent e) {
        UUID uuid = e.getUniqueId();
        this.plugin.getService().loadToCache(uuid);
        this.loadFunctions(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (!this.hasSub(player)) return;
        this.applyFunctions(player);
        this.givePermissions(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        this.playersHasSubscribe.remove(uuid);
        this.pending.remove(uuid);
        this.plugin.getService().removeFromCache(uuid);
    }

    // --------------------------------------------------------------- функции

    private void loadFunctions(UUID uuid) {
        Set<String> set = new LinkedHashSet<String>();
        for (String name : this.plugin.getStorage().getStringList("functions." + uuid)) {
            if (name != null && !name.isEmpty()) set.add(name);
        }
        this.pending.put(uuid, set);
    }

    private Set<String> functionsOf(UUID uuid) {
        Set<String> set = this.pending.get(uuid);
        if (set == null) {
            set = new LinkedHashSet<String>();
            this.pending.put(uuid, set);
        }
        return set;
    }

    public boolean isFunctionEnabled(Player player, Function function) {
        if (player == null || function == null) return false;
        if (!this.hasSub(player)) return false;
        return this.functionsOf(player.getUniqueId()).contains(function.getName());
    }

    /** Включить/выключить функцию с сохранением. false — нет подписки. */
    public boolean setFunction(Player player, Function function, boolean enabled) {
        if (player == null || function == null) return false;
        if (!this.hasSub(player)) return false;
        Set<String> set = this.functionsOf(player.getUniqueId());
        if (enabled == set.contains(function.getName())) return true;
        if (enabled) {
            set.add(function.getName());
        } else {
            set.remove(function.getName());
        }
        UUID uuid = player.getUniqueId();
        this.plugin.getStorage().setStringList("functions." + uuid, new ArrayList<String>(set));
        this.plugin.getStorage().saveAsync();
        return true;
    }

    /** Наложить эффекты всех включённых функций (вход, выдача подписки). */
    public void applyFunctions(Player player) {
        Set<String> set = this.functionsOf(player.getUniqueId());
        for (String name : new ArrayList<String>(set)) {
            Function function = this.plugin.getFunctionsManager().find(name);
            if (function == null) continue;
            try {
                function.apply(player);
            } catch (RuntimeException ignored) {
                // эффект не наложился — не мешаем остальным
            }
        }
    }

    /** Снять эффекты (выключение подписки, остановка сервера). */
    public void detachEffects(Player player) {
        Set<String> set = this.functionsOf(player.getUniqueId());
        for (String name : new ArrayList<String>(set)) {
            Function function = this.plugin.getFunctionsManager().find(name);
            if (function == null) continue;
            try {
                function.clear(player);
            } catch (RuntimeException ignored) {
                // уже снято или игрока нет — не важно
            }
        }
    }

    // -------------------------------------------------------------- подписка

    public boolean hasSub(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (!this.plugin.getService().hasSub(uuid)) return false;
        long timeToEnd = this.plugin.getService().getTimeToEnd(uuid);
        if (System.currentTimeMillis() >= timeToEnd) {
            this.expire(player);
            return false;
        }
        PlayerSub sub = this.playersHasSubscribe.get(uuid);
        if (sub == null) {
            sub = new PlayerSub(player, timeToEnd);
            this.playersHasSubscribe.put(uuid, sub);
        } else {
            sub.setTimeToEnd(timeToEnd);
        }
        return true;
    }

    /** Подписка кончилась: убрать права и эффекты, сказать один раз. */
    private void expire(Player player) {
        UUID uuid = player.getUniqueId();
        this.plugin.getService().remSub(player);
        this.playersHasSubscribe.remove(uuid);
        this.detachEffects(player);
        this.removePermissions(player);
        this.plugin.getPetManager().disableActive(player);
        if (this.expiredNotified.add(uuid)) {
            this.plugin.msg(player, this.plugin.message("subscribeHasExpired",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fСрок действия вашей подписки подошёл к концу."));
        }
    }

    /** Подписка выдана — снова включить всё, что было включено. */
    public void onReceived(Player player, long timeToEnd) {
        UUID uuid = player.getUniqueId();
        this.expiredNotified.remove(uuid);
        this.playersHasSubscribe.put(uuid, new PlayerSub(player, timeToEnd));
        this.givePermissions(player);
        this.applyFunctions(player);
        this.plugin.msg(player, this.plugin.message("playerTakeItSubscribe",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                        + "&fВам была успешно выдана подписка!"));
    }

    @EventHandler
    public void onReceivedSub(PlayerReceivedSubscribeEvent e) {
        if (e.getState() == ReceivedSubscribeState.CANCEL) return;
        this.onReceived(e.getPlayer(), e.getTimeToEnd());
    }

    @EventHandler
    public void onGoneSub(PlayerGoneSubscribeEvent e) {
        Player player = e.getPlayer();
        this.playersHasSubscribe.remove(player.getUniqueId());
        this.detachEffects(player);
        this.removePermissions(player);
    }

    /** Включённые функции уже пишутся сразу — метод для совместимости и onDisable. */
    public void saveAll() {
        this.playersHasSubscribe.clear();
    }

    public PlayerSub convertPlayer(Player player) throws ConvertPlayerException {
        if (!this.hasSub(player)) throw new ConvertPlayerException("No sub for " + player.getName());
        return this.playersHasSubscribe.get(player.getUniqueId());
    }

    // -------------------------------------------------------------- права LP

    private String[] permissions() {
        String raw = this.plugin.getConfig().getString("settings.permissions", "");
        List<String> out = new ArrayList<String>();
        for (String part : raw.split(";")) {
            String value = part.trim();
            if (!value.isEmpty()) out.add(value);
        }
        return out.toArray(new String[0]);
    }

    public void givePermissions(Player player) {
        String[] permissions = this.permissions();
        if (permissions.length == 0) return;
        LPUtil.addPermission(player, permissions);
    }

    public void removePermissions(Player player) {
        String[] permissions = this.permissions();
        if (permissions.length == 0) return;
        LPUtil.removePermissions(player, permissions);
    }

    /** Строка «осталось» для меню и плейсхолдеров. */
    public String timeLeft(Player player) {
        UUID uuid = player.getUniqueId();
        if (!this.plugin.getService().hasSub(uuid)) return "";
        long end = this.plugin.getService().getTimeToEnd(uuid);
        return Utils.formatRemaining(Long.valueOf(end));
    }
}
