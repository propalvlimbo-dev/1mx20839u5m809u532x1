package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixcasino.miner.MinerSession;

public final class MinerGameHolder implements InventoryHolder {

    private final MinerSession session;
    private Inventory inventory;

    public MinerGameHolder(MinerSession session) {
        this.session = session;
    }

    public MinerSession getSession() {
        return session;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
