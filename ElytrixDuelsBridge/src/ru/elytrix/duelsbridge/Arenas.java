package ru.elytrix.duelsbridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Одна арена в готовом мире и сколько угодно точек спавна.
 *
 * Обычный EpicDuels требует по миру на арену. В нашем билде
 * EpicDuels-ElyTriX-3.1.2 у Arena есть общий мир, поэтому арена одна —
 * arena1 в мире duel, — а точек спавна сколько угодно. Мост хранит их в
 * spawns.yml (номер точки = строка списка). Пары идут по порядку — 1-2,
 * 3-4, 5-6, — телепорт EpicDuels подменяется на точки текущей пары.
 *
 *   /duel arena setspawn1     — поставить точку 1
 *   /duel arena setspawn2     — точку 2 (после неё арена в ротации)
 *   /duel arena setspawn3 …   — добавить ещё точки
 *   /duel arena list          — точки и координаты
 *   /duel arena tp [номер]    — прыгнуть на точку
 *   /duel arena delete <N>    — убрать точку N
 *   /duel arena clear         — убрать все точки
 */
final class Arenas implements Listener {

    private static final Pattern NUMBERED = Pattern.compile("^(?:set)?spawn(\\d+)$");
    private static final double SPREAD = 0.5;

    private final ElytrixDuelsBridge plugin;
    private final Duels duels;

    /** Пары точек на бой: имя мира-копии -> {точка команды 1, точка команды 2}. */
    private final Map<String, int[]> pairs = new HashMap<String, int[]>();
    /** Занятые пары: номер пары -> время брони. */
    private final Map<Integer, Long> reserved = new HashMap<Integer, Long>();
    /** Кто ждёт свою пару: игрок -> номер пары (на время запуска боя). */
    private final Map<java.util.UUID, Integer> pending = new HashMap<java.util.UUID, Integer>();
    /** Идущий бой -> номер пары. Освобождается, когда мир-копия выгружается. */
    private final Map<String, Integer> busy = new HashMap<String, Integer>();
    /** Куда игрок поставлен на своей паре: для возврата после падения. */
    private final Map<java.util.UUID, Location> spots = new HashMap<java.util.UUID, Location>();
    /** Сколько проверок подряд мир-копия стояла без игроков. */
    private final Map<String, Integer> idle = new HashMap<String, Integer>();
    /** Кэш spawns.yml: читаем с диска только когда файл менялся. */
    private List<Location> cached;
    private long cacheStamp = -1;

    Arenas(ElytrixDuelsBridge plugin, Duels duels) {
        this.plugin = plugin;
        this.duels = duels;
    }

    /** Патч арен уже применён (setDefaultWorld сделан). */
    private boolean applied;
    private String warnedWorld;

    /**
     * Мир, в котором живёт арена. Пусто — обычное поведение EpicDuels.
     *
     * Если в config.yml ключа нет (или он пуст), но мир duel на сервере есть,
     * работаем с ним: у владельца мир уже готов, заставлять его что-то
     * дописывать в конфиг не нужно.
     */
    String sharedWorld() {
        String world = plugin.getConfig().getString("arenas.world", "");
        world = world == null ? "" : world.trim();
        if (!world.isEmpty()) return world;
        return Bukkit.getWorld("duel") != null ? "duel" : "";
    }

    /** Применить патч арен, если он ещё не применён (мир мог загрузиться позже). */
    void ensure() {
        if (!applied) setup();
    }

    boolean ready() {
        return applied;
    }

    /**
     * Включаем общий мир. @return false, если стоит старый EpicDuels без патча.
     */
    boolean setup() {
        String world = sharedWorld();
        if (world.isEmpty()) return false;
        if (Bukkit.getWorld(world) == null) {
            if (!world.equals(warnedWorld)) {
                warnedWorld = world;
                plugin.getLogger().warning("Арены: мир \"" + world + "\" не загружен — "
                        + "как только он загрузится, арена включится сама.");
            }
            return false;
        }
        try {
            Class<?> arena = Class.forName("dev.epicduels.model.Arena");
            arena.getMethod("setDefaultWorld", String.class).invoke(null, world);
        } catch (Throwable error) {
            plugin.getLogger().warning("Арены: нужен EpicDuels-ElyTriX-3.1.2.jar "
                    + "(в старом билде нет общего мира арен): " + error);
            return false;
        }
        applied = true;
        warnedWorld = null;
        reload();
        sync();
        plugin.getLogger().info("Арены: арена " + arenaName() + " в мире \"" + world
                + "\", точек: " + points().size());
        return true;
    }

