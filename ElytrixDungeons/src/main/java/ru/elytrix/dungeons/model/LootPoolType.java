package ru.elytrix.dungeons.model;

import org.bukkit.Material;

public enum LootPoolType {
    SURFACE_CHEST("Сундуки Наземного Дома", "&#96FF91", Material.CHEST),
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
