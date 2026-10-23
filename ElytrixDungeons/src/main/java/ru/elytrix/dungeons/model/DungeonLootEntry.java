package ru.elytrix.dungeons.model;

import org.bukkit.inventory.ItemStack;

public final class DungeonLootEntry {

    public enum SourceKind {
        ITEM_STACK,
        VAULT_KEY,
        ELYTRIX_ITEM
    }

    private final String id;
    private SourceKind sourceKind;
    private String externalId;
    private ItemStack fallbackStack;
    private double chancePercent;
    private int minAmount;
    private int maxAmount;
    private boolean guaranteed;

    public DungeonLootEntry(
            String id,
            SourceKind sourceKind,
            String externalId,
            ItemStack fallbackStack,
            double chancePercent,
            int minAmount,
            int maxAmount,
            boolean guaranteed
    ) {
        this.id = id;
        this.sourceKind = sourceKind;
        this.externalId = externalId;
        this.fallbackStack = fallbackStack.clone();
        this.chancePercent = Math.max(0.5, Math.min(100.0, chancePercent));
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.guaranteed = guaranteed;
    }

    public String getId() { return id; }
    public SourceKind getSourceKind() { return sourceKind; }
    public String getExternalId() { return externalId; }
    public ItemStack getFallbackStack() { return fallbackStack.clone(); }
    public double getChancePercent() { return chancePercent; }
    public void setChancePercent(double v) { this.chancePercent = Math.round(Math.max(0.5, Math.min(100.0, v)) * 10.0) / 10.0; }
    public int getMinAmount() { return minAmount; }
    public void setMinAmount(int min) {
        this.minAmount = Math.max(1, Math.min(64, min));
        if (this.maxAmount < this.minAmount) this.maxAmount = this.minAmount;
    }
    public int getMaxAmount() { return maxAmount; }
    public void setMaxAmount(int max) {
        this.maxAmount = Math.max(this.minAmount, Math.min(64, max));
    }
    public boolean isGuaranteed() { return guaranteed; }
    public void setGuaranteed(boolean guaranteed) { this.guaranteed = guaranteed; }
}
