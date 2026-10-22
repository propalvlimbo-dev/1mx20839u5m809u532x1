package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTotem extends Function {
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TIME = 30000L;

    public AutoTotem() {
        super("auto-totem", "Авто-тотем");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player player = (Player) e.getEntity();
        if (!Main.getInstance().getSubManager().isFunctionEnabled(player, this)) return;

        double finalDamage = e.getFinalDamage();
        if (finalDamage < player.getHealth()) return;
        if (!consumeTotem(player)) return;

        long now = System.currentTimeMillis();
        long lastUsed = this.cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUsed < COOLDOWN_TIME) return;

        e.setCancelled(true);
        useTotemEffect(player);
        this.cooldowns.put(player.getUniqueId(), now);
        player.sendMessage(Utils.color("&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fАвто-тотем сработал! Перезарядка: &#F8BEFB30 сек."));
    }

    private boolean consumeTotem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().setItem(i, item);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private void useTotemEffect(Player player) {
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
    }
}