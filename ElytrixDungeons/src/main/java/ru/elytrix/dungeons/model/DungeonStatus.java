package ru.elytrix.dungeons.model;

public enum DungeonStatus {
    READY("Закрыт (ждёт активации)"),
    ACTIVE_TRIAL("Идёт бой со стражами"),
    CLEARED("Открыт (на перезарядке)"),
    REGENERATING("Восстанавливается");

    private final String title;

    DungeonStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
