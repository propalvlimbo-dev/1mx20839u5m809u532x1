package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;

public class AntiFood extends Function {
    public AntiFood() {
        super("antifood", "Анти-голод");
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();
            // FIX: Используем быстрый метод без исключений
            if (Main.getInstance().getSubManager().isFunctionEnabled(player, this)) {
                e.setCancelled(true);
            }
        }
    }
}