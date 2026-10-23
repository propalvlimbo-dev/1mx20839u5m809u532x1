package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Holder позволяет надёжно опознать наше меню (без сравнения заголовков).
 */
public class RouletteHolder implements InventoryHolder {

    private final String rouletteId;
    private final Map<Integer, Long> bets = new HashMap<>();
    private Inventory inventory;

    public RouletteHolder(String rouletteId) {
        this.rouletteId = rouletteId;
    }

    public String getRouletteId() {
        return rouletteId;
    }

    public Map<Integer, Long> getBets() {
        return bets;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
