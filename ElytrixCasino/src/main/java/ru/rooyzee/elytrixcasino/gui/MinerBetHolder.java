package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class MinerBetHolder implements InventoryHolder {

    private final String minerId;
    private final Map<Integer, Long> bets = new HashMap<>();
    private Inventory inventory;

    public MinerBetHolder(String minerId) {
        this.minerId = minerId;
    }

    public String getMinerId() {
        return minerId;
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
