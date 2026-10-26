package ru.rooyzee.elytrixitem.item;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
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
        private final AttributeModifier.Operation operation;
        private final String label;

        public Stat(Attribute attribute, double amount, String label) {
            this(attribute, amount, AttributeModifier.Operation.ADD_NUMBER, label);
        }

        private Stat(Attribute attribute, double amount, AttributeModifier.Operation operation,
                     String label) {
            this.attribute = attribute;
            this.amount = amount;
            this.operation = operation;
            this.label = display(amount, operation, label);
        }

        /** Штраф или бонус в процентах: -0.3 — это «-30% скорости». */
        public static Stat percent(Attribute attribute, double amount, String label) {
            return new Stat(attribute, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1, label);
        }

        public Attribute getAttribute() {
            return attribute;
        }

        public double getAmount() {
            return amount;
        }

        public AttributeModifier.Operation getOperation() {
            return operation;
        }

        public String getLabel() {
            return label;
        }

        private static String display(double amount, AttributeModifier.Operation operation,
                                      String label) {
            double value = Math.abs(amount);
            if (operation == AttributeModifier.Operation.ADD_NUMBER) {
                return number(value) + " " + label;
            }
            return number(Math.round(value * 1000.0D) / 10.0D) + "% " + label;
        }

        private static String number(double value) {
            if (value == Math.rint(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
    }

    /** Минус: срабатывает с шансом, когда владелец бьёт. */
    final class Drawback {

        public enum Kind {
            /** Неприятный эффект на владельца. */
            EFFECT,
            /** Владелец загорается: огонь проходит сквозь огнестойкость. */
            FIRE,
            /** Урон удара возвращается владельцу. */
            REFLECT
        }

        private final Kind kind;
        private final PotionEffectType type;
        private final int level;
        private final int durationTicks;
        private final double chance;
        private final double damageMultiplier;
        private final String label;

        public Drawback(PotionEffectType type, int level, int durationTicks, double chance,
                        String label) {
            this(Kind.EFFECT, type, level, durationTicks, chance, 1.0D, label);
        }

        private Drawback(Kind kind, PotionEffectType type, int level, int durationTicks,
                         double chance, double damageMultiplier, String label) {
            this.kind = kind;
            this.type = type;
            this.level = level;
            this.durationTicks = durationTicks;
            this.chance = chance;
            this.damageMultiplier = damageMultiplier;
            this.label = label;
        }

        public static Drawback fire(int durationTicks, double chance) {
            return new Drawback(Kind.FIRE, null, 0, durationTicks, chance, 1.0D, "загореться");
        }

        public static Drawback reflect(double chance) {
            return reflect(chance, 1.0D);
        }

        /** Chance and multiplier for returned hit damage. */
        public static Drawback reflect(double chance, double damageMultiplier) {
            return new Drawback(Kind.REFLECT, null, 0, 0, chance,
                    Math.max(0.0D, damageMultiplier), "обратного удара");
        }

        public Kind getKind() {
            return kind;
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

        /** Chance from 0 to 1. */
        public double getChance() {
            return chance;
        }

        /** Multiplier applied to raw attack damage before target mitigation when reflecting. */
        public double getDamageMultiplier() {
            return damageMultiplier;
        }

        /** Human-readable drawback text used on the item. */
        public String describe() {
            return switch (kind) {
                case EFFECT -> percent(chance) + " шанс " + label + " после удара";
                case FIRE -> percent(chance) + " шанс загореться после удара "
                        + "(сквозь огнестойкость)";
                case REFLECT -> percent(chance) + " Шанс на обратный урон"
                        + (damageMultiplier == 1.0D ? "" : " ×" + number(damageMultiplier) + " урона");
            };
        }

        private static String number(double value) {
            if (value == Math.rint(value)) return String.valueOf((long) value);
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }

        private static String percent(double value) {
            double percent = Math.round(value * 1000.0D) / 10.0D;
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
