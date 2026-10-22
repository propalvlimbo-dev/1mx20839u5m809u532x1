package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;

public class AntiFall extends Function {
    public AntiFall() {
        super("anti-fall", "Анти-падение");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();
            // FIX: Используем быстрый метод без исключений
            if (e.getCause() == DamageCause.FALL && Main.getInstance().getSubManager().isFunctionEnabled(player, this)) {
                e.setCancelled(true);
            }
        }
    }
}