package ru.rooyzee.elytrixsubscribe.pet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Три питомца подписки. У каждого свой набор режимов поведения и своя
 * единственная настройка внешнего вида:
 *
 *   попугай — окрас (5 вариантов),
 *   аллей   — свечение,
 *   пчела   — размер (малыш или обычная).
 */
public enum PetType {
    PARROT("parrot", "Попугай", "parrot_spawn_egg", "Окрас",
            new PetBehavior[]{PetBehavior.ORBIT, PetBehavior.HOVER, PetBehavior.HEAD}),
    ALLAY("allay", "Аллей", "allay_spawn_egg", "Свечение",
            new PetBehavior[]{PetBehavior.ORBIT, PetBehavior.HOVER, PetBehavior.BEHIND,
                    PetBehavior.DANCE}),
    BEE("bee", "Пчела", "bee_spawn_egg", "Размер",
            new PetBehavior[]{PetBehavior.ORBIT, PetBehavior.HOVER, PetBehavior.ZIGZAG,
                    PetBehavior.ROLL});

    private static final String[] PARROT_COLORS = {
            "Красный", "Синий", "Зелёный", "Бирюзовый", "Серый"
    };

    private final String id;
    private final String displayName;
    private final String icon;
    private final String lookName;
    private final List<PetBehavior> behaviors;

    PetType(String id, String displayName, String icon, String lookName,
            PetBehavior[] behaviors) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.lookName = lookName;
        this.behaviors = Collections.unmodifiableList(new ArrayList<PetBehavior>(
                Arrays.asList(behaviors)));
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public String icon() {
        return this.icon;
    }

    /** Как называется настройка внешнего вида в меню. */
    public String lookName() {
        return this.lookName;
    }

    public List<PetBehavior> behaviors() {
        return this.behaviors;
    }

    public boolean allows(PetBehavior behavior) {
        return behavior != null && this.behaviors.contains(behavior);
    }

    public PetBehavior defaultBehavior() {
        return PetBehavior.ORBIT;
    }

    /** Следующий режим по кругу — для кнопки «Поведение». */
    public PetBehavior next(PetBehavior current) {
        if (current == null || !this.behaviors.contains(current)) return this.behaviors.get(0);
        int index = this.behaviors.indexOf(current);
        return this.behaviors.get((index + 1) % this.behaviors.size());
    }

    /** Сколько вариантов у настройки внешнего вида. */
    public int lookVariants() {
        return this == PARROT ? PARROT_COLORS.length : 2;
    }

    /** Подпись варианта: окрас, «включено/выключено» или «малыш». */
    public String lookLabel(int variant) {
        if (this == PARROT) {
            int index = Math.abs(variant) % PARROT_COLORS.length;
            return PARROT_COLORS[index];
        }
        if (this == BEE) return variant > 0 ? "Малыш" : "Обычная";
        return variant > 0 ? "Включено" : "Выключено";
    }

    public int nextLook(int variant) {
        return (Math.abs(variant) + 1) % lookVariants();
    }

    public static PetType byId(String id) {
        if (id == null) return null;
        for (PetType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }

    /** Все id через запятую — для подсказок. */
    public static String ids() {
        StringBuilder out = new StringBuilder();
        for (PetType type : values()) {
            if (out.length() > 0) out.append(", ");
            out.append(type.id);
        }
        return out.toString();
    }
}
