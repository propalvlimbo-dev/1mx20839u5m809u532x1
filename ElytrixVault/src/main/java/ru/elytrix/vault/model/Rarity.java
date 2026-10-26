package ru.elytrix.vault.model;

public enum Rarity {
    COMMON("Обычный", "&f", 50),
    UNCOMMON("Необычный", "&a", 30),
    RARE("Редкий", "&b", 15),
    EPIC("Эпический", "&d", 6),
    LEGENDARY("Легендарный", "&#F8BEFB", 2);

    private final String defaultDisplayName;
    private final String defaultColor;
    private final int defaultWeight;

    Rarity(String defaultDisplayName, String defaultColor, int defaultWeight) {
        this.defaultDisplayName = defaultDisplayName;
        this.defaultColor = defaultColor;
        this.defaultWeight = defaultWeight;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public String getDefaultColor() {
        return defaultColor;
    }

    public int getDefaultWeight() {
        return defaultWeight;
    }

    public Rarity next() {
        Rarity[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Rarity fromName(String raw, Rarity fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