    /** Перечитать arenas.yml и привязать точки к общему миру. */
    private void reload() {
        Object manager = duels.manager("getArenaManager");
        if (manager == null) return;
        String world = sharedWorld();
        Set<String> foreign = foreignSpawns(world);
        Duels.call(manager, "loadArenas");
        for (Object arena : all(manager)) {
            Duels.call(manager, "resolveSpawnWorlds", arena);
            // точки записаны для arena_template_* — после подмены мира они
            // указывают в случайное место, такую арену не выпускаем
            String name = String.valueOf(Duels.call(arena, "getName"));
            if (!world.isEmpty() && foreign.contains(name.toLowerCase(Locale.ROOT))) {
                Duels.call(arena, "setReady", Boolean.FALSE);
                plugin.getLogger().warning("Арены: у \"" + name + "\" точки из другого мира — "
                        + "поставь их заново: /duel arena setspawn1, setspawn2");
            }
        }
        Duels.call(manager, "saveArenas");
    }

    /** Арены, у которых в arenas.yml точка записана для чужого мира. */
    private Set<String> foreignSpawns(String world) {
        Set<String> out = new java.util.HashSet<String>();
        if (world.isEmpty()) return out;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file());
            for (String key : keys(config)) {
                for (String point : new String[]{"spawn1", "spawn2"}) {
                    String stored = config.getString(key + "." + point + ".world");
                    if (stored != null && !stored.isEmpty() && !stored.equals(world)) {
                        out.add(key.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Арены: не смог прочитать arenas.yml: " + error);
        }
        return out;
    }

    private Collection<?> all(Object manager) {
        Object value = Duels.call(manager, "getAllArenas");
        return value instanceof Collection ? (Collection<?>) value : new ArrayList<Object>();
    }

    private File file() {
        File folder = plugin.getServer().getPluginManager().getPlugin("EpicDuels").getDataFolder();
        return new File(folder, "arenas.yml");
    }

    private List<String> keys(YamlConfiguration config) {
        Set<String> keys = config.getKeys(false);
        return keys == null ? new ArrayList<String>() : new ArrayList<String>(keys);
    }

    // --------------------------------------------------------------- точки арены

    /** Имя единственной арены: arenas.name в конфиге, по умолчанию arena1. */
    String arenaName() {
        String name = plugin.getConfig().getString("arenas.name", "arena1");
        name = name == null ? "" : name.trim();
        return name.isEmpty() ? "arena1" : name;
    }

    private File pointsFile() {
        return new File(plugin.getDataFolder(), "spawns.yml");
    }

    private YamlConfiguration pointsConfig() {
        return YamlConfiguration.loadConfiguration(pointsFile());
    }

    private String format(Location location) {
        return String.format(Locale.ROOT, "%.2f;%.2f;%.2f;%.2f;%.2f",
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private Location parse(String row) {
        if (row == null || row.trim().isEmpty()) return null;
        String[] parts = row.split(";");
        if (parts.length < 5) return null;
        World world = Bukkit.getWorld(sharedWorld());
        if (world == null) return null;
        try {
            return new Location(world,
                    Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Float.parseFloat(parts[3].trim()),
                    Float.parseFloat(parts[4].trim()));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** Точки по номерам: индекс 0 — точка 1, дырки остаются null. */
    List<Location> points() {
        long stamp = pointsFile().exists() ? pointsFile().lastModified() : 0;
        if (cached != null && stamp == cacheStamp) return cached;
        List<Location> out = new ArrayList<Location>();
        try {
            for (String row : pointsConfig().getStringList("points")) out.add(parse(row));
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Арена: не смог прочитать spawns.yml: " + error);
        }
        cached = out;
        cacheStamp = stamp;
        return out;
    }

    /** Сбросить кэш точек после записи файла. */
    private void dropCache() {
        cached = null;
        cacheStamp = -1;
    }

    private Location pointAt(int number) {
        if (number < 1) return null;
        List<Location> points = points();
        return number <= points.size() ? points.get(number - 1) : null;
    }

    /** Точки этой арены в нужном мире (для границ боя). */
    List<Location> pointsIn(World world) {
        List<Location> out = new ArrayList<Location>();
        if (world == null) return out;
        for (Location point : points()) {
            if (point == null) continue;
            Location copy = point.clone();
            copy.setWorld(world);
            out.add(copy);
        }
        return out;
    }

    /** Точка игрока в этом мире боя (куда его поставили), иначе null. */
    Location spot(java.util.UUID id, String world) {
        if (id == null || world == null) return null;
        Location stored = spots.get(id);
        if (stored == null || stored.getWorld() == null) return null;
        if (!world.equals(stored.getWorld().getName())) return null;
        return stored.clone();
    }

    /**
     * Точки пары, которая идёт в этом мире-копии: по ним считаются границы
     * боя. Пусто — пара ещё не выбрана, тогда годятся все точки.
     */
    List<Location> pairPointsIn(World world) {
        List<Location> out = new ArrayList<Location>(2);
        if (world == null) return out;
        int[] pair = pairs.get(world.getName());
        if (pair == null) return out;
        List<Location> points = points();
        for (int index : pair) {
            if (index < 0 || index >= points.size()) continue;
            Location point = points.get(index);
            if (point == null) continue;
            Location copy = point.clone();
            copy.setWorld(world);
            out.add(copy);
        }
        return out;
    }

    /** Первый свободный номер точки. */
    int freeNumber() {
        List<Location> points = points();
        for (int index = 0; index < points.size(); index++) {
            if (points.get(index) == null) return index + 1;
        }
        return points.size() + 1;
    }

    private void savePoints(List<Location> points) {
        List<String> rows = new ArrayList<String>();
        for (Location point : points) rows.add(point == null ? "" : format(point));
        YamlConfiguration config = pointsConfig();
        config.set("points", rows);
        try {
            config.save(pointsFile());
        } catch (Exception error) {
            plugin.getLogger().warning("Арена: не смог записать spawns.yml: " + error);
        }
        dropCache();
    }

    private void store(int number, Location location) {
        List<Location> points = new ArrayList<Location>(points());
        while (points.size() < number) points.add(null);
        points.set(number - 1, location);
        savePoints(points);
        sync();
    }

    private void clearPoint(int number) {
        List<Location> points = new ArrayList<Location>(points());
        if (number < 1 || number > points.size()) return;
        points.set(number - 1, null);
        savePoints(points);
        sync();
    }

    private void clearAll() {
        savePoints(new ArrayList<Location>());
        sync();
    }

    /**
     * Перенести точки в арену EpicDuels: точка 1 — спавн первой команды,
     * точка 2 — второй. Остальные точки нужны мосту для выбора пары на бой.
     * Другие арены из arenas.yml выключаются — арена одна.
     */
    private void sync() {
        Object manager = duels.manager("getArenaManager");
        if (manager == null) return;
        Object arena = ensureArena();
        if (arena == null) return;
        Location first = pointAt(1);
        Location second = pointAt(2);
        if (first != null) Duels.call(arena, "setSpawn1", first);
        if (second != null) Duels.call(arena, "setSpawn2", second);
        Duels.call(arena, "setReady", Boolean.valueOf(first != null && second != null));
        String name = arenaName();
        for (Object other : all(manager)) {
            Object value = Duels.call(other, "getName");
            if (value == null || String.valueOf(value).equalsIgnoreCase(name)) continue;
            if (Boolean.TRUE.equals(Duels.call(other, "isReady"))) {
                Duels.call(other, "setReady", Boolean.FALSE);
                plugin.getLogger().info("Арены: \"" + value + "\" выключена — арена одна ("
                        + name + ")");
            }
        }
        Duels.call(manager, "saveArenas");
    }

    /** Арена есть в arenas.yml? Если нет — заводим запись и перечитываем файл. */
    private Object ensureArena() {
        Object manager = duels.manager("getArenaManager");
        if (manager == null) return null;
        String name = arenaName();
        Object arena = Duels.call(manager, "getArena", name);
        if (arena != null) return arena;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file());
            if (!keys(config).contains(name)) {
                config.set(name + ".ready", Boolean.FALSE);
                config.save(file());
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Арены: не смог записать arenas.yml: " + error);
            return null;
        }
        reload();
        return Duels.call(duels.manager("getArenaManager"), "getArena", name);
    }

    // ------------------------------------------------------------------ команды

    void command(Player player, String[] args) {
        if (player == null) return;
        if (!plugin.isAdmin(player)) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТолько администратор.");
            return;
        }
        ensure();
        if (args.length == 0) {
            help(player);
            return;
        }
        String action = plugin.lower(args[0]);
        String name = args.length > 1 ? args[1] : null;

        Matcher numbered = NUMBERED.matcher(action);
        if (numbered.matches()) {
            setPoint(player, Integer.parseInt(numbered.group(1)));
            return;
        }
        if ((action.equals("setspawn") || action.equals("spawn") || action.equals("точка"))
                && name != null && name.matches("\\d+")) {
            setPoint(player, Integer.parseInt(name));
            return;
        }

        if (action.equals("list") || action.equals("список") || action.equals("points")) {
            list(player);
            return;
        }
        if (action.equals("tp") || action.equals("телепорт")) {
            tp(player, name != null && name.matches("\\d+") ? Integer.parseInt(name) : 1);
            return;
        }
        if (action.equals("delete") || action.equals("remove") || action.equals("удалить")) {
            if (name != null && name.matches("\\d+")) {
                clearPoint(player, Integer.parseInt(name));
            } else {
                plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена одна. Убрать точку: &f/duel arena delete &d<номер>");
            }
            return;
        }
        if (action.equals("clear") || action.equals("reset") || action.equals("сброс")) {
            clearAll();
            plugin.msg(player, "&#F8BEFBДуэли &8» &fВсе точки убраны. Ставь заново: &f/duel arena setspawn1");
            return;
        }
        if (action.equals("ready") || action.equals("save") || action.equals("сохранить")
                || action.equals("готово")) {
            sync();
            readyReport(player);
            return;
        }
        if (action.equals("create") || action.equals("создать")) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена одна — &d" + arenaName()
                    + "&f. Новый мир и вторая арена не нужны: просто ставь точки &f/duel arena setspawn1&7, &fsetspawn2&7…");
            return;
        }
        help(player);
    }

    private void help(Player player) {
        String world = sharedWorld().isEmpty() ? "не задан" : sharedWorld();
        plugin.msg(player, "&#F8BEFBАрена &8» &fмир: &#F8BEFB" + world
                + "&7, общий мир арен: " + (applied ? "&aвключён" : "&cвыключен"));
        plugin.msg(player, "&7● &f/duel arena setspawn1 &7· &fsetspawn2 &7— две точки, арена в ротации");
        plugin.msg(player, "&7● &f/duel arena setspawn3 &7· &fsetspawn4 … &7— ещё пара: бои идут по очереди 1-2, 3-4, 5-6");
        plugin.msg(player, "&7● &f/duel arena list &7· &ftp &d<номер> &7· &fdelete &d<номер> &7· &fclear");
        plugin.msg(player, "&7● &fточки ставятся в мире &d" + world + "&7, новые миры не создаются");
    }

    private void list(Player player) {
        plugin.msg(player, "&#F8BEFBАрена &8» &f" + arenaName() + "&7, мир &d"
                + (sharedWorld().isEmpty() ? "не задан" : sharedWorld())
                + "&7, точек: &f" + countPoints());
        List<Location> points = points();
        if (points.isEmpty()) {
            plugin.msg(player, "&7● &fточек нет: встань на место и напиши &f/duel arena setspawn1");
            return;
        }
        for (int index = 0; index < points.size(); index++) {
            Location point = points.get(index);
            if (point == null) continue;
            plugin.msg(player, "&7● &f" + (index + 1) + " &7— &f"
                    + Math.round(point.getX()) + " " + Math.round(point.getY()) + " "
                    + Math.round(point.getZ()) + " &7(поворот " + Math.round(point.getYaw()) + ")");
        }
        boolean ready = pointAt(1) != null && pointAt(2) != null;
        plugin.msg(player, "&7● &f" + (ready
                ? "&aарена в ротации"
                : "&cнужно минимум две точки: &fsetspawn1 &7и &fsetspawn2"));
        plugin.msg(player, "&7● &fпар точек: &d" + readyPairs() + "&7, занято: &d"
                + busy.size() + "&7, свободно: &d" + Math.max(0, readyPairs() - busy.size()));
    }

    private int countPoints() {
        int count = 0;
        for (Location point : points()) {
            if (point != null) count++;
        }
        return count;
    }

    private void setPoint(Player player, int number) {
        if (number < 1) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНомер точки — с 1: &f/duel arena setspawn1");
            return;
        }
        String world = sharedWorld();
        if (world.isEmpty()) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cОбщий мир арен не включён: нужен "
                    + "&fEpicDuels-ElyTriX-3.1.2.jar&c и загруженный мир &fduel&c.");
            return;
        }
        if (!player.getWorld().getName().equals(world)) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТы в мире &f" + player.getWorld().getName()
                    + "&c, а арена в &f" + world + "&c — точки ставятся только там.");
            return;
        }
        Location location = player.getLocation().clone();
        store(number, location);
        plugin.msg(player, "&#F8BEFBДуэли &8» &fТочка &d" + number + "&f: &d"
                + location.getBlockX() + " " + location.getBlockY() + " "
                + location.getBlockZ() + " &7(всего точек: &f" + countPoints() + "&7)");
        int free = freeNumber();
        if (pointAt(1) != null && pointAt(2) != null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена &d" + arenaName()
                    + "&f в ротации: пары точек идут по порядку 1-2, 3-4, 5-6. "
                    + "Ещё точка: &f/duel arena setspawn" + free);
        } else {
            plugin.msg(player, "&#F8BEFBДуэли &8» &7Нужна вторая точка: &f/duel arena setspawn"
                    + Math.max(free, 1));
        }
    }

    private void clearPoint(Player player, int number) {
        if (pointAt(number) == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТочки &f" + number + "&c нет.");
            return;
        }
        clearPoint(number);
        plugin.msg(player, "&#F8BEFBДуэли &8» &fТочка &d" + number + "&f убрана, точек: &f"
                + countPoints());
    }

    private void readyReport(Player player) {
        if (pointAt(1) == null || pointAt(2) == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cДля ротации нужно минимум две точки: &f"
                    + "/duel arena setspawn1 &7и &fsetspawn2");
            return;
        }
        plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена &d" + arenaName() + "&f в ротации, точек: &f"
                + countPoints());
    }

    private void tp(Player player, int number) {
        Location point = pointAt(number);
        if (point == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТочки &f" + number + "&c нет.");
            return;
        }
        player.teleport(point);
        plugin.msg(player, "&#F8BEFBДуэли &8» &fТелепорт на точку &d" + number);
    }

    // ------------------------------------------------- телепорт на точке (бой)

    /**
     * EpicDuels телепортирует бойцов на spawn1/spawn2 арены. Если это наша
     * арена — подменяем место на точки текущей пары (1-2, 3-4, 5-6 по
     * кругу). Смещение внутри
     * команды (2 на 2) сохраняем, только ужимаем с 1.5 до 0.5 блока.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        String world = to.getWorld().getName();
        if (!world.startsWith("arena_instance_")) return;
        if (!ourDuel(world)) return;
        Player teleported = event.getPlayer();
        if (teleported == null || !participant(world, teleported.getUniqueId())) return;
        Location first = pointAt(1);
        Location second = pointAt(2);
        if (first == null || second == null) return;

        double toFirst = horizontal(first, to);
        double toSecond = horizontal(second, to);
        Location base;
        boolean sideOne;
        if (toFirst <= toSecond && toFirst <= 4.0) {
            base = first;
            sideOne = true;
        } else if (toSecond <= 4.0) {
            base = second;
            sideOne = false;
        } else {
            return;
        }

        int[] pair = pair(world, teleported.getUniqueId());
        if (pair == null) return;
        List<Location> points = points();
        Location point = points.get(sideOne ? pair[0] : pair[1]);

        double dx = to.getX() - base.getX();
        double dz = to.getZ() - base.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 1.0E-4) {
            dx = dx / length * SPREAD;
            dz = dz / length * SPREAD;
        } else {
            dx = 0;
            dz = 0;
        }
        Location target = new Location(to.getWorld(), point.getX() + dx, point.getY(),
                point.getZ() + dz, point.getYaw(), point.getPitch());
        event.setTo(target);
        if (spots.size() > 256) spots.clear();
        spots.put(teleported.getUniqueId(), target.clone());
    }

    /**
     * Пара точек на бой: считается один раз на мир-копию, у всех игроков одна.
     * Пары идут строго по порядку — 1 и 2, потом 3 и 4, потом 5 и 6, — и
     * после последней снова с первой. Никакого рандома.
     */
    private int[] pair(String world, java.util.UUID player) {
        List<Location> points = points();
        List<Integer> ready = completePairs(points);
        if (ready.isEmpty()) return null;
        int[] pair = pairs.get(world);
        if (pair != null && pair[0] + 1 < points.size()
                && points.get(pair[0]) != null && points.get(pair[1]) != null) {
            return pair;
        }
        if (pairs.size() > 256) pairs.clear();
        Integer number = busy.get(world);
        if (number == null) {
            number = player == null ? null : pending.remove(player);
            if (number == null || number.intValue() < 1 || number.intValue() > ready.size()) {
                number = Integer.valueOf(freePair(ready.size()));
            }
            if (number.intValue() < 1) number = Integer.valueOf(1);
            busy.put(world, number);
            clearPending(number.intValue());
        }
        int start = ready.get((number.intValue() - 1) % ready.size()).intValue();
        pair = new int[]{start, start + 1};
        pairs.put(world, pair);
        return pair;
    }

    /** Начала полных пар точек: 1-2, 3-4, 5-6… */
    private List<Integer> completePairs(List<Location> points) {
        List<Integer> ready = new ArrayList<Integer>();
        for (int start = 0; start + 1 < points.size(); start += 2) {
            if (points.get(start) != null && points.get(start + 1) != null) {
                ready.add(Integer.valueOf(start));
            }
        }
        return ready;
    }

    /** Сколько готовых пар точек. */
    int readyPairs() {
        return completePairs(points()).size();
    }

    /** Все пары заняты идущими боями? */
    boolean full() {
        purge();
        int count = readyPairs();
        return count == 0 || freePair(count) == 0;
    }

    /**
     * Занять свободную пару под бой. @return номер пары или 0, если все заняты.
     */
    int reserve(java.util.List<java.util.UUID> players) {
        purge();
        int count = readyPairs();
        if (count == 0) return 0;
        int number = freePair(count);
        if (number == 0) return 0;
        reserved.put(Integer.valueOf(number), Long.valueOf(System.currentTimeMillis()));
        if (players != null) {
            for (java.util.UUID id : players) {
                if (id != null) pending.put(id, Integer.valueOf(number));
            }
        }
        return number;
    }

    /** Бой не стартовал — вернуть бронь. */
    void release(java.util.List<java.util.UUID> players) {
        if (players == null) return;
        for (java.util.UUID id : players) {
            Integer number = pending.remove(id);
            if (number != null && !busy.containsValue(number)) reserved.remove(number);
        }
    }

    private int freePair(int count) {
        java.util.Set<Integer> taken = new java.util.HashSet<Integer>(reserved.keySet());
        taken.addAll(busy.values());
        for (int number = 1; number <= count; number++) {
            if (!taken.contains(Integer.valueOf(number))) return number;
        }
        return 0;
    }

    private void clearPending(int number) {
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, Integer>> iterator =
                pending.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().intValue() == number) iterator.remove();
        }
    }

    /** Бронь без боя (запуск не удался) не должна занимать арену вечно. */
    private void purge() {
        long now = System.currentTimeMillis();
        java.util.Iterator<java.util.Map.Entry<Integer, Long>> iterator =
                reserved.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<Integer, Long> entry = iterator.next();
            if (now - entry.getValue().longValue() < 120000L) continue;
            iterator.remove();
            clearPending(entry.getKey().intValue());
        }
    }

    /** Мир-копия выгружен (бой кончился) — пара снова свободна. */
    @EventHandler
    public void onWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        if (event.getWorld() == null) return;
        releaseWorld(event.getWorld().getName());
    }

    /** Раз в 5 секунд: страховка от навсегда занятых пар. */
    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                sweep();
            }
        }, 100L, 100L);
    }

    /**
     * Мир-копия удалена без выгрузки или давно пуста — освобождаем пару.
     * Идущий бой всегда держит хотя бы одного игрока в мире, поэтому
     * 30 секунд без игроков означают, что пару можно отдавать.
     */
    private void sweep() {
        purge();
        if (busy.isEmpty()) return;
        java.util.Set<String> occupied = new java.util.HashSet<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != null) occupied.add(player.getWorld().getName());
        }
        for (String world : new ArrayList<String>(busy.keySet())) {
            if (Bukkit.getWorld(world) == null) {
                releaseWorld(world);
                idle.remove(world);
                continue;
            }
            if (occupied.contains(world)) {
                idle.remove(world);
                continue;
            }
            Integer seen = idle.get(world);
            int next = seen == null ? 1 : seen.intValue() + 1;
            if (next >= 6) {
                releaseWorld(world);
                idle.remove(world);
            } else {
                idle.put(world, Integer.valueOf(next));
            }
        }
        idle.keySet().retainAll(busy.keySet());
    }

    private void releaseWorld(String world) {
        Integer number = busy.remove(world);
        pairs.remove(world);
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, Location>> iterator =
                spots.entrySet().iterator();
        while (iterator.hasNext()) {
            Location value = iterator.next().getValue();
            if (value == null || value.getWorld() == null
                    || value.getWorld().getName().equals(world)) {
                iterator.remove();
            }
        }
        if (number == null) return;
        reserved.remove(number);
        clearPending(number.intValue());
    }

    private double horizontal(Location one, Location two) {
        double dx = one.getX() - two.getX();
        double dz = one.getZ() - two.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Игрок реально участник этого боя (а не спектатор). */
    private boolean participant(String world, java.util.UUID id) {
        if (id == null) return false;
        Object duel = Duels.call(duels.manager("getDuelManager"), "getDuel", id);
        if (duel != null && world.equals(String.valueOf(
                Duels.call(duel, "getInstanceWorldName")))) {
            return true;
        }
        Object team = Duels.call(duels.manager("getTeamDuelManager"), "getTeamDuelOf", id);
        return team != null && world.equals(String.valueOf(
                Duels.call(team, "getInstanceWorldName")));
    }

    /** Бой в этом мире идёт на нашей арене? */
    private boolean ourDuel(String world) {
        Object duel = Duels.call(duels.manager("getDuelManager"), "getDuelByWorld", world);
        Object name = duel == null ? null : Duels.call(duel, "getArenaName");
        if (name == null) {
            Object team = Duels.call(duels.manager("getTeamDuelManager"), "getByWorld", world);
            name = team == null ? null : Duels.call(team, "getArenaName");
        }
        return name != null && arenaName().equalsIgnoreCase(String.valueOf(name));
    }

    /** Готовые арены в нижнем регистре — для проверок перед запуском. */
    static List<String> names(Collection<?> arenas) {
        List<String> out = new ArrayList<String>();
        for (Object arena : arenas) {
            Object name = Duels.call(arena, "getName");
            if (name != null) out.add(String.valueOf(name).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
