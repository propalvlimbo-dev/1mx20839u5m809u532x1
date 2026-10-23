package ru.elytrix.dungeons.model;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;

/** Rarity tiers and their visual marker/pool for surface-house shulkers. */
public enum SurfaceShulkerRarity {
    COMMON("Обычный", "&#DADADA", Material.WHITE_SHULKER_BOX,
            LootPoolType.SURFACE_SHULKER_COMMON, 42),
    UNCOMMON("Необычный", "&#8CFF91", Material.LIME_SHULKER_BOX,
            LootPoolType.SURFACE_SHULKER_UNCOMMON, 26),
    RARE("Редкий", "&#72C9FF", Material.BLUE_SHULKER_BOX,
            LootPoolType.SURFACE_SHULKER_RARE, 16),
    EPIC("Эпический", "&#C58BFF", Material.PURPLE_SHULKER_BOX,
            LootPoolType.SURFACE_SHULKER_EPIC, 9),
    LEGENDARY("Легендарный", "&#FFBD59", Material.ORANGE_SHULKER_BOX,
            LootPoolType.SURFACE_SHULKER_LEGENDARY, 5),
    MYTHIC("Мифический", "&#FF5E8A", Material.RED_SHULKER_BOX,
            LootPoolType.SURFACE_SHULKER_MYTHIC, 2);

    private final String displayName;
    private final String colorHex;
    private final Material blockMaterial;
    private final LootPoolType lootPool;
    private final int weight;

    SurfaceShulkerRarity(
            String displayName,
            String colorHex,
            Material blockMaterial,
            LootPoolType lootPool,
            int weight
    ) {
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.blockMaterial = blockMaterial;
        this.lootPool = lootPool;
        this.weight = weight;
    }

    public String getDisplayName() { return displayName; }
    public String getColorHex() { return colorHex; }
    public Material getBlockMaterial() { return blockMaterial; }
    public LootPoolType getLootPool() { return lootPool; }
    public int getWeight() { return weight; }

    public static SurfaceShulkerRarity roll() {
        int totalWeight = 0;
        for (SurfaceShulkerRarity rarity : values()) {
            totalWeight += rarity.weight;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (SurfaceShulkerRarity rarity : values()) {
            cumulative += rarity.weight;
            if (roll < cumulative) {
                return rarity;
            }
        }
        return COMMON;
    }

    public static SurfaceShulkerRarity fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
