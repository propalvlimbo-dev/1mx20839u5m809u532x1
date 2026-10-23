package ru.elytrix.dungeons.model;

import org.bukkit.Material;

public enum DungeonCategory {
    SURFACE("Наземный", "&#96FF91", Material.MOSSY_STONE_BRICKS),
    UNDERGROUND("Подземный", "&#89A0FF", Material.DEEPSLATE_TILES),
    SKY("Воздушный", "&#F8BEFB", Material.AMETHYST_BLOCK);

    private final String title;
    private final String colorHex;
    private final Material icon;

    DungeonCategory(String title, String colorHex, Material icon) {
        this.title = title;
        this.colorHex = colorHex;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public String getColorHex() {
        return colorHex;
    }

    public Material getIcon() {
        return icon;
    }
}
