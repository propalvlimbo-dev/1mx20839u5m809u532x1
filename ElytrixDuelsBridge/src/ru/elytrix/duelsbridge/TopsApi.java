package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Публичный API топов для других плагинов: сайт, голограммы, Discord-бот.
 *
 * <pre>
 * List&lt;TopsApi.Row&gt; top = TopsApi.topByWins(10);
 * for (TopsApi.Row row : top) {
 *     row.place(); row.name(); row.uuid();
 *     row.wins(); row.losses(); row.winrate(); row.score();
 * }
 * String json = TopsApi.json(10);   // готовый JSON для сайта
 * </pre>
 *
 * Данные берутся из StatsManager EpicDuels. Чтобы класс был виден,
 * в plugin.yml другого плагина: {@code depend: [ElytrixDuelsBridge]}.
 */
public final class TopsApi {

    private TopsApi() {
    }

    /** Одна строка топа. */
    public static final class Row {
        private final int place;
        private final UUID uuid;
        private final String name;
        private final int wins;
        private final int losses;
        private final double winrate;
        private final int score;

        public Row(int place, UUID uuid, String name, int wins, int losses, double winrate, int score) {
            this.place = place;
            this.uuid = uuid;
            this.name = name;
            this.wins = wins;
            this.losses = losses;
            this.winrate = winrate;
            this.score = score;
        }

        public int place() { return place; }
        public UUID uuid() { return uuid; }
        public String name() { return name; }
        public int wins() { return wins; }
        public int losses() { return losses; }
        public double winrate() { return winrate; }
        public int score() { return score; }
    }

    /** Топ по победам, первым идёт 1-е место. Пустой список, если данных нет. */
    public static List<Row> topByWins(int limit) {
        return rows(Tops.read("wins", limit));
    }

    /** Топ по очкам, первым идёт 1-е место. */
    public static List<Row> topByScore(int limit) {
        return rows(Tops.read("score", limit));
    }

    /** Ник по UUID: онлайн или из кеша сервера. */
    public static String nameOf(UUID uuid) {
        return Tops.nameOf(uuid);
    }

    /** JSON топа: {@code {"updated":..., "wins":[...], "score":[...]}}. */
    public static String json(int limit) {
        return Tops.buildJson(limit);
    }

    /** Пересобрать топы и перезаписать tops.json прямо сейчас. */
    public static void refresh() {
        Tops.refreshNow();
    }

    /** Файл, который обновляется сам (plugins/ElytrixDuelsBridge/tops.json). */
    public static java.io.File file() {
        return Tops.fileOf();
    }

    /** Игрок в топе? Номер места или 0, если его там нет. */
    public static int placeOf(UUID uuid, String kind, int limit) {
        if (uuid == null) return 0;
        List<Tops.Entry> rows = Tops.read("score".equals(kind) ? "score" : "wins", limit);
        for (int i = 0; i < rows.size(); i++) {
            Tops.Entry row = rows.get(i);
            if (uuid.equals(row.uuid)) return i + 1;
        }
        return 0;
    }

    private static List<Row> rows(List<Tops.Entry> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<Row> out = new ArrayList<Row>(source.size());
        int place = 0;
        for (Tops.Entry entry : source) {
            place++;
            out.add(new Row(place, entry.uuid, Tops.nameOf(entry.uuid),
                    entry.wins, entry.losses, entry.winrate(), entry.score));
        }
        return out;
    }
}
