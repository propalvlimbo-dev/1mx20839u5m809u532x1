package ru.elytrix.duelsbridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Топы по победам и очкам из StatsManager EpicDuels.
 * Раз в минуту пишем tops.json в папку плагина и держим снимок в памяти,
 * чтобы другие плагины могли читать его без задержек (см. api/TopsApi).
 */
final class Tops {

    /** Строка топа. */
    static final class Entry {
        final UUID uuid;
        final int wins;
        final int losses;
        final int score;

        Entry(UUID uuid, int wins, int losses, int score) {
            this.uuid = uuid;
            this.wins = wins;
            this.losses = losses;
            this.score = score;
        }

        double winrate() {
            int games = wins + losses;
            return games == 0 ? 0.0 : Math.round(1000.0 * wins / games) / 10.0;
        }
    }

    private static ElytrixDuelsBridge plugin;
    private static Duels duels;
    private static List<Entry> wins = Collections.emptyList();
    private static List<Entry> score = Collections.emptyList();

    private Tops() {
    }

    static void start(ElytrixDuelsBridge owner, Duels duelsRef) {
        plugin = owner;
        duels = duelsRef;
        refreshNow();
        if (!plugin.getConfig().getBoolean("api.tops", true)) return;
        long seconds = Math.max(10L, plugin.getConfig().getLong("api.tops-interval-seconds", 60L));
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                refreshNow();
            }
        }, seconds * 20L, seconds * 20L);
    }

    /** Пересобрать снимок и записать файл. */
    static void refreshNow() {
        if (plugin == null || duels == null) return;
        int size = Math.max(1, plugin.getConfig().getInt("api.tops-size", 10));
        wins = read("wins", size);
        score = read("score", size);
        writeFile(buildJson(wins, score));
    }

    /** Прочитать топ из EpicDuels ("wins" или "score"). */
    static List<Entry> read(String kind, int limit) {
        if (duels == null) return Collections.emptyList();
        Object manager = duels.manager("getStatsManager");
        if (manager == null) return Collections.emptyList();
        Object value = Duels.call(manager, kind.equals("score") ? "getTopByScore" : "getTopByWins",
                Integer.valueOf(Math.max(1, limit)));
        if (!(value instanceof List)) return Collections.emptyList();
        List<Entry> rows = new ArrayList<Entry>();
        for (Object row : (List<?>) value) {
            if (row == null) continue;
            UUID uuid = (UUID) field(row, "uuid");
            rows.add(new Entry(uuid, number(row, "wins"), number(row, "losses"), number(row, "score")));
        }
        return rows;
    }

    static String buildJson(int limit) {
        int size = Math.max(1, limit);
        return buildJson(read("wins", size), read("score", size));
    }

    private static String buildJson(List<Entry> winsRows, List<Entry> scoreRows) {
        StringBuilder json = new StringBuilder(600);
        json.append("{\n  \"updated\": ").append(System.currentTimeMillis() / 1000L)
                .append(",\n  \"wins\": ");
        appendRows(json, winsRows);
        json.append(",\n  \"score\": ");
        appendRows(json, scoreRows);
        json.append("\n}\n");
        return json.toString();
    }

    private static void appendRows(StringBuilder json, List<Entry> rows) {
        json.append('[');
        int place = 0;
        for (Entry entry : rows) {
            place++;
            if (place > 1) json.append(',');
            json.append("\n    {\"place\": ").append(place)
                    .append(", \"uuid\": \"").append(entry.uuid == null ? "" : entry.uuid)
                    .append("\", \"name\": \"").append(escape(nameOf(entry.uuid)))
                    .append("\", \"wins\": ").append(entry.wins)
                    .append(", \"losses\": ").append(entry.losses)
                    .append(", \"winrate\": ").append(entry.winrate())
                    .append(", \"score\": ").append(entry.score).append('}');
        }
        if (place > 0) json.append('\n');
        json.append("  ]");
    }

    static File fileOf() {
        return plugin == null ? null : file();
    }

    private static File file() {
        String name = plugin.getConfig().getString("api.tops-file", "tops.json");
        if (name == null || name.trim().isEmpty()) name = "tops.json";
        return new File(plugin.getDataFolder(), name);
    }

    private static void writeFile(String content) {
        if (plugin == null) return;
        if (!plugin.getConfig().getBoolean("api.tops", true)) return;
        File target = file();
        File temp = new File(target.getAbsoluteFile().getParentFile(), target.getName() + ".tmp");
        try {
            if (target.getAbsoluteFile().getParentFile() != null) {
                target.getAbsoluteFile().getParentFile().mkdirs();
            }
            Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8);
            try {
                writer.write(content);
            } finally {
                writer.close();
            }
            if (target.exists() && !target.delete()) {
                plugin.getLogger().warning("Топы: не удалось заменить " + target.getName());
            }
            if (!temp.renameTo(target)) {
                plugin.getLogger().warning("Топы: не удалось переименовать " + temp.getName());
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Топы: " + error);
        }
    }

    static String nameOf(UUID id) {
        if (id == null) return "";
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(id);
            String name = player == null ? null : player.getName();
            return name == null ? id.toString() : name;
        } catch (Throwable error) {
            return id.toString();
        }
    }

    private static Object field(Object row, String name) {
        try {
            return row.getClass().getField(name).get(row);
        } catch (Throwable error) {
            return null;
        }
    }

    private static int number(Object row, String name) {
        Object value = field(row, name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String escape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
