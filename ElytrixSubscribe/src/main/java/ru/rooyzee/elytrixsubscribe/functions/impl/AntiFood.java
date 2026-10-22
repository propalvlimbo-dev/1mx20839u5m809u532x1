package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;

/** Анти-голод: сытость не уменьшается (лечиться едой по-прежнему можно). */
public class AntiFood extends Function {

    public AntiFood() {
        super("antifood", "Анти-голод");
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!Main.getInstance().getSubManager().isFunctionEnabled(player, this)) return;
        if (event.getFoodLevel() < player.getFoodLevel()) event.setCancelled(true);
    }
}
