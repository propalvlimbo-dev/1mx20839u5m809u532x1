package ru.rooyzee.potionstacker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Цвета: &7-код и &amp;#RRGGBB. */
public final class Colors {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final String CODES = "0123456789abcdefklmnor";

    private Colors() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;
        Matcher matcher = HEX.matcher(result);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(
                    "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§"
                            + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5)));
        }
        matcher.appendTail(builder);
        result = builder.toString();

        StringBuilder out = new StringBuilder(result.length());
        for (int i = 0; i < result.length(); i++) {
            char symbol = result.charAt(i);
            if (symbol == '&' && i + 1 < result.length()
                    && CODES.indexOf(Character.toLowerCase(result.charAt(i + 1))) >= 0) {
                out.append('§').append(Character.toLowerCase(result.charAt(i + 1)));
                i++;
                continue;
            }
            out.append(symbol);
        }
        return out.toString();
    }
}
