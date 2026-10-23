package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AutomateHolder implements InventoryHolder {

    private final String automateId;
    private Inventory inventory;
    private boolean spinning;
    private int playSlot = -1;

    public AutomateHolder(String automateId) {
        this.automateId = automateId;
    }

    public String getAutomateId() {
        return automateId;
    }

    public boolean isSpinning() {
        return spinning;
    }

    public void setSpinning(boolean spinning) {
        this.spinning = spinning;
    }

    public int getPlaySlot() {
        return playSlot;
    }

    public void setPlaySlot(int playSlot) {
        this.playSlot = playSlot;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
