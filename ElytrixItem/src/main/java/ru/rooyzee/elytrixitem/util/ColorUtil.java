package ru.rooyzee.elytrixitem.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String GRADIENT_FROM = "F8BEFB";
    private static final String GRADIENT_TO = "CBC1FB";

    private ColorUtil() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String color = net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(color));
        }

        matcher.appendTail(buffer);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Плавный градиент по символам — тот же, что в меню BMenu
     * (заголовки и названия предметов вида « Название »).
     */
    public static String gradient(String text) {
        return gradient(text, GRADIENT_FROM, GRADIENT_TO);
    }

    public static String gradient(String text, String fromHex, String toHex) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int from = Integer.parseInt(fromHex, 16);
        int to = Integer.parseInt(toHex, 16);
        int last = Math.max(1, text.length() - 1);

        StringBuilder builder = new StringBuilder(text.length() * 15);
        for (int i = 0; i < text.length(); i++) {
            double ratio = i / (double) last;
            int red = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * ratio);
            int green = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * ratio);
            int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * ratio);
            builder.append(String.format("\u00a7x\u00a7%X\u00a7%X\u00a7%X\u00a7%X\u00a7%X\u00a7%X",
                    (red >> 4) & 0xF, red & 0xF, (green >> 4) & 0xF, green & 0xF, (blue >> 4) & 0xF, blue & 0xF));
            builder.append(text.charAt(i));
        }
        return builder.toString();
    }
}