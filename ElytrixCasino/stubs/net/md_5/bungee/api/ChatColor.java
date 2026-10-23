package net.md_5.bungee.api;
public enum ChatColor {
    BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,
    DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE, MAGIC, BOLD,
    STRIKETHROUGH, UNDERLINE, ITALIC, RESET;
    public static String stripColor(String input) { return input; }
    public static ChatColor of(String name) { return null; }
}
