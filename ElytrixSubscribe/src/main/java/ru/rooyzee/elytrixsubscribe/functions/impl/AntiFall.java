package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;

/** Анти-падение: урон от падения не проходит. */
public class AntiFall extends Function {

    public AntiFall() {
        super("anti-fall", "Анти-падение");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Player player = (Player) event.getEntity();
        if (Main.getInstance().getSubManager().isFunctionEnabled(player, this)) {
            event.setCancelled(true);
        }
    }
}
