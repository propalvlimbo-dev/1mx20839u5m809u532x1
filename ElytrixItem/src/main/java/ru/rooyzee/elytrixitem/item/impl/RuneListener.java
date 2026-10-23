package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.item.ItemRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Эффекты шаров: пока шар лежит в инвентаре, игрок получает его эффекты.
 *
 * Эффекты обновляются раз в секунду и снимаются, когда шар убран.
 */
public final class RuneListener implements Listener {

    private static final int DURATION_TICKS = 60;
    private static final int MAX_OWNED_DURATION = 70;

    private final Main plugin;
    private final NamespacedKey key;
    private final Map<UUID, Map<PotionEffectType, Integer>> applied = new HashMap<>();

    public RuneListener(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "custom_item_id");

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<PotionEffectType, Integer> best = new HashMap<>();

            for (ItemStack item : player.getInventory().getContents()) {
                RuneItem rune = runeFrom(item);
                if (rune == null) {
                    continue;
                }

                for (RuneItem.Effect effect : rune.getEffects()) {
                    best.merge(effect.getType(), effect.getLevel(), Math::max);
                }
            }

            apply(player, best);
        }
    }

    private void apply(Player player, Map<PotionEffectType, Integer> best) {
        Map<PotionEffectType, Integer> previous = applied.getOrDefault(player.getUniqueId(),
                new HashMap<>());

        for (Map.Entry<PotionEffectType, Integer> entry : previous.entrySet()) {
            if (best.containsKey(entry.getKey())) {
                continue;
            }

            PotionEffect current = player.getPotionEffect(entry.getKey());
            if (current != null && current.getAmplifier() == entry.getValue() - 1
                    && current.getDuration() <= MAX_OWNED_DURATION) {
                player.removePotionEffect(entry.getKey());
            }
        }

        for (Map.Entry<PotionEffectType, Integer> entry : best.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            int amplifier = entry.getValue() - 1;

            if (current != null && (current.getAmplifier() > amplifier
                    || (current.getAmplifier() == amplifier && current.getDuration() > MAX_OWNED_DURATION))) {
                continue;
            }

            player.addPotionEffect(new PotionEffect(entry.getKey(), DURATION_TICKS,
                    amplifier, false, false, true));
        }

        applied.put(player.getUniqueId(), best);
    }

    private RuneItem runeFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        String id = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }

        ItemRegistry registry = plugin.getItemRegistry();
        CustomItem custom = registry.get(id);
        return custom instanceof RuneItem rune ? rune : null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
    }
}
