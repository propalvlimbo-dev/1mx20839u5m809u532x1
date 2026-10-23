package ru.rooyzee.elytrixcasino.util;

import org.bukkit.Bukkit;

/**
 * Определение версии сервера для мелких различий в протоколе.
 */
public final class Versions {

    private static final int[] VERSION = parse();

    private Versions() {
    }

    private static int[] parse() {
        try {
            String raw = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = raw.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new int[]{major, minor, patch};
        } catch (Throwable t) {
            return new int[]{1, 16, 5};
        }
    }

    public static boolean isAtLeast(int major, int minor, int patch) {
        if (VERSION[0] != major) {
            return VERSION[0] > major;
        }
        if (VERSION[1] != minor) {
            return VERSION[1] > minor;
        }
        return VERSION[2] >= patch;
    }

    /**
     * Флаг "listed" в player info появился только в 1.19.4.
     * На старых версиях NPC приходится убирать из таба отдельным пакетом.
     */
    public static boolean supportsTabListedFlag() {
        return isAtLeast(1, 19, 4);
    }

    public static String getVersion() {
        return VERSION[0] + "." + VERSION[1] + "." + VERSION[2];
    }
}
