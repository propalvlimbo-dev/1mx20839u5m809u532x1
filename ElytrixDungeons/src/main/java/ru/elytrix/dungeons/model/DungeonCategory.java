package ru.elytrix.dungeons.model;

import org.bukkit.Material;

public enum DungeonCategory {
    SURFACE("Наземный (Дом)", "&#96FF91", Material.DARK_OAK_DOOR),
    UNDERGROUND("Подземный", "&#89A0FF", Material.DEEPSLATE_TILES);

    private final String title;
    private final String colorHex;
    private final Material icon;

    DungeonCategory(String title, String colorHex, Material icon) {
        this.title = title;
        this.colorHex = colorHex;
        this.icon = icon;
    }

    public String getTitle() { return title; }
    public String getColorHex() { return colorHex; }
    public Material getIcon() { return icon; }
}
