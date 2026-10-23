package ru.elytrix.dungeons.model;

public enum TrialType {
    WAVES("Волны стражей и Босс"),
    CAPTURE("Удержание алтаря"),
    SEALS("Печати древних и Хранитель");

    private final String title;

    TrialType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
