package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.item.ItemRegistry;
import ru.rooyzee.elytrixitem.item.OffhandBonusItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Бонусы предметов во второй руке: тотемов и шаров.
 *
 * Бонусы ставит сам плагин, а не предмет, поэтому при снятии предмета
 * здоровье пересчитывается: шалкерский тотем реально забирает 2 сердца,
 * а сердце доктора после снятия уходит.
 */
public final class OffhandBonusListener implements Listener {

    private static final long PERIOD = 10L;
    private static final double EPSILON = 0.001D;

    private final Main plugin;
    private final NamespacedKey key;
    private final Map<UUID, Map<String, Applied>> appliedStats = new HashMap<>();
    private final Map<UUID, Map<PotionEffectType, Integer>> appliedEffects = new HashMap<>();
    private final Random random = new Random();

    public OffhandBonusListener(Main plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "custom_item_id");

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    refresh(player);
                }
            }
        }.runTaskTimer(plugin, 0L, PERIOD);
    }

    /** Пересчитать бонусы по предмету во второй руке. */
    public void refresh(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        OffhandBonusItem bonus = resolve(offhand);

        Map<String, Desired> stats = new LinkedHashMap<>();
        List<OffhandBonusItem.Effect> effects = new ArrayList<>();
        if (bonus != null) {
            for (OffhandBonusItem.Stat stat : bonus.getStats()) {
                stats.put(statKey(stat), new Desired(stat.getAttribute(),
                        stat.getOperation(), stat.getAmount()));
            }
            effects.addAll(bonus.getEffects(offhand));
        }

        applyStats(player, stats);
        applyEffects(player, effects);
    }

    // ---------- характеристики ----------

    private void applyStats(Player player, Map<String, Desired> desired) {
        Map<String, Applied> previous = appliedStats.computeIfAbsent(player.getUniqueId(),
                ignored -> new HashMap<>());

        double oldMax = maxHealth(player);
        boolean wasFull = player.getHealth() >= oldMax - EPSILON;

        for (Iterator<Map.Entry<String, Applied>> iterator = previous.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<String, Applied> entry = iterator.next();
            Applied applied = entry.getValue();
            Desired want = desired.get(entry.getKey());

            if (want != null && Math.abs(want.amount - applied.amount) < EPSILON) {
                continue;
            }

            remove(player, applied);
            iterator.remove();
        }

        for (Map.Entry<String, Desired> entry : desired.entrySet()) {
            if (previous.containsKey(entry.getKey())) {
                continue;
            }

            Desired want = entry.getValue();
            AttributeInstance instance = player.getAttribute(want.attribute);
            if (instance == null) {
                continue;
            }

            NamespacedKey modifierKey = new NamespacedKey(plugin, "offhand_" + entry.getKey());
            AttributeModifier modifier = new AttributeModifier(modifierKey, want.amount,
                    want.operation);
            for (AttributeModifier existing : new ArrayList<>(instance.getModifiers())) {
                if (existing.getKey().equals(modifierKey)) {
                    instance.removeModifier(existing);
                }
            }
            instance.addModifier(modifier);
            previous.put(entry.getKey(), new Applied(want.attribute, modifier, want.amount));
        }

        double newMax = maxHealth(player);
        double health = player.getHealth();

        if (newMax > oldMax && wasFull) {
            player.setHealth(newMax);
        } else if (health > newMax) {
            player.setHealth(newMax);
        }
    }

    private String statKey(OffhandBonusItem.Stat stat) {
        return stat.getAttribute().getKey().getKey().toLowerCase(Locale.ROOT) + "_"
                + stat.getOperation().name().toLowerCase(Locale.ROOT);
    }

    private void remove(Player player, Applied applied) {
        AttributeInstance instance = player.getAttribute(applied.attribute);
        if (instance != null) {
            instance.removeModifier(applied.modifier);
        }
    }

    private double maxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? player.getMaxHealth() : instance.getValue();
    }

    // ---------- эффекты ----------

    private void applyEffects(Player player, List<OffhandBonusItem.Effect> wanted) {
        Map<PotionEffectType, Integer> desired = new LinkedHashMap<>();
        for (OffhandBonusItem.Effect effect : wanted) {
            desired.merge(effect.getType(), effect.getLevel(), Math::max);
        }

        Map<PotionEffectType, Integer> previous = appliedEffects.computeIfAbsent(
                player.getUniqueId(), ignored -> new HashMap<>());

        for (Iterator<Map.Entry<PotionEffectType, Integer>> iterator = previous.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<PotionEffectType, Integer> entry = iterator.next();
            if (desired.containsKey(entry.getKey())) {
                continue;
            }

            PotionEffect current = player.getPotionEffect(entry.getKey());
            if (current != null && current.isInfinite()
                    && current.getAmplifier() == entry.getValue() - 1) {
                player.removePotionEffect(entry.getKey());
            }
            iterator.remove();
        }

        for (Map.Entry<PotionEffectType, Integer> entry : desired.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            int amplifier = entry.getValue() - 1;

            if (current != null) {
                boolean stronger = current.getAmplifier() > amplifier;
                boolean enough = current.getAmplifier() == amplifier && current.isInfinite();
                if (stronger || enough) {
                    continue;
                }
            }

            player.addPotionEffect(new PotionEffect(entry.getKey(), PotionEffect.INFINITE_DURATION,
                    amplifier, false, false, true), true);
        }

        previous.clear();
        previous.putAll(desired);
    }

    // ---------- минусы ----------

    /**
     * Минусы шара: срабатывают с шансом, когда владелец бьёт.
     *
     * Эффект вешается на владельца, огонь поджигает его же (и проходит сквозь
     * огнестойкость), а отражение наносит владельцу заданную долю финального урона.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || event.getEntity().equals(player)) {
            return;
        }

        OffhandBonusItem bonus = resolve(player.getInventory().getItemInOffHand());
        if (bonus == null || bonus.getDrawbacks().isEmpty()) {
            return;
        }

        for (OffhandBonusItem.Drawback drawback : bonus.getDrawbacks()) {
            if (random.nextDouble() >= drawback.getChance()) {
                continue;
            }

            switch (drawback.getKind()) {
                case EFFECT -> player.addPotionEffect(new PotionEffect(drawback.getType(),
                        drawback.getDurationTicks(), drawback.getLevel() - 1, false, true, true),
                        true);
                case FIRE -> ignite(player, drawback.getDurationTicks());
                case REFLECT -> {
                    double reflected = event.getFinalDamage() * drawback.getDamageMultiplier();
                    if (reflected > 0.0D) {
                        // MAGIC bypasses the holder's armor so the returned damage is not silently reduced again.
                        player.damage(reflected, DamageSource.builder(DamageType.MAGIC).build());
                    }
                }
            }
        }
    }

    /** Поджог владельца: обычный огонь, но огнестойкость его не спасает. */
    private void ignite(Player player, int ticks) {
        player.setFireTicks(Math.max(player.getFireTicks(), ticks));

        if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            player.damage(ticks / 20.0D);
        }
    }

    // ---------- пробуждение шаров ----------

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        ItemStack offhand = killer.getInventory().getItemInOffHand();
        RuneItem rune = runeOf(offhand);
        if (rune == null) {
            return;
        }

        int kills = rune.getKills(offhand);
        if (kills >= rune.getKillsRequired()) {
            return;
        }

        int next = kills + 1;
        killer.getInventory().setItemInOffHand(rune.build(next));

        if (next >= rune.getKillsRequired()) {
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.4F);
            plugin.getMessages().send(killer, "rune-awakened", "{item}", rune.getItemName());
        } else {
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.6F);
        }

        refresh(killer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        Map<String, Applied> stats = appliedStats.remove(player.getUniqueId());
        if (stats != null) {
            for (Applied applied : stats.values()) {
                remove(player, applied);
            }
        }

        Map<PotionEffectType, Integer> effects = appliedEffects.remove(player.getUniqueId());
        if (effects != null) {
            for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
                PotionEffect current = player.getPotionEffect(entry.getKey());
                if (current != null && current.isInfinite()
                        && current.getAmplifier() == entry.getValue() - 1) {
                    player.removePotionEffect(entry.getKey());
                }
            }
        }

    }

    // ---------- разбор предмета ----------

    private OffhandBonusItem resolve(ItemStack item) {
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
        return custom instanceof OffhandBonusItem bonus ? bonus : null;
    }

    private RuneItem runeOf(ItemStack item) {
        OffhandBonusItem bonus = resolve(item);
        return bonus instanceof RuneItem rune ? rune : null;
    }

    private static final class Desired {

        private final Attribute attribute;
        private final AttributeModifier.Operation operation;
        private final double amount;

        private Desired(Attribute attribute, AttributeModifier.Operation operation, double amount) {
            this.attribute = attribute;
            this.operation = operation;
            this.amount = amount;
        }
    }

    private static final class Applied {

        private final Attribute attribute;
        private final AttributeModifier modifier;
        private final double amount;

        private Applied(Attribute attribute, AttributeModifier modifier, double amount) {
            this.attribute = attribute;
            this.modifier = modifier;
            this.amount = amount;
        }
    }
}
