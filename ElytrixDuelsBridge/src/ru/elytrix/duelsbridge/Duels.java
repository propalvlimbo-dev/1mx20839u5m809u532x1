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

    boolean kitExists(String name) {
        String kit = Kits.canonical(name);
        return kit != null && call(manager("getKitManager"), "getKit", kit) != null;
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
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cДанные боя потерялись, начни заново."));
            return false;
        }
        boolean tournament = "TOURNAMENT".equals(String.valueOf(mode));
        if (!feature(tournament ? "tournaments" : "team-duels")) {
            plugin.msg(player, plugin.string("messages.feature-off",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cВыключено на сервере: &f%name%")
                    .replace("%name%", tournament ? "Турниры" : "Бои 2 на 2"));
            return false;
        }
        if (!isOwner(party, id)) {
            plugin.msg(player, plugin.string("messages.owner-only",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cБой запускает только пригласивший тиммейта."));
            return false;
        }
        Object kit = call(manager("getKitManager"), "getKit", kitName);
        if (kit == null) {
            plugin.msg(player, plugin.string("messages.kit-gone",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cКит &f%kit%&c больше не существует.")
                    .replace("%kit%", String.valueOf(kitName)));
            return false;
        }
        Object members = call(party, "getMembers");
        if (members instanceof List) {
            for (Object member : (List<?>) members) {
                if (member instanceof UUID && busy((UUID) member)) {
                    plugin.msg(player, plugin.string("messages.member-busy",
                            "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cКто-то из пары уже в бою."));
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
                        "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cСначала выбери размер команд."));
                return false;
            }
            Object arenas = call(manager("getArenaManager"), "getReadyArenas");
            if (!(arenas instanceof List) || ((List<?>) arenas).isEmpty()) {
                plugin.msg(player, plugin.string("messages.no-arenas",
                        "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНет готовых арен — сообщи администратору."));
                return false;
            }
            List<?> list = (List<?>) arenas;
            Object arena = list.get(random.nextInt(list.size()));
            Object all = members instanceof List ? members : new ArrayList<Object>();
            ok = invoke(manager("getTeamDuelManager"), "startTeamDuel",
                    new Class<?>[]{List.class, size.getClass(), arena.getClass(), kit.getClass()},
                    all, size, arena, kit);
        }
        if (!ok) {
            plugin.msg(player, plugin.string("messages.start-failed",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНе удалось начать бой. Попробуй ещё раз."));
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
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cСначала выбери игрока и кит."));
            return false;
        }
        Object arenas = call(manager("getArenaManager"), "getReadyArenas");
        if (!(arenas instanceof List) || ((List<?>) arenas).isEmpty()) {
            plugin.msg(player, plugin.string("messages.no-arenas",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНет готовых арен — сообщи администратору."));
            return false;
        }
        List<?> ready = (List<?>) arenas;
        Object arena = ready.get(random.nextInt(ready.size()));
        Object name = call(arena, "getName");
        if (name == null) return false;
        call(gui(), "finishChallengeWithArena", player, String.valueOf(name));
        return true;
    }

    /** Случайная готовая арена или null. */
    Object randomArena() {
        Object arenas = call(manager("getArenaManager"), "getReadyArenas");
        if (!(arenas instanceof List) || ((List<?>) arenas).isEmpty()) return null;
        List<?> ready = (List<?>) arenas;
        return ready.get(random.nextInt(ready.size()));
    }

    /**
     * Мгновенный бой 1 на 1 из очереди: без вызова и подтверждения,
     * сразу стартуем на случайной арене.
     */
    boolean startQueue1v1(Player first, Player second, String kitName) {
        String kit = Kits.canonical(kitName);
        if (first == null || second == null || kit == null) return false;
        Object arena = randomArena();
        Object kitObject = call(manager("getKitManager"), "getKit", kit);
        if (arena == null || kitObject == null) return false;
        Object started = invokeRaw(manager("getDuelManager"), "startDirectDuel",
                new Class<?>[]{Player.class, Player.class, arena.getClass(), kitObject.getClass(), int.class},
                first, second, arena, kitObject, Integer.valueOf(1));
        return started != null;
    }

    /**
     * Бой 2 на 2 из очереди: составы задаём сами (пары не перемешиваем).
     */
    boolean startQueue2v2(java.util.List<UUID> teamA, java.util.List<UUID> teamB, String kitName) {
        if (teamA == null || teamB == null || teamA.size() != 2 || teamB.size() != 2) return false;
        String kit = Kits.canonical(kitName);
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
        Object arenas = call(manager("getArenaManager"), "getReadyArenas");
        if (size == null || kit == null || !(arenas instanceof List) || ((List<?>) arenas).isEmpty()) {
            return false;
        }
        List<?> ready = (List<?>) arenas;
        Object arena = ready.get(random.nextInt(ready.size()));
        return invoke(manager("getTeamDuelManager"), "startTeamDuel",
                new Class<?>[]{List.class, size.getClass(), arena.getClass(), kit.getClass()},
                new ArrayList<UUID>(members), size, arena, kit);
    }

    /**
     * EpicDuels перемешивает игроков и делит пополам. Сразу после старта
     * возвращаем правильные составы: пара A против пары B. Мир боя ещё
     * грузится, телепорт идёт следующим тиком — составы успевают примениться.
     */
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
