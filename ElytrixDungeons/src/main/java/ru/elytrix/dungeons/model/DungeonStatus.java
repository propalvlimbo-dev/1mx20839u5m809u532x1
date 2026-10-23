package ru.elytrix.dungeons.model;

public enum DungeonStatus {
    READY("Готов к испытанию"),
    ACTIVE_TRIAL("Идёт испытание"),
    CLEARED("Зачищен"),
    REGENERATING("Восстанавливается");

    private final String title;

    DungeonStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
