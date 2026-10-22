package ru.elytrix.duelsbridge;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Перки китов: эффекты, которые выдаются на бой и снимаются после него.
 *
 * EpicDuels умеет только предметы, поэтому эффекты накладывает мост: как
 * только игрока телепортирует на арену (мир arena_instance_*), он получает
 * баффы своего кита; при возврате в лобби они снимаются.
 *
 * Настраивается в config.yml, раздел buffs: список «ЭФФЕКТ:уровень» на кит.
 */
final class Effects implements Listener {

    /** Если в конфиге ничего нет — работают эти перки. */
    private static final Map<String, String[]> DEFAULTS = new HashMap<String, String[]>();

    /**
     * Перки 2.0: у каждого кита своя роль, а не «скорость всем».
     *   Классика — ровный бой; Гаппли — живучесть; Сумо — мобильность;
     *   Лучник и Тризуб — дистанция; Булава — вертикаль; Незерит — танк.
     */
    static {
        DEFAULTS.put("классика", new String[]{"SPEED:1"});
        DEFAULTS.put("ухк", new String[]{"SPEED:1"});
        DEFAULTS.put("гаппли", new String[]{"ABSORPTION:1"});
        DEFAULTS.put("сумо", new String[]{"SPEED:2", "JUMP_BOOST:1"});
        DEFAULTS.put("лучник", new String[]{"SPEED:2"});
        DEFAULTS.put("булава", new String[]{"SPEED:1"});
        DEFAULTS.put("топор", new String[]{"SPEED:1"});
        DEFAULTS.put("тризуб", new String[]{"SPEED:2"});
        DEFAULTS.put("арбалет", new String[]{"SPEED:1"});
        DEFAULTS.put("незерит", new String[]{"RESISTANCE:1"});
    }

    private final ElytrixDuelsBridge plugin;
    private final Duels duels;
    private final Map<UUID, Set<PotionEffectType>> applied = new HashMap<UUID, Set<PotionEffectType>>();

    Effects(ElytrixDuelsBridge plugin, Duels duels) {
        this.plugin = plugin;
        this.duels = duels;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("buffs.enabled", true);
    }

    private int durationTicks() {
        return plugin.getConfig().getInt("buffs.duration-seconds", 900) * 20;
    }

    /** Вошёл на арену — выдаём перки; вернулся в лобби — снимаем. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled()) return;
        final Player player = event.getPlayer();
        if (player == null) return;
        Location to = event.getTo();
        Location from = event.getFrom();
        String toWorld = to == null || to.getWorld() == null ? "" : to.getWorld().getName();
        String fromWorld = from == null || from.getWorld() == null ? "" : from.getWorld().getName();

        if (toWorld.startsWith("arena_instance_")) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    apply(player);
                }
            });
            return;
        }
        if (fromWorld.startsWith("arena_instance_") && plugin.getConfig().getBoolean("buffs.clear-on-leave", true)) {
            clear(player);
        }
    }

    /**
     * Перк кита «Булава»: урон от падения на арене не проходит —
     * иначе булава бьёт по себе сильнее, чем по сопернику.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!enabled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        String kit = duels.kitNameOf(player.getUniqueId());
        if (kit == null) return;
        String low = kit.toLowerCase(Locale.ROOT);
        if (low.equals("булава") || low.equals("mace")) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) applied.remove(event.getPlayer().getUniqueId());
    }

    private void apply(Player player) {
        if (player == null || !player.isOnline()) return;
        String kit = duels.kitNameOf(player.getUniqueId());
        if (kit == null) return;
        List<String> specs = plugin.getConfig().getStringList("buffs.per-kit." + kit);
        if (specs == null || specs.isEmpty()) {
            String[] defaults = DEFAULTS.get(kit.toLowerCase(Locale.ROOT));
            if (defaults == null) return;
            specs = java.util.Arrays.asList(defaults);
        }
        Set<PotionEffectType> types = applied.computeIfAbsent(player.getUniqueId(),
                key -> new LinkedHashSet<PotionEffectType>());
        int ticks = durationTicks();
        for (String spec : specs) {
            if (spec == null || spec.trim().isEmpty()) continue;
            String[] parts = spec.split(":");
            String name = parts[0].trim().toUpperCase(Locale.ROOT);
            int level = 1;
            if (parts.length > 1) {
                try {
                    level = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    level = 1;
                }
            }
            PotionEffectType type = type(name);
            if (type == null) {
                plugin.getLogger().warning("Перки: неизвестный эффект " + name + " у кита " + kit);
                continue;
            }
            player.addPotionEffect(new PotionEffect(type, ticks, level - 1, false, false, true));
            types.add(type);
        }
    }

    private void clear(Player player) {
        Set<PotionEffectType> types = applied.remove(player.getUniqueId());
        if (types == null) return;
        for (PotionEffectType type : types) player.removePotionEffect(type);
    }

    private static PotionEffectType type(String name) {
        try {
            return PotionEffectType.getByName(name);
        } catch (Throwable error) {
            return null;
        }
    }
}
