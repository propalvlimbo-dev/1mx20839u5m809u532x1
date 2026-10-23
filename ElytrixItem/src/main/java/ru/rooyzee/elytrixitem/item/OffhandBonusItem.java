package ru.rooyzee.elytrixitem.item;

import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Предмет, который работает только во второй руке и даёт бонусы игроку.
 *
 * Бонусы выдаёт не сам предмет, а OffhandBonusListener: так проще
 * пересчитывать здоровье, когда предмет убрали.
 */
public interface OffhandBonusItem extends CustomItem {

    List<Stat> getStats();

    /** Эффекты, которые предмет даёт прямо сейчас (у шаров — только после пробуждения). */
    List<Effect> getEffects(ItemStack item);

    /** Бонус к характеристике; отрицательное значение — штраф. */
    final class Stat {

        private final Attribute attribute;
        private final double amount;
        private final String label;

        public Stat(Attribute attribute, double amount, String label) {
            this.attribute = attribute;
            this.amount = amount;
            this.label = number(Math.abs(amount)) + " " + label;
        }

        public Attribute getAttribute() {
            return attribute;
        }

        public double getAmount() {
            return amount;
        }

        public String getLabel() {
            return label;
        }

        private static String number(double value) {
            if (value == Math.rint(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
    }

    /** Эффект зелья; level — уровень как в игре (I, II, III). */
    final class Effect {

        private final PotionEffectType type;
        private final int level;
        private final String label;

        public Effect(PotionEffectType type, int level, String label) {
            this.type = type;
            this.level = level;
            this.label = label;
        }

        public PotionEffectType getType() {
            return type;
        }

        public int getLevel() {
            return level;
        }

        public String getLabel() {
            return label;
        }

        /** «Скорость I» — как эффект пишется в лоре. */
        public String describe() {
            return label + " " + roman(level);
        }
    }

    static String roman(int value) {
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (value >= 1 && value <= 10) {
            return ones[value];
        }
        return String.valueOf(value);
    }
}
