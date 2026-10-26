package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Очередь на бой — своя, без родной очереди EpicDuels.
 *
 * 1 на 1: два игрока с одинаковым китом встречаются сразу, как только второй
 * встал в очередь. Никаких «нажми, чтобы присоединиться»: кому надо — тот
 * встаёт в очередь сам, остальные видят объявление с кнопкой.
 *
 * 2 на 2: в очередь встают парой (напарника выбрали заранее) или поодиночке
 * («рандом»). Два одиночки автоматически становятся парой, когда находится
 * вторая пара или ещё двое одиночек.
 *
 * Время жизни: 1 на 1 — queue.timeout-seconds (3 минуты), 2 на 2 —
 * queue.team-timeout-seconds (5 минут). Если игрок ушёл с сервера, начал
 * другой бой или вышел из пары — он выпадает из очереди сам.
 */
final class Queue {

    /** Запись в очереди: одиночка или пара. */
    static final class Entry {
        final UUID owner;
        final UUID mate;
        final String kit;
        final boolean team;
        final long joinedAt;

        Entry(UUID owner, UUID mate, String kit, boolean team, long joinedAt) {
            this.owner = owner;
            this.mate = mate;
            this.kit = kit;
            this.team = team;
            this.joinedAt = joinedAt;
        }

        boolean pair() {
            return mate != null;
        }

        boolean has(UUID id) {
            return owner.equals(id) || (mate != null && mate.equals(id));
        }

        List<UUID> members() {
            List<UUID> out = new ArrayList<UUID>(2);
            out.add(owner);
            if (mate != null) out.add(mate);
            return out;
        }
    }

    private final ElytrixDuelsBridge plugin;
    private final List<Entry> entries = new ArrayList<Entry>();

    Queue(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- состояние

    boolean isQueued(UUID id) {
        return entryOf(id) != null || plugin.duels().nativeQueued(id);
    }

    Entry entryOf(UUID id) {
        synchronized (entries) {
            for (Entry entry : entries) {
                if (entry.has(id)) return entry;
            }
            return null;
        }
    }

    /** Значения для окна «Ищем бой»: {kit}, {mode}, {waited}. */
    java.util.Map<String, String> argsFor(Player player) {
        java.util.Map<String, String> args = new java.util.LinkedHashMap<String, String>();
        Entry entry = entryOf(player.getUniqueId());
        if (entry == null) {
            // игрок стоит в старой очереди самого EpicDuels — показываем её кит
            String nativeKit = plugin.duels().nativeQueuedKit(player.getUniqueId());
            if (nativeKit == null) return args;
            args.put("kit", plugin.duels().kitLabel(nativeKit));
            args.put("mode", "1 на 1");
            args.put("waited", "--:--");
            return args;
        }
        args.put("kit", entry.kit);
        args.put("mode", entry.team ? (entry.pair() ? "2 на 2, парой" : "2 на 2, рандом") : "1 на 1");
        long seconds = Math.max(0, (System.currentTimeMillis() - entry.joinedAt) / 1000L);
        args.put("waited", (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60));
        return args;
    }

    // ---------------------------------------------------------------- вход

    /** Встать в очередь одиночкой (1 на 1 или 2 на 2 «рандом»). */
    void join(Player player, String kit, boolean team) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (plugin.duels().busy(id)) {
            plugin.msg(player, msg("messages.queue-you-busy",
                    "&#F8BEFBДуэли &8» &cТы уже в бою."));
            return;
        }
        String name = plugin.duels().kitName(kit);
        if (name == null) {
            kitUnknown(player, kit);
            return;
        }
        leave(player, false);
        Entry entry = new Entry(id, null, name, team, System.currentTimeMillis());
        synchronized (entries) {
            entries.add(entry);
        }
        announceJoin(entry);
        match();
    }

    /** Встать в очередь готовой парой. */
    void joinPair(Player owner, Player mate, String kit) {
        if (owner == null || mate == null) return;
        if (plugin.duels().busy(owner.getUniqueId()) || plugin.duels().busy(mate.getUniqueId())) {
            plugin.msg(owner, msg("messages.queue-pair-busy",
                    "&#F8BEFBДуэли &8» &cКто-то из пары уже в бою."));
            return;
        }
        String name = plugin.duels().kitName(kit);
        if (name == null) {
            kitUnknown(owner, kit);
            return;
        }
        leave(owner, false);
        plugin.duels().nativeLeave(mate.getUniqueId());
        Entry entry = new Entry(owner.getUniqueId(), mate.getUniqueId(), name, true,
                System.currentTimeMillis());
        synchronized (entries) {
            entries.add(entry);
        }
        announceJoin(entry);
        match();
    }

