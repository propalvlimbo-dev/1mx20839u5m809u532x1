package ru.rooyzee.elytrixsubscribe.pet;

/** Настройки одного питомца у игрока. */
public class PetData {
    private final PetType type;
    private String name;
    private int variant;
    private PetBehavior behavior;
    private boolean active;

    public PetData(PetType type) {
        this.type = type;
        this.behavior = type.defaultBehavior();
    }

    public PetType type() {
        return this.type;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name == null || name.trim().isEmpty() ? null : name.trim();
    }

    public int variant() {
        return this.variant;
    }

    public void setVariant(int variant) {
        this.variant = Math.abs(variant) % this.type.lookVariants();
    }

    public PetBehavior behavior() {
        return this.behavior;
    }

    public void setBehavior(PetBehavior behavior) {
        this.behavior = this.type.allows(behavior) ? behavior : this.type.defaultBehavior();
    }

    /** Как настройка внешнего вида выглядит в меню. */
    public String lookLabel() {
        return this.type.lookLabel(this.variant);
    }

    public boolean active() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Для БД: 1 — включён, 0 — нет. */
    public int activeFlag() {
        return this.active ? 1 : 0;
    }
}
