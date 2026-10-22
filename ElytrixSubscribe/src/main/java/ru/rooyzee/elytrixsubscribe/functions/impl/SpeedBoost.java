package ru.rooyzee.elytrixsubscribe.functions.impl;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.Function;

/**
 * Скорость: +20% к базовой скорости, как в старой версии.
 *
 * Модификатор на 1.21 хранится по NamespacedKey, поэтому его можно наложить
 * заново после перезахода и не получить второй такой же.
 */
public class SpeedBoost extends Function {
    private static final double BOOST = 0.20;

    public SpeedBoost() {
        super("speed", "Скорость");
    }

    @Override
    public void apply(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        this.clear(player);
        attribute.addModifier(new AttributeModifier(this.key(),
                BOOST, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    @Override
    public void clear(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        NamespacedKey key = this.key();
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                attribute.removeModifier(modifier);
                return;
            }
        }
    }

    private NamespacedKey key() {
        return new NamespacedKey(Main.getInstance(), "elytrix_speed_boost");
    }
}