    // --------------------------------------------------------------- выход

    /** Выйти из очереди. notify = false — молча (при старте боя или смене режима). */
    void leave(Player player, boolean notify) {
        if (player == null) return;
        Entry removed = remove(player.getUniqueId());
        // заодно чистим очередь самого EpicDuels: игрок мог попасть туда
        // старым меню, и тогда «выйти» отвечало «Ты не в очереди»
        boolean wasNative = plugin.duels().nativeLeave(player.getUniqueId());
        if (removed == null) {
            if (!wasNative) {
                if (notify) {
                    plugin.msg(player, msg("messages.queue-not-in",
                            "&#F8BEFBДуэли &8» &7Поиск уже не идёт — очередь пуста."));
                }
                return;
            }
            player.sendActionBar(Text.of(" "));
            if (notify) plugin.msg(player, msg("messages.queue-left",
                    "&#F8BEFBДуэли &8» &7Поиск отменён."));
            return;
        }
        clearActionBar(removed);
        if (!notify) return;
        String messageKey = removed.team ? "messages.queue-team-left" : "messages.queue-left";
        String fallback = removed.team
                ? "&#F8BEFBДуэли &8» &7Поиск пары отменён."
                : "&#F8BEFBДуэли &8» &7Поиск отменён.";
        String text = msg(messageKey, fallback);
        for (UUID id : removed.members()) plugin.msg(Bukkit.getPlayer(id), text);
    }

    void leave(Player player) {
        leave(player, true);
    }

