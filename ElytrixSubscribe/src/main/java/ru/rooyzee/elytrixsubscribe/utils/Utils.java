package ru.rooyzee.elytrixsubscribe.utils;

import net.md_5.bungee.api.ChatColor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String formatMillisToDate(long millis) {
        Date date = new Date(millis);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy HH:mm");
        return sdf.format(date);
    }

    public static String formatRemaining(Long endMillis) {
        if (endMillis == null) return "0 мин";
        long remaining = endMillis - System.currentTimeMillis();
        if (remaining <= 0) return "0 мин";

        long totalMinutes = remaining / 60000L;
        long totalHours = totalMinutes / 60L;
        long totalDays = totalHours / 24L;
        long totalMonths = totalDays / 30L;

        if (totalMonths >= 1) {
            long days = totalDays % 30L;
            return totalMonths + " мес " + days + " дн";
        }
        if (totalDays >= 1) {
            long hours = totalHours % 24L;
            return totalDays + " дн " + hours + " ч";
        }
        if (totalHours >= 1) {
            long minutes = totalMinutes % 60L;
            return totalHours + " ч " + minutes + " мин";
        }
        return totalMinutes + " мин";
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}