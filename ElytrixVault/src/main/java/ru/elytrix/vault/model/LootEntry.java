package ru.elytrix.vault.model;

import java.util.Map;
import org.bukkit.inventory.ItemStack;

public class LootEntry {

    private final String id;
    private final ItemStack previewItem;
    private final String command;
    private Rarity rarity;
    private int customWeight;

    public LootEntry(String id, ItemStack previewItem, String command, Rarity rarity, int customWeight) {
        this.id = id;
        this.previewItem = previewItem.clone();
        this.command = (command == null || command.isBlank()) ? null : command.trim();
        this.rarity = rarity == null ? Rarity.RARE : rarity;
        this.customWeight = Math.max(0, customWeight);
    }

    public String getId() {
        return id;
    }

    public ItemStack getPreviewItem() {
        return previewItem.clone();
    }

    public String getCommand() {
        return command;
    }

    public boolean hasCommand() {
        return command != null && !command.isEmpty();
    }

    public Rarity getRarity() {
        return rarity;
    }

    public void setRarity(Rarity rarity) {
        this.rarity = rarity == null ? Rarity.COMMON : rarity;
    }

    public int getCustomWeight() {
        return customWeight;
    }

    public void setCustomWeight(int customWeight) {
        this.customWeight = Math.max(0, customWeight);
    }

    public int getEffectiveWeight(Map<Rarity, Integer> rarityWeights) {
        if (customWeight > 0) {
            return customWeight;
        }
        Integer base = rarityWeights.get(rarity);
        return Math.max(1, base != null ? base : rarity.getDefaultWeight());
    }
}
