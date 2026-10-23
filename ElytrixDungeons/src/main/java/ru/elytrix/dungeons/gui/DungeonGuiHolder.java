package ru.elytrix.dungeons.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.elytrix.dungeons.model.LootPoolType;

public final class DungeonGuiHolder implements InventoryHolder {

    public enum Screen {
        PLAYER_OVERVIEW,
        ADMIN_BOARD,
        LOOT_POOL_SELECT,
        LOOT_POOL_EDITOR,
        TEMPLATE_SPAWNER
    }

    private final Screen screen;
    private final LootPoolType poolType;
    private Inventory inventory;

    public DungeonGuiHolder(Screen screen, LootPoolType poolType) {
        this.screen = screen;
        this.poolType = poolType;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public Screen getScreen() { return screen; }
    public LootPoolType getPoolType() { return poolType; }
}
