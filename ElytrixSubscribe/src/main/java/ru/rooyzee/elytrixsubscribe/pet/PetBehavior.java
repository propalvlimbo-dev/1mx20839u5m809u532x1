package ru.rooyzee.elytrixsubscribe.pet;

/** Режимы поведения питомца. У каждого питомца свой набор (см. {@link PetType}). */
public enum PetBehavior {
    /** Круг вокруг игрока. */
    ORBIT("orbit", "Круг"),
    /** Висит сбоку от игрока. */
    HOVER("hover", "Рядом"),
    /** Садится на голову, когда игрок стоит. */
    HEAD("head", "На плече"),
    /** Летит за спиной. */
    BEHIND("behind", "Позади"),
    /** Кружится и подпрыгивает в такт. */
    DANCE("dance", "Танец"),
    /** Летает зигзагом из стороны в сторону. */
    ZIGZAG("zigzag", "Зигзаг"),
    /** Кувыркается на месте. */
    ROLL("roll", "Кувырок");

    private final String id;
    private final String label;

    PetBehavior(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return this.id;
    }

    public String label() {
        return this.label;
    }

    /** По id из базы; неизвестное значение — null. */
    public static PetBehavior byId(String id) {
        if (id == null) return null;
        for (PetBehavior behavior : values()) {
            if (behavior.id.equalsIgnoreCase(id)) return behavior;
        }
        return null;
    }
}
