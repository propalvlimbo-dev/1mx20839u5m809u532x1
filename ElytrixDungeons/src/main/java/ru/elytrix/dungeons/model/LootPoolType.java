package ru.elytrix.dungeons.model;

import org.bukkit.Material;

public enum LootPoolType {
    SURFACE_CHEST("Сундуки (Наземный данж)", Material.CHEST, "&#96FF91"),
    SURFACE_BARREL("Бочки (Наземный данж)", Material.BARREL, "&#96FF91"),
    SURFACE_UNIQUE_SHULKER("Уникальные шалкеры 3 шт. (Наземный)", Material.PURPLE_SHULKER_BOX, "&#F8BEFB"),
    UNDERGROUND_SHULKER("Главный шалкер после мобов (Подземный)", Material.SHULKER_BOX, "&#89A0FF"),
    UNDERGROUND_CONTAINER("Бочки/сундуки зала (Подземный)", Material.DEEPSLATE_TILES, "&#89A0FF"),
    SKY_ANGELIC_SHARD("Ангельские осколки — лут (Воздушный)", Material.AMETHYST_CLUSTER, "&#F8BEFB");

    private final String title;
    private final Material icon;
    private final String colorHex;

    LootPoolType(String title, Material icon, String colorHex) {
        this.title = title;
        this.icon = icon;
        this.colorHex = colorHex;
    }

    public String getTitle() { return title; }
    public Material getIcon() { return icon; }
    public String getColorHex() { return colorHex; }
}
