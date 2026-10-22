package ru.elytrix.duelsbridge;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Несколько арен в одном готовом мире.
 *
 * Обычный EpicDuels требует по миру на арену (arena_template_&lt;имя&gt;), потому
 * что имя мира выводится из имени арены. В нашем билде EpicDuels-ElyTriX-3.1.1
 * у Arena появился общий мир: мост вызывает Arena.setDefaultWorld("duel"), и все
 * арены живут в мире duel, отличаясь только точками спавна.
 *
 * Арены создаются правкой plugins/EpicDuels/arenas.yml — без создания миров,
 * чтобы случайно не тронуть сам мир duel.
 *
 *   /duelx arena create <имя>   — завести арену
 *   /duelx arena spawn1 <имя>   — точка первой команды (стоять в мире duel)
 *   /duelx arena spawn2 <имя>   — точка второй команды
 *   /duelx arena ready <имя>    — включить арену в ротацию
 *   /duelx arena list           — что уже есть
 *   /duelx arena remove <имя>   — убрать из списка (мир не трогается)
 *   /duelx arena tp <имя>       — прыгнуть на первую точку
 */
final class Arenas {

    private final ElytrixDuelsBridge plugin;
    private final Duels duels;
    /**
     * Арена, с которой сейчас работает админ: её запоминает create/spawn,
     * поэтому родная форма «/duel arena setspawn1» без имени тоже работает.
     */
    private final java.util.Map<java.util.UUID, String> current =
            new java.util.HashMap<java.util.UUID, String>();

    Arenas(ElytrixDuelsBridge plugin, Duels duels) {
        this.plugin = plugin;
        this.duels = duels;
    }

    /** Мир, в котором живут все арены. Пусто — обычное поведение EpicDuels. */
    String sharedWorld() {
        String world = plugin.getConfig().getString("arenas.world", "");
        return world == null ? "" : world.trim();
    }

    /**
     * Включаем общий мир. @return false, если стоит старый EpicDuels без патча.
     */
    boolean setup() {
        String world = sharedWorld();
        if (world.isEmpty()) return false;
        if (Bukkit.getWorld(world) == null) {
            plugin.getLogger().warning("Арены: мир \"" + world + "\" не загружен — оставляю арены как есть.");
            return false;
        }
        try {
            Class<?> arena = Class.forName("dev.epicduels.model.Arena");
            arena.getMethod("setDefaultWorld", String.class).invoke(null, world);
        } catch (Throwable error) {
            plugin.getLogger().warning("Арены: нужен EpicDuels-ElyTriX-3.1.1.jar "
                    + "(в старом билде нет общего мира арен): " + error);
            return false;
        }
        reload();
        plugin.getLogger().info("Арены: все арены в мире \"" + world + "\", миров arena_template_* не нужно");
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
                        + "поставь их заново: /duelx arena spawn1 " + name + ", spawn2, ready");
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

    // ------------------------------------------------------------------ команды

    void command(Player player, String[] args) {
        if (player == null) return;
        if (!plugin.isAdmin(player)) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТолько администратор.");
            return;
        }
        if (args.length == 0) {
            help(player);
            return;
        }
        String action = plugin.lower(args[0]);
        String name = args.length > 1 ? args[1] : null;

