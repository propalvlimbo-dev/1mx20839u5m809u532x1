package org.bukkit;

public enum ChatColor {
    BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE,
    GOLD, GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE,
    MAGIC, BOLD, STRIKETHROUGH, UNDERLINE, ITALIC, RESET;

    public static final char COLOR_CHAR = '\u00A7';
    public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) { return textToTranslate; }
    public static String stripColor(String input) { return input; }
}
