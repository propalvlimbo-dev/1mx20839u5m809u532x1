package ru.rooyzee.elytrixitem.item.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.util.ColorUtil;

/** Handles the two-minute target display and persistent two-hour cooldown. */
public final class TruthCompassListener implements Listener {

    private static final long TRACKING_DURATION_MS = 2L * 60L * 1000L;
    private static final long COOLDOWN_DURATION_MS = 2L * 60L * 60L * 1000L;
    private static final long DISPLAY_PERIOD_TICKS = 20L;

    private final Main plugin;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey cooldownKey;
    private final Map<UUID, TrackingTarget> activeTargets = new HashMap<>();

    public TruthCompassListener(Main plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "custom_item_id");
        this.cooldownKey = new NamespacedKey(plugin, "truth_compass_cooldown_until");
        startActionBarTask();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCompassUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = event.getPlayer().getInventory().getItemInMainHand();
        }
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return;
        }
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(itemIdKey, PersistentDataType.STRING);
        if (!TruthCompass.ID.equals(itemId)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long cooldownUntil = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        if (cooldownUntil != null && cooldownUntil > now) {
            sendActionBar(player, "Компас Правды перезаряжается: " + formatDuration(cooldownUntil - now));
            return;
        }

        TrackingTarget target = findRandomDungeon();
        if (target == null) {
            plugin.getMessages().send(player, "truth-compass-no-dungeons");
            return;
        }

        player.getPersistentDataContainer().set(
                cooldownKey, PersistentDataType.LONG, now + COOLDOWN_DURATION_MS);
        activeTargets.put(player.getUniqueId(), new TrackingTarget(
                target.worldName(), target.x(), target.y(), target.z(), now + TRACKING_DURATION_MS));
        plugin.getMessages().send(player, "truth-compass-activated");
        displayTarget(player, activeTargets.get(player.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeTargets.remove(event.getPlayer().getUniqueId());
    }

    private void startActionBarTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, TrackingTarget>> iterator = activeTargets.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, TrackingTarget> entry = iterator.next();
                    Player player = Bukkit.getPlayer(entry.getKey());
                    TrackingTarget target = entry.getValue();
                    if (player == null || !player.isOnline() || target.expiresAtMs() <= now) {
                        iterator.remove();
                        continue;
                    }
                    displayTarget(player, target);
                }
            }
        }.runTaskTimer(plugin, 0L, DISPLAY_PERIOD_TICKS);
    }

    private void displayTarget(Player player, TrackingTarget target) {
        sendActionBar(player, "Компас Правды → " + target.worldName()
                + "  X: " + target.x() + "  Y: " + target.y() + "  Z: " + target.z());
    }

    private static void sendActionBar(Player player, String text) {
        String brand = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» &f";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ColorUtil.color(brand + text)));
    }

    /**
     * ElytrixDungeons is an optional companion plugin. Reflection keeps this plugin
     * buildable and loadable when ElytrixDungeons is not installed.
     */
    private TrackingTarget findRandomDungeon() {
        Plugin dungeonsPlugin = Bukkit.getPluginManager().getPlugin("ElytrixDungeons");
        if (dungeonsPlugin == null) {
            return null;
        }

        try {
            Object manager = dungeonsPlugin.getClass().getMethod("getDungeonManager").invoke(dungeonsPlugin);
            if (manager == null || Boolean.TRUE.equals(invokeNoArgs(manager, "isHiddenFromWorld"))) {
                return null;
            }
            Object result = manager.getClass().getMethod("getAllDungeons").invoke(manager);
            if (!(result instanceof Iterable<?> dungeons)) {
                return null;
            }

            List<TrackingTarget> candidates = new ArrayList<>();
            for (Object dungeon : dungeons) {
                if (dungeon == null) {
                    continue;
                }
                Object status = invokeNoArgs(dungeon, "getStatus");
                if (status != null && "REGENERATING".equals(status.toString())) {
                    continue;
                }
                Object position = invokeNoArgs(dungeon, "getEntrancePos");
                if (position == null) {
                    position = invokeNoArgs(dungeon, "getOrigin");
                }
                if (position == null) {
                    continue;
                }

                Object worldName = invokeNoArgs(position, "worldName");
                Object x = invokeNoArgs(position, "x");
                Object y = invokeNoArgs(position, "y");
                Object z = invokeNoArgs(position, "z");
                if (worldName instanceof String world && x instanceof Number blockX
                        && y instanceof Number blockY && z instanceof Number blockZ) {
                    candidates.add(new TrackingTarget(world, blockX.intValue(), blockY.intValue(),
                            blockZ.intValue(), 0L));
                }
            }
            return candidates.isEmpty()
                    ? null
                    : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().severe("Не удалось получить список данжей для Компаса Правды: "
                    + exception.getMessage());
            return null;
        }
    }

    private static Object invokeNoArgs(Object instance, String methodName) throws ReflectiveOperationException {
        Method method = instance.getClass().getMethod(methodName);
        return method.invoke(instance);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return String.format("%dч %02dм", hours, minutes);
        }
        return String.format("%02dм %02dс", minutes, remainingSeconds);
    }

    private record TrackingTarget(String worldName, int x, int y, int z, long expiresAtMs) {}
}
