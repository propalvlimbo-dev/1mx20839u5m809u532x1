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

    /** Минусы предмета: срабатывают с шансом, когда игрок бьёт. */
    default List<Drawback> getDrawbacks() {
        return List.of();
    }

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

    /** Минус: с шансом вешает на владельца неприятный эффект после удара. */
    final class Drawback {

        private final PotionEffectType type;
        private final int level;
        private final int durationTicks;
        private final double chance;
        private final String label;

        public Drawback(PotionEffectType type, int level, int durationTicks, double chance,
                        String label) {
            this.type = type;
            this.level = level;
            this.durationTicks = durationTicks;
            this.chance = chance;
            this.label = label;
        }

        public PotionEffectType getType() {
            return type;
        }

        public int getLevel() {
            return level;
        }

        public int getDurationTicks() {
            return durationTicks;
        }

        /** Шанс от 0 до 1. */
        public double getChance() {
            return chance;
        }

        /** «5% шанс отравления после удара» — как минус пишется в лоре. */
        public String describe() {
            return percent(chance) + " шанс " + label + " после удара";
        }

        private static String percent(double value) {
            double percent = value * 100.0D;
            if (percent == Math.rint(percent)) {
                return String.valueOf((long) percent) + "%";
            }
            return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
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
