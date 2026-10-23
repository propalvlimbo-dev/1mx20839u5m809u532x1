package ru.rooyzee.elytrixcasino.bet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальный реестр активных ставок казино:
 * гарантирует, что у одного игрока одновременно не может быть больше 1 активной ставки
 * ни в рулетке, ни в автоматах, ни в минёре.
 */
public final class ActiveBetManager {

    private final Map<UUID, ActiveBet> activeBets = new ConcurrentHashMap<>();

    public ActiveBet getActiveBet(UUID playerId) {
        return activeBets.get(playerId);
    }

    public boolean hasActiveBet(UUID playerId) {
        return activeBets.containsKey(playerId);
    }

    public boolean tryAcquire(UUID playerId, GameType gameType, String machineId, long amount) {
        ActiveBet created = new ActiveBet(playerId, gameType, machineId, amount);
        return activeBets.putIfAbsent(playerId, created) == null;
    }

    public void release(UUID playerId) {
        if (playerId != null) {
            activeBets.remove(playerId);
        }
    }

    public void release(UUID playerId, GameType expectedType) {
        if (playerId == null) {
            return;
        }
        activeBets.computeIfPresent(playerId, (id, current) ->
                current.getGameType() == expectedType ? null : current);
    }

    public void clearAll() {
        activeBets.clear();
    }
}
