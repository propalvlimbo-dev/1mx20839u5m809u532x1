package ru.rooyzee.elytrixcasino.util;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Загрузка скина игрока с Mojang (value + signature для пакетного NPC).
 * Вызывать только асинхронно.
 */
public final class SkinFetcher {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]+)\"");
    private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private SkinFetcher() {
    }

    /**
     * @return массив {value, signature} или null, если не получилось
     */
    public static String[] fetch(String playerName) throws Exception {
        String profile = read("https://api.mojang.com/users/profiles/minecraft/" + playerName);
        if (profile == null) {
            return null;
        }
        Matcher idMatcher = ID_PATTERN.matcher(profile);
        if (!idMatcher.find()) {
            return null;
        }
        String uuid = idMatcher.group(1);

        String textures = read("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
        if (textures == null) {
            return null;
        }
        Matcher valueMatcher = VALUE_PATTERN.matcher(textures);
        Matcher signatureMatcher = SIGNATURE_PATTERN.matcher(textures);
        if (!valueMatcher.find() || !signatureMatcher.find()) {
            return null;
        }
        return new String[]{valueMatcher.group(1), signatureMatcher.group(1)};
    }

    private static String read(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "ElytrixCasino");
        try {
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream stream = connection.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
