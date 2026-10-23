package ru.elytrix.dungeons.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private TextUtil() {}

    public static String color(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(raw);
        StringBuilder out = new StringBuilder(raw.length() + 32);
        int last = 0;
        while (matcher.find()) {
            out.append(raw, last, matcher.start());
            String hex = matcher.group(1);
            out.append(ChatColor.COLOR_CHAR).append('x');
            for (int i = 0; i < 6; i++) {
                out.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(hex.charAt(i)));
            }
            last = matcher.end();
        }
        out.append(raw, last, raw.length());
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public static List<String> colorList(List<String> lines) {
        List<String> res = new ArrayList<>(lines.size());
        for (String line : lines) {
            res.add(color(line));
        }
        return res;
    }

    public static String stars(int tier) {
        int clamped = Math.max(1, Math.min(5, tier));
        StringBuilder sb = new StringBuilder();
        sb.append("&#F8BEFB");
        for (int i = 0; i < clamped; i++) {
            sb.append('★');
        }
        if (clamped < 5) {
            sb.append("&8");
            for (int i = clamped; i < 5; i++) {
                sb.append('☆');
            }
        }
        return sb.toString();
    }

    public static String formatDuration(long secondsTotal) {
        long s = Math.max(0L, secondsTotal);
        long hours = s / 3600L;
        long minutes = (s % 3600L) / 60L;
        long secs = s % 60L;
        if (hours > 0) {
            return String.format("%02dч %02dм %02dс", hours, minutes, secs);
        }
        return String.format("%02dм %02dс", minutes, secs);
    }

    public static String formatShortClock(long secondsTotal) {
        long s = Math.max(0L, secondsTotal);
        return String.format("%02d:%02d", s / 60L, s % 60L);
    }

    public static String itemTitle(ItemStack stack) {
        if (stack == null) {
            return "Предмет";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName()).trim();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        String raw = stack.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
