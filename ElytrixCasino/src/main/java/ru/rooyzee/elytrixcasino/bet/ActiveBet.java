package ru.rooyzee.elytrixcasino.bet;

import java.util.UUID;

public final class ActiveBet {

    private final UUID playerId;
    private final GameType gameType;
    private final String machineId;
    private final long amount;
    private final long timestamp;

    public ActiveBet(UUID playerId, GameType gameType, String machineId, long amount) {
        this.playerId = playerId;
        this.gameType = gameType;
        this.machineId = machineId;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getMachineId() {
        return machineId;
    }

    public long getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
