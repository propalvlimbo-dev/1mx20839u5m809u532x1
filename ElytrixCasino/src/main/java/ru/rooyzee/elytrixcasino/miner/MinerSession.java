package ru.rooyzee.elytrixcasino.miner;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Состояние одной активной партии игрока в «Минёр» (поле 45 клеток = 5x9).
 */
public final class MinerSession {

    public enum CellType {
        MINE,
        EMPTY,
        COINS
    }

    public static final class Cell {
        private final CellType type;
        private final Material icon;
        private final String title;
        private final long reward;

        public Cell(CellType type, Material icon, String title, long reward) {
            this.type = type;
            this.icon = icon;
            this.title = title;
            this.reward = reward;
        }

        public CellType getType() {
            return type;
        }

        public Material getIcon() {
            return icon;
        }

        public String getTitle() {
            return title;
        }

        public long getReward() {
            return reward;
        }
    }

    private final UUID playerId;
    private final String minerId;
    private final long bet;
    private final Cell[] cells;
    private final boolean[] revealed;
    private final int totalMines;
    private final int totalCoinCells;

    private long collectedCoins;
    private int revealedSafeCells;
    private int revealedCoinCells;
    private boolean finished;

    private MinerSession(UUID playerId,
                         String minerId,
                         long bet,
                         Cell[] cells,
                         int totalMines,
                         int totalCoinCells) {
        this.playerId = playerId;
        this.minerId = minerId;
        this.bet = bet;
        this.cells = cells;
        this.revealed = new boolean[cells.length];
        this.totalMines = totalMines;
        this.totalCoinCells = totalCoinCells;
    }

    public static MinerSession generate(UUID playerId, String minerId, long bet, MinerSettings settings) {
        List<Cell> deck = new ArrayList<>(MinerSettings.BOARD_SIZE);

        int mines = settings.getMinesCount();
        for (int i = 0; i < mines; i++) {
            deck.add(new Cell(CellType.MINE, Material.TNT, "&#FF5555&lМИНА", 0L));
        }

        int coinCells = 0;
        for (MinerSettings.CoinTier tier : settings.getCoinTiers()) {
            long reward = tier.rewardForBet(bet);
            for (int i = 0; i < tier.getCount() && deck.size() < MinerSettings.BOARD_SIZE; i++) {
                deck.add(new Cell(CellType.COINS, tier.getIcon(), tier.getDisplayName(), reward));
                coinCells++;
            }
        }

        while (deck.size() < MinerSettings.BOARD_SIZE) {
            deck.add(new Cell(CellType.EMPTY, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Пустая клетка", 0L));
        }

        Collections.shuffle(deck, ThreadLocalRandom.current());
        return new MinerSession(
                playerId,
                minerId,
                bet,
                deck.toArray(new Cell[0]),
                mines,
                coinCells
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getMinerId() {
        return minerId;
    }

    public long getBet() {
        return bet;
    }

    public int size() {
        return cells.length;
    }

    public Cell getCell(int slot) {
        return cells[slot];
    }

    public boolean isRevealed(int slot) {
        return revealed[slot];
    }

    public Cell reveal(int slot) {
        if (finished || slot < 0 || slot >= cells.length || revealed[slot]) {
            return null;
        }
        revealed[slot] = true;
        Cell cell = cells[slot];
        if (cell.getType() == CellType.MINE) {
            finished = true;
        } else {
            revealedSafeCells++;
            if (cell.getType() == CellType.COINS) {
                collectedCoins += cell.getReward();
                revealedCoinCells++;
            }
        }
        return cell;
    }

    public boolean allCoinCellsFound() {
        return totalCoinCells > 0 && revealedCoinCells >= totalCoinCells;
    }

    public long getCollectedCoins() {
        return collectedCoins;
    }

    public int getRevealedSafeCells() {
        return revealedSafeCells;
    }

    public int getTotalMines() {
        return totalMines;
    }

    public int getRemainingCoinCells() {
        return Math.max(0, totalCoinCells - revealedCoinCells);
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean markFinished() {
        if (finished) {
            return false;
        }
        finished = true;
        return true;
    }
}