    private Entry remove(UUID id) {
        synchronized (entries) {
            Iterator<Entry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (!entry.has(id)) continue;
                iterator.remove();
                return entry;
            }
            return null;
        }
    }

    private boolean remove(Entry entry) {
        synchronized (entries) {
            return entries.remove(entry);
        }
    }

    /** Игрок вышел с сервера: убираем его и сообщаем напарнику. */
    void quit(Player player) {
        UUID id = player.getUniqueId();
        plugin.duels().nativeLeave(id);
        Entry entry = remove(id);
        if (entry == null) return;
        clearActionBar(entry);
        for (UUID member : entry.members()) {
            if (member.equals(id)) continue;
            plugin.msg(Bukkit.getPlayer(member), msg("messages.queue-mate-left",
                    "&#F8BEFBДуэли &8» &7Поиск отменён: напарник вышел."));
        }
    }

    // ---------------------------------------------------------------- задачи

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                validate();
                match();
            }
        }, 20L, 20L);
    }

    /** Убираем тех, кто уже не может искать: вышел, в бою, вне пары, опоздал. */
    private void validate() {
        long now = System.currentTimeMillis();
        actionBars(now);
        List<Entry> expired = new ArrayList<Entry>();
        List<Entry> busy = new ArrayList<Entry>();
        synchronized (entries) {
            Iterator<Entry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                boolean gone = false;
                for (UUID id : entry.members()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player == null || !player.isOnline()) {
                        gone = true;
                        break;
                    }
                }
                if (gone) {
                    iterator.remove();
                    debug("игрок вышел: " + entry.owner);
                    continue;
                }
                boolean fighting = false;
                for (UUID id : entry.members()) {
                    if (plugin.duels().busy(id)) {
                        fighting = true;
                        break;
                    }
                }
                if (fighting) {
                    iterator.remove();
                    busy.add(entry);
                    debug("уже в бою: " + entry.owner);
                    continue;
                }
                long limit = (entry.team ? teamTimeout() : timeout()) * 1000L;
                if (now - entry.joinedAt >= limit) {
                    iterator.remove();
                    expired.add(entry);
                    debug("истекло время: " + entry.owner);
                }
            }
        }
        for (Entry entry : busy) {
            clearActionBar(entry);
            String text = msg("messages.queue-busy",
                    "&#F8BEFBДуэли &8» &7Поиск отменён: ты уже в бою.");
            for (UUID id : entry.members()) plugin.msg(Bukkit.getPlayer(id), text);
        }
        for (Entry entry : expired) {
            clearActionBar(entry);
            String text = msg("messages.queue-timeout",
                    "&#F8BEFBДуэли &8» &7Соперник не нашёлся за &f%minutes%&7 мин — поиск отменён.")
                    .replace("%minutes%", String.valueOf(Math.max(1,
                            (entry.team ? teamTimeout() : timeout()) / 60)));
            for (UUID id : entry.members()) plugin.msg(Bukkit.getPlayer(id), text);
        }
    }

    private long timeout() {
        return plugin.getConfig().getLong("queue.timeout-seconds", 180L);
    }

    private long teamTimeout() {
        return plugin.getConfig().getLong("queue.team-timeout-seconds", 300L);
    }

    /** Подбор: 1 на 1 — два одиночки; 2 на 2 — пары и одиночки. */
    private void match() {
        Set<String> kits = new LinkedHashSet<String>();
        synchronized (entries) {
            for (Entry entry : entries) kits.add(entry.kit);
        }
        for (String kit : kits) {
            boolean progress = true;
            while (progress) {
                progress = matchOne(kit);
            }
        }
    }

    private boolean matchOne(String kit) {
        Entry pairA = null;
        Entry pairB = null;
        List<Entry> oneVsOne = new ArrayList<Entry>();
        List<Entry> teamSolos = new ArrayList<Entry>();
        synchronized (entries) {
            for (Entry entry : entries) {
                if (!entry.kit.equalsIgnoreCase(kit)) continue;
                if (entry.pair()) {
                    if (pairA == null) pairA = entry;
                    else if (pairB == null) pairB = entry;
                } else if (entry.team) {
                    teamSolos.add(entry);
                } else {
                    oneVsOne.add(entry);
                }
            }
        }

        // 1 на 1 — два одиночки с одним китом
        if (oneVsOne.size() >= 2) {
            Entry first = oneVsOne.get(0);
            Entry second = oneVsOne.get(1);
            if (!take(first, second)) return false;
            start1v1(first, second);
            return true;
        }
        // пара против пары
        if (pairA != null && pairB != null) {
            if (!take(pairA, pairB)) return false;
            start2v2(pairA, pairB, null, null);
            return true;
        }
        // пара против двух одиночек («рандом»)
        if (pairA != null && teamSolos.size() >= 2) {
            Entry first = teamSolos.get(0);
            Entry second = teamSolos.get(1);
            if (!take(pairA, first, second)) return false;
            start2v2(pairA, null, first, second);
            return true;
        }
        // четыре одиночки — две случайные пары
        if (teamSolos.size() >= 4) {
            List<Entry> four = new ArrayList<Entry>(teamSolos.subList(0, 4));
            if (!take(four.get(0), four.get(1), four.get(2), four.get(3))) return false;
            startFourSolos(four);
            return true;
        }
        return false;
    }

    /** Убрать записи из очереди одной операцией: либо все, либо ни одной. */
    private boolean take(Entry... taken) {
        synchronized (entries) {
            for (Entry entry : taken) {
                if (!entries.contains(entry)) return false;
            }
            for (Entry entry : taken) entries.remove(entry);
            for (Entry entry : taken) {
                Component empty = Text.of(" ");
                for (UUID id : entry.members()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) player.sendActionBar(empty);
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- старт

    /** Action bar внизу экрана: кит, режим и сколько уже ищем. */
    private void actionBars(long now) {
        if (!plugin.getConfig().getBoolean("queue.action-bar", true)) return;
        String pattern = msg("messages.queue-actionbar",
                "&#F8BEFBПоиск &8» &f%kit% &7· &f%time%");
        synchronized (entries) {
            for (Entry entry : entries) {
                long seconds = Math.max(0, (now - entry.joinedAt) / 1000L);
                String time = (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
                String text = pattern.replace("%kit%", entry.kit).replace("%time%", time)
                        .replace("%mode%", entry.team ? "2 на 2" : "1 на 1");
                Component line = Text.of(text);
                for (UUID id : entry.members()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) player.sendActionBar(line);
                }
            }
        }
    }

    private void debug(String text) {
        if (plugin.getConfig().getBoolean("queue.debug", false)) {
            plugin.getLogger().info("[queue] " + text);
        }
    }

    /** Вернуть записи в очередь после неудачного старта. */
    private void requeue(Entry... back) {
        synchronized (entries) {
            for (Entry entry : back) {
                if (!entries.contains(entry)) entries.add(entry);
            }
        }
    }

    /** Вернуть игроков в очередь одиночками (2 на 2 — по парам не восстановить). */
    private void requeueIds(List<UUID> ids, String kit, boolean team) {
        long now = System.currentTimeMillis();
        synchronized (entries) {
            for (UUID id : ids) {
                boolean present = false;
                for (Entry entry : entries) {
                    if (entry.has(id)) {
                        present = true;
                        break;
                    }
                }
                if (!present) entries.add(new Entry(id, null, kit, team, now));
            }
        }
    }

    /** «Такого кита нет» + подсказка, какие есть. */
    private void kitUnknown(Player player, String kit) {
        String text = msg("messages.kit-unknown",
                "&#F8BEFBДуэли &8» &cКита &f%kit%&c нет на сервере. Есть: &f%list%")
                .replace("%kit%", kit == null ? "?" : kit);
        java.util.List<String> names = plugin.duels().kitNames();
        String list = names.isEmpty() ? "—" : String.join(", ", names);
        plugin.msg(player, text.replace("%list%", list));
    }

    /** Убрать наш action bar. */
    private void clearActionBar(Entry entry) {
        Component empty = Text.of(" ");
        for (UUID id : entry.members()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendActionBar(empty);
        }
    }

    private void start1v1(Entry first, Entry second) {
        Player a = Bukkit.getPlayer(first.owner);
        Player b = Bukkit.getPlayer(second.owner);
        if (!plugin.duels().startQueue1v1(a, b, first.kit)) {
            requeue(first, second);
            if (plugin.duels().consumeBusyRefusal()) return;
            String text = msg("messages.start-failed",
                    "&#F8BEFBДуэли &8» &cБой не стартовал (нет готовой арены?) — продолжаем искать.");
            plugin.msg(a, text);
            plugin.msg(b, text);
            return;
        }
        String pattern = msg("messages.queue-found-1v1",
                "&#F8BEFBДуэли &8» &fСоперник найден: &d%name%&f. Кит &d%kit%&f.");
        plugin.msg(a, pattern.replace("%name%", name(second.owner)).replace("%kit%", first.kit));
        plugin.msg(b, pattern.replace("%name%", name(first.owner)).replace("%kit%", first.kit));
    }

    private void start2v2(Entry pairA, Entry pairB, Entry soloA, Entry soloB) {
        List<UUID> teamA = new ArrayList<UUID>();
        List<UUID> teamB = new ArrayList<UUID>();
        if (pairA != null) {
            teamA.add(pairA.owner);
            if (pairA.mate != null) teamA.add(pairA.mate);
        }
        if (pairB != null) {
            teamB.add(pairB.owner);
            if (pairB.mate != null) teamB.add(pairB.mate);
        }
        // два одиночки всегда играют против пары, а не дополняют её
        if (soloA != null) {
            teamB.add(soloA.owner);
            if (soloB != null) teamB.add(soloB.owner);
        }
        startTeams(teamA, teamB, pairA == null ? pairB.kit : pairA.kit);
    }

    private void startFourSolos(List<Entry> four) {
        List<UUID> ids = new ArrayList<UUID>(4);
        for (Entry entry : four) ids.add(entry.owner);
        java.util.Collections.shuffle(ids);
        List<UUID> teamA = new ArrayList<UUID>(ids.subList(0, 2));
        List<UUID> teamB = new ArrayList<UUID>(ids.subList(2, 4));
        startTeams(teamA, teamB, four.get(0).kit);
    }

    private void startTeams(List<UUID> teamA, List<UUID> teamB, String kit) {
        boolean ok = plugin.duels().startQueue2v2(teamA, teamB, kit);
        List<UUID> all = new ArrayList<UUID>();
        all.addAll(teamA);
        all.addAll(teamB);
        if (!ok) {
            // бой не стартовал — возвращаем всех в поиск, чтобы не «выкидывало» из очереди
            requeueIds(all, kit, true);
            if (plugin.duels().consumeBusyRefusal()) return;
            String text = msg("messages.start-failed",
                    "&#F8BEFBДуэли &8» &cБой не стартовал (нет готовой арены?) — продолжаем искать.");
            for (UUID id : all) plugin.msg(Bukkit.getPlayer(id), text);
            return;
        }
        String text = msg("messages.queue-found-2v2",
                "&#F8BEFBДуэли &8» &fПара найдена: &d%a% + %b% &fпротив &d%c% + %d%&f.")
                .replace("%a%", name(teamA.get(0)))
                .replace("%b%", name(teamA.get(1)))
                .replace("%c%", name(teamB.get(0)))
                .replace("%d%", name(teamB.get(1)));
        for (UUID id : all) plugin.msg(Bukkit.getPlayer(id), text);
    }

    // ---------------------------------------------------------- объявления

    private void announceJoin(Entry entry) {
        Player owner = Bukkit.getPlayer(entry.owner);
        if (owner == null) return;
        String text;
        String button;
        String command;
        String hover;
        if (!entry.team) {
            text = msg("messages.queue-joined-1v1",
                    "&#F8BEFBДуэли &8» &fИщем соперника &7(кит &f%kit%&7)&f. ")
                    .replace("%kit%", entry.kit);
            button = "&c[Выйти]";
            command = "/duel queue leave";
            hover = "Больше не искать";
        } else if (entry.pair()) {
            text = msg("messages.queue-joined-2v2",
                    "&#F8BEFBДуэли &8» &fИщем вторую пару &7(кит &f%kit%&7)&f. ")
                    .replace("%kit%", entry.kit);
            button = "&c[Выйти]";
            command = "/duel queue leave";
            hover = "Отменить поиск";
        } else {
            text = msg("messages.queue-joined-solo2v2",
                    "&#F8BEFBДуэли &8» &fИщем напарника и вторую пару "
                            + "&7(кит &f%kit%&7)&f. ")
                    .replace("%kit%", entry.kit);
            button = "&c[Выйти]";
            command = "/duel queue leave";
            hover = "Отменить поиск";
        }
        Component line = Text.of(text).append(Text.button(button, command, hover));
        for (UUID id : entry.members()) Text.send(Bukkit.getPlayer(id), line);

        if (plugin.getConfig().getBoolean("queue.broadcast", true)) broadcast(entry);
    }

    /** Объявление в чат с кнопкой «встать в очередь» для остальных. */
    private void broadcast(Entry entry) {
        Player owner = Bukkit.getPlayer(entry.owner);
        if (owner == null) return;
        String text;
        String button;
        String command;
        if (!entry.team) {
            text = msg("messages.queue-broadcast-1v1",
                    "&#F8BEFBДуэли &8» &d%player% &fищет бой 1 на 1: кит &d%kit%&f. ")
                    .replace("%player%", owner.getName()).replace("%kit%", entry.kit);
            button = "&f[Встать в очередь]";
            command = "/duel queue " + entry.kit;
        } else if (entry.pair()) {
            text = msg("messages.queue-broadcast-2v2",
                    "&#F8BEFBДуэли &8» &d%player% &fищет вторую пару 2 на 2: "
                            + "кит &d%kit%&f. ")
                    .replace("%player%", owner.getName()).replace("%kit%", entry.kit);
            button = "&f[Встать в очередь]";
            command = "/duel queue team " + entry.kit;
        } else {
            text = msg("messages.queue-broadcast-solo2v2",
                    "&#F8BEFBДуэли &8» &d%player% &fищет пару 2 на 2: кит &d%kit%&f. ")
                    .replace("%player%", owner.getName()).replace("%kit%", entry.kit);
            button = "&f[Присоединиться]";
            command = "/duel queue team " + entry.kit;
        }
        Component line = Text.of(text).append(Text.button(button, command, "Нажми, чтобы встать в очередь"));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) continue;
            if (entry.has(online.getUniqueId())) continue;
            Text.send(online, line);
        }
    }

    private String name(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? "?" : player.getName();
    }

    private String msg(String path, String fallback) {
        return plugin.string(path, fallback);
    }
}
