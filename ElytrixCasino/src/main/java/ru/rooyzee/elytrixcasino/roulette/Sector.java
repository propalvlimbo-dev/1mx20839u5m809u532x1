package ru.rooyzee.elytrixcasino.roulette;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixcasino.util.Text;

/**
 * Один сектор рулетки.
 */
public final class Sector {

    private final double multiplier;
    private final int weight;
    private final String color;
    private final Material icon;

    public Sector(double multiplier, int weight, String color, Material icon) {
        this.multiplier = multiplier;
        this.weight = Math.max(0, weight);
        this.color = color == null ? "&f" : color;
        this.icon = icon == null ? Material.WHITE_WOOL : icon;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getWeight() {
        return weight;
    }

    public String getColor() {
        return color;
    }

    public Material getIcon() {
        return icon;
    }

    public String getLabel() {
        return "x" + Text.formatMultiplier(multiplier);
    }

    public String getColoredLabel() {
        return color + "&l" + getLabel();
    }

    public ItemStack createIcon() {
        return new ItemStack(icon);
    }
}
