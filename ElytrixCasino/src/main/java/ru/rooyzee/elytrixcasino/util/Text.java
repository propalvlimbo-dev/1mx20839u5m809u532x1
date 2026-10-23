package ru.rooyzee.elytrixcasino.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;

import java.util.Locale;

public final class Text {

    private static final String CODES = "0123456789abcdef";

    private Text() {
    }

    public static String profileName(String input) {
        String plain = ChatColor.stripColor(color(input));
        if (plain == null || plain.isEmpty()) {
            plain = "Roulette";
        }
        if (plain.length() > 16) {
            return plain.substring(0, 16);
        }
        return plain;
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if ((current == '&' || current == ChatColor.COLOR_CHAR)
                    && i + 7 < input.length()
                    && input.charAt(i + 1) == '#') {
                String hex = input.substring(i + 2, i + 8);
                if (isHex(hex)) {
                    out.append(ChatColor.COLOR_CHAR).append('x');
                    for (int h = 0; h < 6; h++) {
                        out.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(hex.charAt(h)));
                    }
                    i += 7;
                    continue;
                }
            }
            out.append(current);
        }
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public static Component component(String input) {
        String text = color(input);
        TextComponent.Builder root = Component.text();
        StringBuilder buffer = new StringBuilder();
        Style.Builder style = Style.style().decoration(TextDecoration.ITALIC, false);

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == ChatColor.COLOR_CHAR && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (buffer.length() > 0) {
                    root.append(Component.text(buffer.toString(), style.build()));
                    buffer.setLength(0);
                }
                if (code == 'x' && i + 12 < text.length()) {
                    StringBuilder hex = new StringBuilder("#");
                    boolean valid = true;
                    for (int h = 0; h < 6; h++) {
                        if (text.charAt(i + 1) != ChatColor.COLOR_CHAR) {
                            valid = false;
                            break;
                        }
                        hex.append(text.charAt(i + 2));
                        i += 2;
                    }
                    if (valid) {
                        TextColor hexColor = TextColor.fromHexString(hex.toString());
                        style = Style.style().decoration(TextDecoration.ITALIC, false);
                        if (hexColor != null) {
                            style = style.color(hexColor);
                        }
                    }
                    continue;
                }
                if (code == 'r') {
                    style = Style.style().decoration(TextDecoration.ITALIC, false);
                } else if (CODES.indexOf(code) >= 0) {
                    style = Style.style().color(colorOf(code)).decoration(TextDecoration.ITALIC, false);
                } else {
                    TextDecoration decoration = decorationOf(code);
                    if (decoration != null) {
                        style = style.decoration(decoration, true);
                    }
                }
                continue;
            }
            buffer.append(current);
        }
        if (buffer.length() > 0) {
            root.append(Component.text(buffer.toString(), style.build()));
        }
        return root.build();
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean upper = c >= 'A' && c <= 'F';
            boolean lower = c >= 'a' && c <= 'f';
            if (!digit && !upper && !lower) {
                return false;
            }
        }
        return true;
    }

    private static NamedTextColor colorOf(char code) {
        switch (code) {
            case '0': return NamedTextColor.BLACK;
            case '1': return NamedTextColor.DARK_BLUE;
            case '2': return NamedTextColor.DARK_GREEN;
            case '3': return NamedTextColor.DARK_AQUA;
            case '4': return NamedTextColor.DARK_RED;
            case '5': return NamedTextColor.DARK_PURPLE;
            case '6': return NamedTextColor.GOLD;
            case '7': return NamedTextColor.GRAY;
            case '8': return NamedTextColor.DARK_GRAY;
            case '9': return NamedTextColor.BLUE;
            case 'a': return NamedTextColor.GREEN;
            case 'b': return NamedTextColor.AQUA;
            case 'c': return NamedTextColor.RED;
            case 'd': return NamedTextColor.LIGHT_PURPLE;
            case 'e': return NamedTextColor.YELLOW;
            default: return NamedTextColor.WHITE;
        }
    }

    private static TextDecoration decorationOf(char code) {
        switch (code) {
            case 'k': return TextDecoration.OBFUSCATED;
            case 'l': return TextDecoration.BOLD;
            case 'm': return TextDecoration.STRIKETHROUGH;
            case 'n': return TextDecoration.UNDERLINED;
            case 'o': return TextDecoration.ITALIC;
            default: return null;
        }
    }

    public static String formatMultiplier(double multiplier) {
        if (multiplier == Math.rint(multiplier) && !Double.isInfinite(multiplier)) {
            return String.valueOf((long) multiplier);
        }
        return String.valueOf(multiplier);
    }

    public static String formatPercent(double percent) {
        return String.format(Locale.US, "%.1f", percent);
    }
}
