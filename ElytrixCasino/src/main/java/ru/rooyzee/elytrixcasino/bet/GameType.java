package ru.rooyzee.elytrixcasino.bet;

public enum GameType {
    ROULETTE("Рулетка"),
    AUTOMATE("Автомат"),
    MINER("Минёр");

    private final String displayName;

    GameType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
