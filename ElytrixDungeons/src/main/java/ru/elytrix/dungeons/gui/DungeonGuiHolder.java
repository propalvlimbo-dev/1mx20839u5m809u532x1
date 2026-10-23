package ru.elytrix.dungeons.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DungeonGuiHolder implements InventoryHolder {

    public enum Screen {
        PLAYER_OVERVIEW,
        ADMIN_BOARD,
        LOOT_TIER_SELECT,
        LOOT_POOL_EDITOR,
        TEMPLATE_SPAWNER
    }

    private final Screen screen;
    private final int tier;
    private final boolean rewardPool;
    private Inventory inventory;

    public DungeonGuiHolder(Screen screen, int tier, boolean rewardPool) {
        this.screen = screen;
        this.tier = tier;
        this.rewardPool = rewardPool;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Screen getScreen() { return screen; }
    public int getTier() { return tier; }
    public boolean isRewardPool() { return rewardPool; }
}