        if (action.equals("list") || action.equals("список")) {
            list(player);
            return;
        }
        if (action.equals("create") || action.equals("создать")) {
            if (name == null) {
                help(player);
                return;
            }
            create(player, name);
            return;
        }
        if (action.equals("spawn1") || action.equals("setspawn1") || action.equals("точка1")) {
            name = resolve(player, name);
            if (name == null) return;
            spawn(player, name, true);
            return;
        }
        if (action.equals("spawn2") || action.equals("setspawn2") || action.equals("точка2")) {
            name = resolve(player, name);
            if (name == null) return;
            spawn(player, name, false);
            return;
        }
        // родная /duel arena save ставит ready — у нас так же
        if (action.equals("save") || action.equals("ready") || action.equals("сохранить")
                || action.equals("готово")) {
            name = resolve(player, name);
            if (name == null) return;
            ready(player, name);
            return;
        }
        if (action.equals("remove") || action.equals("delete") || action.equals("удалить")) {
            name = resolve(player, name);
            if (name == null) return;
            remove(player, name);
            return;
        }
        if (action.equals("tp") || action.equals("телепорт")) {
            name = resolve(player, name);
            if (name == null) return;
            tp(player, name);
            return;
        }
        help(player);
    }

    /**
     * Имя арены для команды без аргумента: последняя, с которой работал админ,
     * иначе единственная недостроенная, иначе единственная вообще.
     */
    private String resolve(Player player, String name) {
        if (name != null && !name.isEmpty()) return name;
        String remembered = current.get(player.getUniqueId());
        if (remembered != null) return remembered;

        Collection<?> all = all(duels.manager("getArenaManager"));
        String onlyIncomplete = null;
        String only = null;
        int incomplete = 0;
        for (Object arena : all) {
            Object value = Duels.call(arena, "getName");
            if (value == null) continue;
            String candidate = String.valueOf(value);
            only = candidate;
            if (!Boolean.TRUE.equals(Duels.call(arena, "isReady"))) {
                incomplete++;
                onlyIncomplete = candidate;
            }
        }
        if (incomplete == 1) return onlyIncomplete;
        if (all.size() == 1) return only;
        plugin.msg(player, "&#F8BEFBДуэли &8» &cУкажи арену: &f/duel arena spawn1 <имя>");
        list(player);
        return null;
    }

    private void help(Player player) {
        String world = sharedWorld().isEmpty() ? "не задан" : sharedWorld();
        plugin.msg(player, "&#F8BEFBАрены &8» &fмир: &#F8BEFB" + world);
        plugin.msg(player, "&7● &f/duelx arena create &d<имя> &7— завести арену (миров не создаёт)");
        plugin.msg(player, "&7● &f/duelx arena spawn1 &d<имя> &7— точка команды 1");
        plugin.msg(player, "&7● &f/duelx arena spawn2 &d<имя> &7— точка команды 2");
        plugin.msg(player, "&7● &f/duelx arena ready &d<имя> &7— включить в ротацию");
        plugin.msg(player, "&7● &f/duel arena list &7· &fdelete &d<имя> &7· &ftp &d<имя>");
        plugin.msg(player, "&7● &fродные &d/duel arena create|setspawn1|setspawn2|save &7тоже работают");
    }

    private void list(Player player) {
        Object manager = duels.manager("getArenaManager");
        Collection<?> all = all(manager);
        if (all.isEmpty()) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &7арен пока нет: &f/duelx arena create <имя>");
            return;
        }
        plugin.msg(player, "&#F8BEFBДуэли &8» &fарены в мире &d" + sharedWorld() + "&f: &d" + all.size());
        for (Object arena : all) {
            String name = String.valueOf(Duels.call(arena, "getName"));
            boolean ready = Boolean.TRUE.equals(Duels.call(arena, "isReady"));
            Location first = (Location) Duels.call(arena, "getSpawn1");
            Location second = (Location) Duels.call(arena, "getSpawn2");
            String points = first == null || second == null
                    ? "&cточек нет"
                    : "&7(" + Math.round(first.getX()) + " " + Math.round(first.getY()) + " "
                            + Math.round(first.getZ()) + ")";
            plugin.msg(player, (ready ? "&a● " : "&7● ") + "&f" + name + " &7— "
                    + (ready ? "&aготова " : "&cне готова ") + points);
        }
    }

    private void create(Player player, String name) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file());
            for (String key : keys(config)) {
                if (key.equalsIgnoreCase(name)) {
                    current.put(player.getUniqueId(), name);
                    plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена &d" + name
                            + "&f уже есть — ставь точки: &f/duel arena setspawn1&7, потом &fsetspawn2&7 и &fsave");
                    return;
                }
            }
            config.set(name + ".ready", false);
            config.save(file());
            current.put(player.getUniqueId(), name);
        } catch (Exception error) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНе смог записать arenas.yml: &f" + error);
            return;
        }
        reload();
        plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена &d" + name + "&f создана. "
                + "Встань на точку 1 и напиши &f/duel arena setspawn1 " + name);
    }

    private void spawn(Player player, String name, boolean first) {
        Object manager = duels.manager("getArenaManager");
        Object arena = Duels.call(manager, "getArena", name);
        if (arena == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНет арены &f" + name
                    + "&c. Сначала &f/duelx arena create " + name);
            return;
        }
        String world = sharedWorld();
        if (!world.isEmpty() && !player.getWorld().getName().equals(world)) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cТы не в мире &f" + world
                    + "&c — точки ставятся только там.");
            return;
        }
        current.put(player.getUniqueId(), name);
        Location location = player.getLocation().clone();
        Duels.call(arena, first ? "setSpawn1" : "setSpawn2", location);
        Duels.call(manager, "saveArenas");
        String point = first ? "1" : "2";
        plugin.msg(player, "&#F8BEFBДуэли &8» &fТочка &d" + point + "&f арены &d" + name
                + "&f: &d" + location.getBlockX() + " " + location.getBlockY() + " "
                + location.getBlockZ());
    }

    private void ready(Player player, String name) {
        Object manager = duels.manager("getArenaManager");
        Object arena = Duels.call(manager, "getArena", name);
        if (arena == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНет арены &f" + name);
            return;
        }
        if (Duels.call(arena, "getSpawn1") == null || Duels.call(arena, "getSpawn2") == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cСначала поставь обе точки.");
            return;
        }
        Duels.call(arena, "setReady", Boolean.TRUE);
        Duels.call(manager, "saveArenas");
        plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена &d" + name + "&f в ротации.");
    }

    private void remove(Player player, String name) {
        Object manager = duels.manager("getArenaManager");
        if (Duels.call(manager, "getArena", name) == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНет арены &f" + name);
            return;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file());
            for (String key : keys(config)) {
                if (key.equalsIgnoreCase(name)) config.set(key, null);
            }
            config.save(file());
        } catch (Exception error) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНе смог записать arenas.yml: &f" + error);
            return;
        }
        reload();
        plugin.msg(player, "&#F8BEFBДуэли &8» &fАрена &d" + name
                + "&f убрана. Мир &d" + sharedWorld() + "&f не тронут.");
    }

    private void tp(Player player, String name) {
        Object manager = duels.manager("getArenaManager");
        Object arena = Duels.call(manager, "getArena", name);
        if (arena == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cНет арены &f" + name);
            return;
        }
        Location location = (Location) Duels.call(arena, "getSpawn1");
        if (location == null || location.getWorld() == null) {
            plugin.msg(player, "&#F8BEFBДуэли &8» &cУ арены нет первой точки.");
            return;
        }
        player.teleport(location);
        plugin.msg(player, "&#F8BEFBДуэли &8» &fТелепорт на &d" + name);
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
