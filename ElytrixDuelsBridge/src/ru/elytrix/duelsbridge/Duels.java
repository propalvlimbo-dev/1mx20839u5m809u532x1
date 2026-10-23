package ru.elytrix.duelsbridge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Все обращения к EpicDuels — через рефлексию: у каждого плагина свой
 * ClassLoader, поэтому классы EpicDuels напрямую не видны.
 */
final class Duels {

    private final ElytrixDuelsBridge plugin;
    private final Random random = new Random();

    Duels(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- базовое

    Object epic() {
        return Bukkit.getPluginManager().getPlugin("EpicDuels");
    }

    boolean available() {
        return epic() != null;
    }

    Object gui() {
        return call(epic(), "getGUIManager");
    }

    Object manager(String getter) {
        return call(epic(), getter);
    }

    /**
     * EpicDuels держит слепок инвентаря (backup) с начала боя до возврата в
     * лобби и на это время глушит все команды. Так узнаём это состояние.
     */
    boolean hasBackup(Player player) {
        if (player == null) return false;
        Object manager = manager("getInventoryBackupManager");
        if (manager == null) return inDuel(player);
        Object value = call(manager, "hasBackup", player.getUniqueId());
        if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        return inDuel(player);
    }

    /** Идёт ли у игрока бой. */
    boolean inDuel(Player player) {
        if (player == null) return false;
        Object duel = call(manager("getDuelManager"), "getDuel", player.getUniqueId());
        if (duel == null) {
            duel = call(manager("getTeamDuelManager"), "getTeamDuelOf", player.getUniqueId());
        }
        return duel != null;
    }

    static Object call(Object target, String method, Object... args) {
        if (target == null) return null;
        try {
            Method found = null;
            for (Method candidate : target.getClass().getMethods()) {
                if (!candidate.getName().equals(method)) continue;
                if (candidate.getParameterCount() != args.length) continue;
                found = candidate;
                break;
            }
            if (found == null) return null;
            found.setAccessible(true);
            return found.invoke(target, args);
        } catch (Throwable error) {
            return null;
        }
    }

    boolean feature(String name) {
        Object value = call(epic(), "isFeatureEnabled", name);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    boolean busy(UUID id) {
        Object value = call(manager("getDuelManager"), "isBusy", id);
        return value instanceof Boolean && (Boolean) value;
    }

    // --------------------------------------------------------- статистика

    /** Значения для меню BMenu: {wins}, {losses}, {winrate}, {score}. */
    Map<String, String> stats(UUID id) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Object manager = manager("getStatsManager");
        Object stats = call(manager, "getStats", id);
        out.put("wins", value(stats, "getWins", "0"));
        out.put("losses", value(stats, "getLosses", "0"));
        Object rate = call(stats, "getWinRate");
        out.put("winrate", rate instanceof Number
                ? String.format("%.1f", ((Number) rate).doubleValue()) : "0.0");
        Object score = null;
        if (manager != null && stats != null) {
            try {
                Method method = manager.getClass().getMethod("calculateScore", stats.getClass());
                method.setAccessible(true);
                score = method.invoke(null, stats);
            } catch (Throwable ignored) {
                // ниже подставим прочерк
            }
        }
        out.put("score", score == null ? "0" : String.valueOf(score));
        return out;
    }

    private String value(Object target, String method, String fallback) {
        Object result = call(target, method);
        return result == null ? fallback : String.valueOf(result);
    }

    // ------------------------------------------------------------- группа

    Object partyOf(UUID id) {
        return call(manager("getPartyManager"), "getPartyOf", id);
    }

    /** Создаёт пару или возвращает текущую, если игрок уже в ней. */
    Object ownParty(Player player) {
        Object party = partyOf(player.getUniqueId());
        if (party == null) {
            party = call(manager("getPartyManager"), "createParty", player);
        }
        return party;
    }

    boolean addMember(Object party, UUID id) {
        Object value = call(party, "addMember", id);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    int partySize(Object party) {
        Object value = call(party, "size");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    boolean isOwner(Object party, UUID id) {
        Object value = call(party, "isOwner", id);
        return value instanceof Boolean && (Boolean) value;
    }

    void clearFlow(UUID id) {
        call(gui(), "clearPartyFlow", id);
    }

    /** Настройка потока пары: режим, размер (если есть), кит. */
    void flowMode(UUID id, String modeName) {
        Object gui = gui();
        Class<?> modeClass = secondArg(gui, "setPartyFlowMode");
        if (modeClass == null) return;
        call(gui, "setPartyFlowMode", id, enumOf(modeClass, modeName));
    }

    void flowSize(UUID id, String sizeName) {
        Object gui = gui();
        Class<?> sizeClass = secondArg(gui, "setPartyFlowTeamSize");
        if (sizeClass == null) return;
        call(gui, "setPartyFlowTeamSize", id, enumOf(sizeClass, sizeName));
    }

    void flowKit(UUID id, String kit) {
        call(gui(), "setPartyFlowKit", id, kit);
    }

    Object flowModeValue(UUID id) {
        return call(gui(), "getPartyFlowMode", id);
    }

    Object flowSizeValue(UUID id) {
        return call(gui(), "getPartyFlowTeamSize", id);
    }

    Object flowKitValue(UUID id) {
        return call(gui(), "getPartyFlowKit", id);
    }

    /**
     * Кит по любому имени: русскому, английскому или как в kits.yml.
     * Null — такого кита нет.
     */
    Object kitOf(String name) {
        if (name == null) return null;
        Object manager = manager("getKitManager");
        if (manager == null) return null;
        String clean = name.trim();
        String[] tries = new String[]{
                clean,
                Kits.canonical(clean),
                Kits.legacyOf(clean)
        };
        for (String candidate : tries) {
            if (candidate == null || candidate.isEmpty()) continue;
            Object kit = call(manager, "getKit", candidate);
            if (kit != null) return kit;
        }
        return null;
    }

    /** Имя кита так, как оно записано в kits.yml (для сообщений и очереди). */
    String kitName(String name) {
        Object kit = kitOf(name);
        if (kit == null) return null;
        Object actual = call(kit, "getName");
        return actual == null ? Kits.canonical(name) : String.valueOf(actual);
    }

    boolean kitExists(String name) {
        return kitOf(name) != null;
    }

    /** Все киты сервера — чтобы подсказать, если игрок выбрал несуществующий. */
    java.util.List<String> kitNames() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        Object value = call(manager("getKitManager"), "getKitNames");
        if (!(value instanceof java.util.List)) return out;
        for (Object name : (java.util.List<?>) value) {
            if (name != null) out.add(String.valueOf(name));
        }
        return out;
    }

    // ------------------------------------------------- очередь самого EpicDuels

    /** Игрок стоит в очереди EpicDuels (её же показывает его прежнее меню). */
    boolean nativeQueued(UUID id) {
        Object value = call(manager("getQueueManager"), "isInQueue", id);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    /** Кит, с которым игрок стоит в очереди EpicDuels. */
    String nativeQueuedKit(UUID id) {
        Object value = call(manager("getQueueManager"), "getQueuedKit", id);
        return value == null ? null : String.valueOf(value);
    }

    /** Убрать игрока из очереди EpicDuels: true — он там был. */
    boolean nativeLeave(UUID id) {
        Object value = call(manager("getQueueManager"), "leaveQueue", id);
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    /** Кит, за который сейчас воюет игрок (обычный бой или 2 на 2). */
    String kitNameOf(UUID id) {
        Object duel = call(manager("getDuelManager"), "getDuel", id);
        if (duel == null) duel = call(manager("getTeamDuelManager"), "getTeamDuelOf", id);
        if (duel == null) return null;
        Object kit = call(duel, "getKitName");
        return kit == null ? null : String.valueOf(kit);
    }

    /** Подпись кита для сообщений: русское имя, если оно известно. */
    String kitLabel(String name) {
        String kit = Kits.canonical(name);
        return kit == null ? String.valueOf(name) : kit;
    }

    /** Запуск боя по текущему потоку (аналог кнопки подтверждения). */
    boolean startFlow(Player player) {
        UUID id = player.getUniqueId();
        Object mode = flowModeValue(id);
        Object kitName = flowKitValue(id);
        Object party = partyOf(id);
        if (mode == null || kitName == null || party == null) {
            plugin.msg(player, plugin.string("messages.missing-data",
                    "&#F8BEFBДуэли &8» &cДанные боя потерялись, начни заново."));
            return false;
        }
        boolean tournament = "TOURNAMENT".equals(String.valueOf(mode));
        if (!feature(tournament ? "tournaments" : "team-duels")) {
            plugin.msg(player, plugin.string("messages.feature-off",
                    "&#F8BEFBДуэли &8» &cВыключено на сервере: &f%name%")
                    .replace("%name%", tournament ? "Турниры" : "Бои 2 на 2"));
            return false;
        }
        if (!isOwner(party, id)) {
            plugin.msg(player, plugin.string("messages.owner-only",
                    "&#F8BEFBДуэли &8» &cБой запускает только пригласивший тиммейта."));
            return false;
        }
        // в потоке лежит русское имя кита («Сумо»), а KitManager знает только
        // английский id — переводим через kitOf, иначе «Кит не найден»
        Object kit = kitOf(String.valueOf(kitName));
        if (kit == null) {
            plugin.msg(player, plugin.string("messages.kit-gone",
                    "&#F8BEFBДуэли &8» &cКит &f%kit%&c больше не существует.")
                    .replace("%kit%", kitLabel(String.valueOf(kitName))));
            return false;
        }
        Object members = call(party, "getMembers");
        if (members instanceof List) {
            for (Object member : (List<?>) members) {
                if (member instanceof UUID && busy((UUID) member)) {
                    plugin.msg(player, plugin.string("messages.member-busy",
                            "&#F8BEFBДуэли &8» &cКто-то из пары уже в бою."));
                    return false;
                }
            }
        }
        boolean ok;
        if (tournament) {
            ok = invoke(manager("getTournamentManager"), "startTournament",
                    new Class<?>[]{party.getClass(), kit.getClass()}, party, kit);
        } else {
            Object size = flowSizeValue(id);
            if (size == null) {
                plugin.msg(player, plugin.string("messages.no-team-size",
                        "&#F8BEFBДуэли &8» &cСначала выбери размер команд."));
                return false;
            }
            List<Object> list = readyArenas();
            if (list.isEmpty()) {
                plugin.msg(player, plugin.string("messages.no-arenas",
                        "&#F8BEFBДуэли &8» &cНет готовых арен — сообщи администратору.")
                        .replace("%arenas%", arenasReport()));
                plugin.getLogger().warning("Арены: запуск боя невозможен — " + arenasReport());
                return false;
            }
            Object all = members instanceof List ? members : new ArrayList<Object>();
            List<UUID> team = ids(all);
            if (plugin.arenas().reserve(team) == 0) {
                busy(team);
                return false;
            }
            Object arena = list.get(random.nextInt(list.size()));
            ok = invoke(manager("getTeamDuelManager"), "startTeamDuel",
                    new Class<?>[]{List.class, size.getClass(), arena.getClass(), kit.getClass()},
                    all, size, arena, kit);
            if (!ok) plugin.arenas().release(team);
            else fixPartyTeams(team, size);
        }
        if (!ok) {
            plugin.msg(player, plugin.string("messages.start-failed",
                    "&#F8BEFBДуэли &8» &cНе удалось начать бой. Попробуй ещё раз."));
            return false;
        }
        clearFlow(id);
        return true;
    }

    /**
     * Вызов игрока на случайную готовую арену — без окна выбора карты.
     * Кит и раунды уже лежат в GUI-потоке (их ставит openRoundsSelect).
     */
    boolean finishChallenge(Player player) {
        UUID id = player.getUniqueId();
        if (call(gui(), "getChallengeTarget", id) == null
                || call(gui(), "getChallengeKit", id) == null) {
            plugin.msg(player, plugin.string("messages.no-challenge",
                    "&#F8BEFBДуэли &8» &cСначала выбери игрока и кит."));
            return false;
        }
        List<Object> ready = readyArenas();
        if (ready.isEmpty()) {
            plugin.msg(player, plugin.string("messages.no-arenas",
                    "&#F8BEFBДуэли &8» &cНет готовых арен — сообщи администратору.")
                    .replace("%arenas%", arenasReport()));
            plugin.getLogger().warning("Арены: запуск боя невозможен — " + arenasReport());
            return false;
        }
        if (plugin.arenas().full()) {
            plugin.msg(player, busyText());
            return false;
        }
        Object arena = ready.get(random.nextInt(ready.size()));
        Object name = call(arena, "getName");
        if (name == null) return false;
        call(gui(), "finishChallengeWithArena", player, String.valueOf(name));
        return true;
    }

    /**
     * Готовые арены. Если EpicDuels не видит ни одной, но у арен есть обе
     * точки спавна, включаем их сами: точки = арена готова, и забытый
     * /duel arena save не должен ломать запуск боя.
     */
    List<Object> readyArenas() {
        Object manager = manager("getArenaManager");
        List<Object> ready = rows(call(manager, "getReadyArenas"));
        if (!ready.isEmpty()) return ready;

        Object all = call(manager, "getAllArenas");
        if (!(all instanceof java.util.Collection)) return ready;
        List<Object> promoted = new ArrayList<Object>();
        for (Object arena : (java.util.Collection<?>) all) {
            if (arena == null) continue;
            if (call(arena, "getSpawn1") == null || call(arena, "getSpawn2") == null) continue;
            call(arena, "setReady", Boolean.TRUE);
            promoted.add(arena);
            plugin.getLogger().info("Арены: \"" + call(arena, "getName")
                    + "\" включена сама — у неё есть обе точки спавна");
        }
        if (!promoted.isEmpty()) call(manager, "saveArenas");
        return promoted;
    }

    /** Что с аренами: короткая строка для сообщения админу. */
    String arenasReport() {
        Object all = call(manager("getArenaManager"), "getAllArenas");
        if (!(all instanceof java.util.Collection)) return "";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Object arena : (java.util.Collection<?>) all) {
            if (arena == null) continue;
            count++;
            if (out.length() > 0) out.append("&7, &f");
            out.append(String.valueOf(call(arena, "getName"))).append("&7 — ");
            boolean first = call(arena, "getSpawn1") != null;
            boolean second = call(arena, "getSpawn2") != null;
            if (!first && !second) out.append("&cнет точек");
            else if (!first) out.append("&cнет точки 1");
            else if (!second) out.append("&cнет точки 2");
            else out.append("&aготова");
        }
        if (count == 0) return "&cарен нет вовсе";
        return out.toString();
    }

    private static List<Object> rows(Object value) {
        List<Object> out = new ArrayList<Object>();
        if (value instanceof java.util.Collection) {
            for (Object row : (java.util.Collection<?>) value) {
                if (row != null) out.add(row);
            }
        }
        return out;
    }

    /** Случайная готовая арена или null. */
    /** Последний отказ был из-за занятых арен — очередь не должна писать своё. */
    private boolean busyRefusal;

    boolean consumeBusyRefusal() {
        boolean value = busyRefusal;
        busyRefusal = false;
        return value;
    }

    private static java.util.List<UUID> ids(Object value) {
        java.util.List<UUID> out = new ArrayList<UUID>();
        if (value instanceof Iterable) {
            for (Object row : (Iterable<?>) value) {
                if (row instanceof UUID) out.add((UUID) row);
            }
        }
        return out;
    }

    private final java.util.Map<UUID, Long> busyNotified = new java.util.HashMap<UUID, Long>();

    private void busy(List<UUID> players) {
        busyRefusal = true;
        long now = System.currentTimeMillis();
        if (busyNotified.size() > 512) busyNotified.clear();
        for (UUID id : players) {
            if (id == null) continue;
            Long last = busyNotified.get(id);
            if (last != null && now - last.longValue() < 15000L) continue;
            busyNotified.put(id, Long.valueOf(now));
            plugin.msg(Bukkit.getPlayer(id), busyText());
        }
    }

    String busyText() {
        return plugin.string("messages.arenas-busy",
                "&#F8BEFBДуэли &8» &cВсе арены заняты — подожди, скоро освободится.");
    }

    Object randomArena() {
        // readyArenas() сам включает арену, у которой есть обе точки спавна
        List<Object> ready = readyArenas();
        if (ready.isEmpty()) return null;
        return ready.get(random.nextInt(ready.size()));
    }

    /**
     * Мгновенный бой 1 на 1 из очереди: без вызова и подтверждения,
     * сразу стартуем на случайной арене.
     */
    boolean startQueue1v1(Player first, Player second, String kitName) {
        if (first == null || second == null) return false;
        Object arena = randomArena();
        Object kitObject = kitOf(kitName);
        if (arena == null || kitObject == null) return false;
        List<UUID> pair = new ArrayList<UUID>();
        pair.add(first.getUniqueId());
        pair.add(second.getUniqueId());
        if (plugin.arenas().reserve(pair) == 0) {
            busy(pair);
            return false;
        }
        Object started = invokeRaw(manager("getDuelManager"), "startDirectDuel",
                new Class<?>[]{Player.class, Player.class, arena.getClass(), kitObject.getClass(), int.class},
                first, second, arena, kitObject, Integer.valueOf(1));
        if (started == null) {
            plugin.arenas().release(pair);
            return false;
        }
        return true;
    }

    /**
     * Бой 2 на 2 из очереди: составы задаём сами (пары не перемешиваем).
     */
    boolean startQueue2v2(java.util.List<UUID> teamA, java.util.List<UUID> teamB, String kitArg) {
        if (teamA == null || teamB == null || teamA.size() != 2 || teamB.size() != 2) return false;
        String kit = kitName(kitArg);
        if (kit == null) return false;
        java.util.List<UUID> all = new ArrayList<UUID>(teamA);
        all.addAll(teamB);
        if (!startTeamDuel(all, "TWO_VS_TWO", kit)) return false;
        fixTeams(teamA.get(0), teamA.get(1), teamB.get(0), teamB.get(1));
        return true;
    }

    /**
     * Запуск командного боя из готового списка игроков (для очереди пар).
     * Порядок: первая пара — команда A, вторая — команда B (см. fixTeams).
     */
    boolean startTeamDuel(List<UUID> members, String sizeName, String kitName) {
        Object gui = gui();
        Class<?> sizeClass = secondArg(gui, "setPartyFlowTeamSize");
        Object size = sizeClass == null ? null : enumOf(sizeClass, sizeName);
        Object kit = call(manager("getKitManager"), "getKit", kitName);
        List<Object> ready = readyArenas();
        if (size == null || kit == null || ready.isEmpty()) return false;
        if (plugin.arenas().reserve(members) == 0) {
            busy(members);
            return false;
        }
        Object arena = ready.get(random.nextInt(ready.size()));
        boolean ok = invoke(manager("getTeamDuelManager"), "startTeamDuel",
                new Class<?>[]{List.class, size.getClass(), arena.getClass(), kit.getClass()},
                new ArrayList<UUID>(members), size, arena, kit);
        if (!ok) plugin.arenas().release(members);
        return ok;
    }

    /**
     * EpicDuels перемешивает игроков и делит пополам. Сразу после старта
     * возвращаем правильные составы: пара A против пары B. Мир боя ещё
     * грузится, телепорт идёт следующим тиком — составы успевают примениться.
     */
    /**
     * 2 на 2 из меню партии: EpicDuels мешает состав случайно, поэтому пару
     * снова ставим вместе — своя пара против двух других.
     */
    private void fixPartyTeams(List<UUID> members, Object size) {
        Object total = call(size, "getTotalPlayers");
        if (!(total instanceof Number) || ((Number) total).intValue() != 4) return;
        if (members.size() < 4) return;
        List<UUID> four = new ArrayList<UUID>(members.subList(0, 4));
        UUID first = null;
        UUID second = null;
        for (UUID id : four) {
            UUID mate = plugin.teams().partnerOf(id);
            if (mate == null || mate.equals(id) || !four.contains(mate)) continue;
            first = id;
            second = mate;
            break;
        }
        List<UUID> teamA = new ArrayList<UUID>(2);
        List<UUID> teamB = new ArrayList<UUID>(2);
        if (first != null) {
            teamA.add(first);
            teamA.add(second);
            for (UUID id : four) {
                if (!teamA.contains(id)) teamB.add(id);
            }
        } else {
            teamA.add(four.get(0));
            teamA.add(four.get(1));
            teamB.add(four.get(2));
            teamB.add(four.get(3));
        }
        if (teamA.size() != 2 || teamB.size() != 2) return;
        fixTeams(teamA.get(0), teamA.get(1), teamB.get(0), teamB.get(1));
    }

    boolean fixTeams(UUID a1, UUID a2, UUID b1, UUID b2) {
        Object instance = call(manager("getTeamDuelManager"), "getTeamDuelOf", a1);
        if (instance == null) return false;
        rewrite(call(instance, "getTeamA"), a1, a2);
        rewrite(call(instance, "getTeamB"), b1, b2);
        rewrite(call(instance, "getAliveA"), a1, a2);
        rewrite(call(instance, "getAliveB"), b1, b2);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void rewrite(Object target, UUID first, UUID second) {
        if (!(target instanceof java.util.Collection)) return;
        java.util.Collection<UUID> set = (java.util.Collection<UUID>) target;
        set.clear();
        set.add(first);
        set.add(second);
    }

    private Object invokeRaw(Object target, String method, Class<?>[] types, Object... args) {
        if (target == null) return null;
        try {
            Method found = target.getClass().getMethod(method, types);
            found.setAccessible(true);
            return found.invoke(target, args);
        } catch (Throwable error) {
            plugin.getLogger().warning("EpicDuels." + method + ": " + error);
            return null;
        }
    }

    private boolean invoke(Object target, String method, Class<?>[] types, Object... args) {
        if (target == null) return false;
        try {
            Method found = target.getClass().getMethod(method, types);
            found.setAccessible(true);
            Object result = found.invoke(target, args);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable error) {
            plugin.getLogger().warning("EpicDuels." + method + ": " + error);
            return false;
        }
    }

    private static Object enumOf(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        return null;
    }

    private static Class<?> secondArg(Object target, String method) {
        if (target == null) return null;
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(method) && candidate.getParameterCount() == 2) {
                return candidate.getParameterTypes()[1];
            }
        }
        return null;
    }
}
