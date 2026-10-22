package ru.rooyzee.elytrixsubscribe.functions.impl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;

/**
 * Авто-тотем: при смертельном ударе тратит тотем из инвентаря и лечит.
 *
 * Перезарядка 30 секунд, и тотем списывается только когда он реально
 * сработал: раньше предмет исчезал даже на перезарядке.
 */
public class AutoTotem extends Function {
    private static final long COOLDOWN = 30000L;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();

    public AutoTotem() {
        super("auto-totem", "Авто-тотем");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!Main.getInstance().getSubManager().isFunctionEnabled(player, this)) return;

        double damage = event.getFinalDamage();
        if (damage < player.getHealth()) return;

        long now = System.currentTimeMillis();
        Long last = this.cooldowns.get(player.getUniqueId());
        if (last != null && now - last.longValue() < COOLDOWN) return;
        if (!this.hasTotem(player)) return;
        if (!this.consumeTotem(player)) return;

        event.setCancelled(true);
        this.heal(player);
        this.cooldowns.put(player.getUniqueId(), Long.valueOf(now));
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
        Main.getInstance().msg(player, Main.getInstance().message("auto-totem",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                        + "&fАвто-тотем сработал! Перезарядка: &#F8BEFB30 сек."));
    }

    private boolean hasTotem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) return true;
        }
        return false;
    }

    private boolean consumeTotem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) continue;
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(slot, item);
            } else {
                player.getInventory().setItem(slot, null);
            }
            return true;
        }
        return false;
    }

    private void heal(Player player) {
        double max = 20.0;
        try {
            max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        } catch (RuntimeException ignored) {
        }
        player.setHealth(Math.min(max, 20.0));
        player.setFoodLevel(20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
    }
}
