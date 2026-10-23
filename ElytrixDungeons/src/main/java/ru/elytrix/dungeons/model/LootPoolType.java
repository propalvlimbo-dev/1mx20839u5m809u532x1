package ru.elytrix.dungeons.model;

import org.bukkit.Material;

public enum LootPoolType {
    SURFACE_CHEST("Сундуки Наземного Дома", "&#96FF91", Material.CHEST),
    SURFACE_SHULKER_COMMON("Шалкер дома — Обычный", "&#DADADA", Material.WHITE_SHULKER_BOX),
    SURFACE_SHULKER_UNCOMMON("Шалкер дома — Необычный", "&#8CFF91", Material.LIME_SHULKER_BOX),
    SURFACE_SHULKER_RARE("Шалкер дома — Редкий", "&#72C9FF", Material.BLUE_SHULKER_BOX),
    SURFACE_SHULKER_EPIC("Шалкер дома — Эпический", "&#C58BFF", Material.PURPLE_SHULKER_BOX),
    SURFACE_SHULKER_LEGENDARY("Шалкер дома — Легендарный", "&#FFBD59", Material.ORANGE_SHULKER_BOX),
    SURFACE_SHULKER_MYTHIC("Шалкер дома — Мифический", "&#FF5E8A", Material.RED_SHULKER_BOX),
    UNDERGROUND_SHULKER("Главный шалкер Подземелья", "&#F8BEFB", Material.PURPLE_SHULKER_BOX),
    UNDERGROUND_CONTAINER("Сундуки и бочки Подземелья", "&#89A0FF", Material.BARREL);

    private final String title;
    private final String colorHex;
    private final Material icon;

    LootPoolType(String title, String colorHex, Material icon) {
        this.title = title;
        this.colorHex = colorHex;
        this.icon = icon;
    }

    public String getTitle() { return title; }
    public String getColorHex() { return colorHex; }
    public Material getIcon() { return icon; }
}
