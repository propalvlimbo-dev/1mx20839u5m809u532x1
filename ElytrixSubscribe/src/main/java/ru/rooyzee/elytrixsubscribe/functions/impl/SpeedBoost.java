package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.rooyzee.elytrixsubscribe.functions.Function;

import java.util.UUID;

public class SpeedBoost extends Function {
    private static final UUID MODIFIER_UUID = UUID.fromString("f8a3c1e2-0d4f-4e2f-8a1c-9b0d5e7f3a11");
    private static final String MODIFIER_NAME = "elytrix_speed_boost";
    private static final double BOOST = 0.10;

    public SpeedBoost() {
        super("speed", "Скорость");
    }

    @Override
    public void onEnable(Player player) {
        applyModifier(player);
        super.onEnable(player);
    }

    @Override
    public void onDisable(Player player) {
        removeModifier(player);
        super.onDisable(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeModifier(e.getPlayer());
    }

    private void applyModifier(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        removeModifier(player);
        attr.addModifier(new AttributeModifier(MODIFIER_UUID, MODIFIER_NAME, BOOST, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeModifier(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        attr.getModifiers().stream()
                .filter(m -> m.getUniqueId().equals(MODIFIER_UUID))
                .findFirst()
                .ifPresent(attr::removeModifier);
    }
}