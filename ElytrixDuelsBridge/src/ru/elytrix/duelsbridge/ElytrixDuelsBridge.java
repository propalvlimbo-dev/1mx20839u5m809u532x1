package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Подменяет окна EpicDuels на меню BMenu и на своё окно выбора игрока.
 *
 * Плагин не трогает java-классы EpicDuels: он слушает события Bukkit,
 * определяет, какое окно собирается открыть EpicDuels (по MenuHolder),
 * отменяет открытие и показывает наше меню. Значения для меню
 * ({wins}, {target}, ...) передаются прямо в BMenu, без PlaceholderAPI.
 */
public final class ElytrixDuelsBridge extends JavaPlugin implements Listener {

    private static final String HOLDER = "dev.epicduels.gui.MenuHolder";

    /** Значения по умолчанию: страхуют, если ключа нет в старом config.yml. */
    private static final Map<String, String> DEFAULTS = new HashMap<String, String>();

    static {
        DEFAULTS.put("MAIN", "elytrix:duel");
        DEFAULTS.put("STATS", "elytrix:duel_stats");
        DEFAULTS.put("MATCHMAKING", "elytrix:duel_queue");
        DEFAULTS.put("KIT_SELECT", "elytrix:duel_kit");
        DEFAULTS.put("ROUNDS_SELECT", "elytrix:duel_rounds");
        DEFAULTS.put("PARTY_CONFIRM", "elytrix:duel_confirm");
    }

    private static final Set<String> DUEL_SUBCOMMANDS = new HashSet<String>(Arrays.asList(
            "menu", "duels", "matchmaking", "stats", "challenge", "accept", "deny", "cancel",
            "queue", "kits", "spectate", "forfeit", "rematch", "toggle", "leaderboard",
            "arena", "kit", "setlobby", "lobby", "reload", "help"));

    private final Duels duels = new Duels(this);
    private final Menus menus = new Menus(this);
    private final Kits kits = new Kits(this);
    private final ArenaRules arenaRules = new ArenaRules(this, duels);
    private final Teams teams = new Teams(this);
    private final Queue queue = new Queue(this);
    private final Arenas arenas = new Arenas(this, duels);
    /** Оригинальные обработчики /duel из EpicDuels — нужны админам и /duelx. */
    private PluginCommand duelCommand;
    private DuelXCommand bridgeExecutor;
    private CommandExecutor duelExecutor;
    private TabCompleter duelCompleter;
    /** Кого игрок выбрал в своём окне (до выбора кита). */
    private final Map<UUID, UUID> targets = new HashMap<UUID, UUID>();
    /**
     * Что делать после выбора кита: TEAM_PAIR — встать в очередь парой,
     * TEAM_SOLO — 2 на 2 «рандом», QUEUE_1V1 — очередь 1 на 1.
     * Нет записи — вызов игрока или старый поток EpicDuels.
     */
    private final Map<UUID, String> intents = new HashMap<UUID, String>();

    // ------------------------------------------------------------- включение

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // дописываем недостающие ключи в уже существующий config.yml,
        // не трогая то, что там уже стоит
        try {
            FileConfiguration config = getConfig();
            boolean changed = false;
            for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
                String path = "menus." + entry.getKey();
                if (config.getString(path) == null) {
                    config.set(path, entry.getValue());
                    changed = true;
                }
            }
            // окна прежней группы больше не наши: старые значения переводим в keep,
            // чтобы после удаления party.yml не было ошибок BMenu
            for (String key : new String[]{"PARTY_MODE", "PARTY_TEAM_SIZE", "PARTY_KIT"}) {
                String path = "menus." + key;
                String value = config.getString(path);
                if (value != null && value.startsWith("elytrix:duel_party")) {
                    config.set(path, "keep");
                    changed = true;
                }
            }
            if (config.getString("command-guard.enabled") == null) {
                config.set("command-guard.enabled", true);
                config.set("command-guard.duel-menu", true);
                config.set("command-guard.hide-tab", true);
                config.set("command-guard.admin-permission", "epicduels.admin");
                config.set("command-guard.bypass-permission", "elytrix.guard.bypass");
                config.set("command-guard.blocked", Arrays.asList(
                        "plugins", "pl", "version", "ver", "about", "icanhasbukkit", "help"));
                config.set("command-guard.hidden", Arrays.asList(
                        "plugins", "pl", "version", "ver", "about", "icanhasbukkit", "help",
                        "bmenu", "epicduels", "duelx", "dx", "party", "p"));
                changed = true;
            }
            if (config.getString("kits.auto-install") == null) {
                config.set("kits.auto-install", true);
                config.set("kits.names", Arrays.asList(Kits.NAMES));
                changed = true;
            }
            // старые английские id наборов → русские имена
            List<String> kitNames = config.getStringList("kits.names");
            if (kitNames != null && kitNames.contains("sword")) {
                config.set("kits.names", Arrays.asList(Kits.NAMES));
                changed = true;
            }
            if (config.getString("queue.timeout-seconds") == null) {
                config.set("queue.timeout-seconds", 180);
                config.set("queue.team-timeout-seconds", 300);
                config.set("queue.broadcast", true);
                changed = true;
            }
            if (config.getString("protection.block-place") == null) {
                config.set("protection.block-place", true);
                config.set("protection.explosions", true);
                changed = true;
            }
            if (config.getString("protection.leave") == null) {
                config.set("protection.leave", true);
                config.set("protection.pearl-limit", true);
                config.set("protection.max-height", 7);
                config.set("protection.arena-margin", 20);
                config.set("protection.min-drop", 25);
                config.set("protection.blocked-commands", Arrays.asList(
                        "spawn", "hub", "lobby", "tpa", "tpahere", "tpaccept",
                        "warp", "home", "back"));
                changed = true;
            }
            // один общий мир для всех арен: по умолчанию — уже готовый duel
            if (config.getString("arenas.world") == null) {
                config.set("arenas.world", Bukkit.getWorld("duel") == null ? "" : "duel");
                changed = true;
            }
            if (config.getString("api.tops") == null) {
                config.set("api.tops", true);
                config.set("api.tops-interval-seconds", 60);
                config.set("api.tops-size", 10);
                config.set("api.tops-file", "tops.json");
                changed = true;
            }
            if (changed) saveConfig();
        } catch (RuntimeException error) {
            getLogger().warning("Не удалось обновить config.yml: " + error);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ArenaGuard(this), this);
        getServer().getPluginManager().registerEvents(new CommandGuard(this), this);
        getServer().getPluginManager().registerEvents(arenaRules, this);
        getServer().getPluginManager().registerEvents(new Effects(this, duels), this);
        arenaRules.start();
        arenas.setup();
        Tops.start(this, duels);
        teams.start();
        queue.start();
        hijackDuel();

        PluginCommand command = getCommand("duelx");
        if (command != null) {
            DuelXCommand executor = new DuelXCommand(this);
            bridgeExecutor = executor;
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Команда /duelx не зарегистрирована: загрузи плагин "
                    + "перезапуском сервера, а не через PlugMan.");
        }
        if (getServer().getPluginManager().getPlugin("BMenu") == null) {
            getLogger().warning("BMenu не найден: окна EpicDuels будут открываться как есть.");
        }
        if (getConfig().getBoolean("kits.auto-install", true)) {
            int created = kits.install(false);
            if (created > 0) {
                getLogger().info("ElytriX: добавлено наборов — " + created
                        + " (всего в kits.yml " + Kits.NAMES.length + ")");
            } else if (created == 0) {
                getLogger().info("ElytriX: наборы уже есть, kits.yml не менялся");
            } else {
                getLogger().warning("ElytriX: наборы не созданы — EpicDuels не отдал KitManager");
            }
        }
        getLogger().info("ElytriX: мосты меню EpicDuels -> BMenu включены");
    }

    /** Забираем /duel себе: игрокам только «/duel» и «/duel ник». */
    private void hijackDuel() {
        if (!getConfig().getBoolean("command-guard.duel-menu", true)) return;
        PluginCommand command = getServer().getPluginCommand("duel");
        if (command == null) {
            getLogger().warning("Команда /duel не найдена — EpicDuels загрузился?");
            return;
        }
        duelCommand = command;
        duelExecutor = command.getExecutor();
        duelCompleter = command.getTabCompleter();
        DuelCommandGuard guard = new DuelCommandGuard(this, duelExecutor, duelCompleter);
        command.setExecutor(guard);
        command.setTabCompleter(guard);
    }

    /** Права администратора: видит все команды и полный /duel. */
    boolean isAdmin(Player player) {
        if (player == null) return false;
        if (player.isOp()) return true;
        String admin = getConfig().getString("command-guard.admin-permission", "epicduels.admin");
        if (admin != null && !admin.isEmpty() && player.hasPermission(admin)) return true;
        String bypass = getConfig().getString("command-guard.bypass-permission", "elytrix.guard.bypass");
        return bypass != null && !bypass.isEmpty() && player.hasPermission(bypass);
    }

    /** Главное меню дуэлей — то же, что открывает пустой /duel. */
    void openMainMenu(Player player) {
        if (player == null) return;
        // В поиске /duel показывает одно окно с красной кнопкой «Отменить поиск»
        if (queue.isQueued(player.getUniqueId())) {
            openQueueWait(player);
            return;
        }
        String id = menuForOr("MAIN", "elytrix:duel");
        menus.open(player, id, menuArgs(player, id));
    }

    /** Окно «Ищем бой»: кит, режим, сколько ждём и кнопка отмены. */
    void openQueueWait(Player player) {
        String id = menuForOr("QUEUE_WAIT", "elytrix:duel_queue_wait");
        if (menus.openMenu(player, id, queue.argsFor(player)) != Menus.FAILED) return;
        // меню ещё не установлено — хотя бы кнопка в чате
        Component line = Text.of(string("messages.queue-wait-hint",
                "&#F8BEFBДуэли &8» &fТы в поиске. "));
        Text.send(player, line.append(Text.button("&c[Отменить поиск]",
                "/duel queue leave", "Больше не искать")));
    }

    /** Статистика: числа подставляем сами, без PlaceholderAPI. */
    void openStats(Player player) {
        Map<String, String> args = duels.stats(player.getUniqueId());
        String id = menuForOr("STATS", "elytrix:duel_stats");
        if (menus.openMenu(player, id, args) == Menus.WITH_ARGS) return;
        // BMenu не принял значения (старая версия) — показываем цифры в чате,
        // чтобы вместо {wins} игрок не видел фигурные скобки
        msg(player, "&#F8BEFBДуэли &8» &fПобеды: &#F8BEFB" + args.get("wins")
                + " &7· &fПоражения: &#F8BEFB" + args.get("losses")
                + " &7· &fПобед: &#F8BEFB" + args.get("winrate") + "%"
                + " &7· &fОчки: &#F8BEFB" + args.get("score"));
    }

    /** Мир, в котором лежат все арены (пусто — старый режим EpicDuels). */
    String sharedArenaWorld() {
        return arenas.sharedWorld();
    }

    /** Подсказка на родные команды арен EpicDuels (в общем мире не работают). */
    void arenaHint(Player player, String sub) {
        arenas.hint(player, sub);
    }

    /** /duelx arena — арены в одном мире. */
    void arenaCommand(Player player, String[] args) {
        arenas.command(player, args);
    }

    /** Окно очереди (выбор кита для 1 на 1). */
    void openQueueMenu(Player player) {
        String id = menuForOr("MATCHMAKING", "elytrix:duel_queue");
        menus.open(player, id, menuArgs(player, id));
    }

    /** Передать подкоманду нашему же /duelx, но через видимую клиенту /duel. */
    boolean runBridge(Player player, String[] args) {
        if (bridgeExecutor == null || args == null || args.length == 0) return false;
        if (!DuelXCommand.SUBS.contains(lower(args[0]))) return false;
        return bridgeExecutor.run(player, args);
    }

    /** Выполнить подкоманду EpicDuels напрямую, минуя наш фильтр. */
    void runOriginal(Player player, String... args) {
        if (duelExecutor == null || duelCommand == null) {
            msg(player, "&#F8BEFBДуэли &8» &cEpicDuels не отвечает — перезапусти сервер.");
            return;
        }
        duelExecutor.onCommand(player, duelCommand, "duel", args);
    }

    @Override
    public void onDisable() {
        targets.clear();
        // возвращаем EpicDuels его обработчики /duel
        if (duelCommand != null) {
            if (duelExecutor != null) duelCommand.setExecutor(duelExecutor);
            if (duelCompleter != null) duelCommand.setTabCompleter(duelCompleter);
        }
        getLogger().info("ElytriX: мосты меню выключены");
    }

    // ----------------------------------------------------------- открытие окна

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) return;
        InventoryHolder holder;
        try {
            holder = inventory.getHolder(false);
        } catch (RuntimeException error) {
            return;
        }
        if (holder == null || !HOLDER.equals(holder.getClass().getName())) return;
        if (!(event.getPlayer() instanceof Player)) return;
        final Player player = (Player) event.getPlayer();

        String type;
        try {
            Object value = holder.getClass().getMethod("getType").invoke(holder);
            type = ((Enum<?>) value).name();
        } catch (ReflectiveOperationException | RuntimeException error) {
            return;
        }

        // Список игроков рисуем сами — окно плагина не показываем.
        if ("DUELS".equals(type)) {
            event.setCancelled(true);
            final PlayerPicker.Mode mode = PlayerPicker.Mode.CHALLENGE;
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    PlayerPicker.open(ElytrixDuelsBridge.this, player, mode, 0);
                }
            });
            return;
        }

        // Выбор карты убран: окно арен не показываем, берём случайную готовую.
        if ("ARENA_SELECT".equals(type)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    duels.finishChallenge(player);
                }
            });
            return;
        }

        // Групповых окон больше нет: игрока ведём в главное меню и объясняем,
        // что командный бой теперь — кнопка «Бои 2 на 2».
        if ("PARTY_MODE".equals(type) || "PARTY_TEAM_SIZE".equals(type)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    msg(player, string("messages.group-removed",
                            "&#F8BEFBДуэли &8» &fКомандные дуэли теперь в &f/duels&f: "
                                    + "&f«Бои 2 на 2»"));
                    String id = menuForOr("MAIN", "elytrix:duel");
                    menus.open(player, id, menuArgs(player, id));
                }
            });
            return;
        }

        String menu = menuFor(type);
        if (menu == null || menu.isEmpty()) return;

        event.setCancelled(true);
        final String menuId = menu;
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                menus.open(player, menuId, menuArgs(player, menuId));
            }
        });
    }

    /** Значения, которые подставляются в {фигурные скобки} меню BMenu. */
    Map<String, String> menuArgsFor(Player player, String menuId) {
        return menuArgs(player, menuId);
    }

    private Map<String, String> menuArgs(Player player, String menuId) {
        Map<String, String> args = new LinkedHashMap<String, String>();
        if (menuId.endsWith(":duel") || menuId.endsWith(":duel_stats")) {
            args.putAll(duels.stats(player.getUniqueId()));
        }
        if (menuId.endsWith(":duel_queue_wait")) {
            args.putAll(queue.argsFor(player));
        }
        if (menuId.endsWith(":duel_confirm")) {
            Object mode = duels.flowModeValue(player.getUniqueId());
            Object size = duels.flowSizeValue(player.getUniqueId());
            Object kit = duels.flowKitValue(player.getUniqueId());
            args.put("mode", mode == null ? "—" : label(String.valueOf(mode)));
            args.put("size", size == null ? "—" : label(String.valueOf(size)));
            args.put("kit", kit == null ? "—" : String.valueOf(kit));
            Object party = duels.partyOf(player.getUniqueId());
            int members = party == null ? 0 : duels.partySize(party);
            boolean pair = "TEAM_DUEL".equals(String.valueOf(mode))
                    && "TWO_VS_TWO".equals(String.valueOf(size)) && members == 2;
            args.put("action", pair ? "Встать в очередь" : "Начать бой");
        }
        return args;
    }

    private String label(String constant) {
        switch (constant) {
            case "TOURNAMENT": return "Турнир";
            case "TEAM_DUEL": return "Бои 2 на 2";
            case "TWO_VS_TWO": return "2 на 2";
            case "THREE_VS_THREE": return "3 на 3";
            case "FOUR_VS_FOUR": return "4 на 4";
            default: return constant;
        }
    }

    /** id меню BMenu для типа окна EpicDuels, либо null (окно остаётся от плагина). */
    String menuFor(String type) {
        String value = getConfig().getString("menus." + type);
        if (value == null) value = DEFAULTS.get(type);
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("keep")) return null;
        return value;
    }

    // -------------------------------------------------------------- клики

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        InventoryHolder holder = clicked.getHolder();
        if (!(holder instanceof PlayerPicker)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerPicker picker = (PlayerPicker) holder;
        int slot = event.getSlot();

        if (slot == PlayerPicker.closeSlot()) {
            player.closeInventory();
            return;
        }
        if (slot == PlayerPicker.prevSlot()) {
            if (picker.page() > 0) {
                PlayerPicker.open(this, player, picker.mode(), picker.page() - 1);
            }
            return;
        }
        if (slot == PlayerPicker.randomSlot() && picker.mode() == PlayerPicker.Mode.TEAM) {
            player.closeInventory();
            pickTeamRandom(player);
            return;
        }
        if (slot == PlayerPicker.soloSlot() && picker.mode() == PlayerPicker.Mode.TEAM) {
            player.closeInventory();
            pickSoloTeam(player);
            return;
        }
        if (slot == PlayerPicker.nextSlot()) {
            if (picker.page() + 1 < picker.pages()) {
                PlayerPicker.open(this, player, picker.mode(), picker.page() + 1);
            }
            return;
        }

        UUID picked = picker.shown().get(slot);
        if (picked == null) return;
        Player target = Bukkit.getPlayer(picked);
        if (target == null) {
            msg(player, string("messages.player-gone", "&#F8BEFBДуэли &8» &cИгрок уже вышел."));
            PlayerPicker.open(this, player, picker.mode(), picker.page());
            return;
        }
        if (duels.busy(picked)) {
            msg(player, string("messages.target-busy", "&#F8BEFBДуэли &8» &c%name%&c сейчас в бою.")
                    .replace("%name%", target.getName()));
            return;
        }

        if (picker.mode() == PlayerPicker.Mode.TEAM) {
            startTeam(player, target);
        } else {
            requestDuel(player, target);
        }
    }

    // ------------------------------------------------- /duel <ник> и /duel

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null) return;
        String[] parts = message.trim().split("\\s+");
        if (parts.length == 1) {
            String only = parts[0].toLowerCase(Locale.ROOT);
            if (only.equals("/duels") || only.equals("/дуэли")) {
                final Player viewer = event.getPlayer();
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override
                    public void run() {
                        openMainMenu(viewer);
                    }
                });
            }
            return;
        }
        if (parts.length != 2) return;
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!command.equals("/duel") && !command.equals("/d")) return;
        if (DUEL_SUBCOMMANDS.contains(parts[1].toLowerCase(Locale.ROOT))) return;

        final Player target = Bukkit.getPlayerExact(parts[1]);
        if (target == null) return;
        final Player player = event.getPlayer();

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                requestDuel(player, target);
            }
        });
    }

    // ------------------------------------------------------------- действия

    /** /duelx bounds — показать границы текущей арены (для настройки). */
    void boundsInfo(Player player) {
        ArenaRules.Bounds bounds = arenaRules.resolve(player);
        if (bounds == null) {
            msg(player, string("messages.arena-no-duel",
                    "&#F8BEFBДуэли &8» &cТы не в бою."));
            return;
        }
        msg(player, "&#F8BEFBДуэли &8» &fАрена: &f" + bounds.world);
        msg(player, "&7  центр: &f" + round(bounds.centerX) + " " + round(bounds.centerZ)
                + " &7| радиус: &f" + round(bounds.radius));
        msg(player, "&7  высота: &f" + round(bounds.baseY) + " + " + round(bounds.maxY - bounds.baseY)
                + " &7| низ: &f" + round(bounds.minY));
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }

    /** /duelx kits — создать (или пересоздать) десять готовых наборов. */
    void installKits(Player player, boolean reset) {
        int created = kits.install(reset);
        if (created < 0) {
            msg(player, string("messages.kits-failed",
                    "&#F8BEFBДуэли &8» &cНе удалось: EpicDuels не отдал KitManager."));
            return;
        }
        msg(player, string("messages.kits-ready",
                "&#F8BEFBДуэли &8» &fНаборов готово: &f%count%")
                .replace("%count%", String.valueOf(created)));
    }

    /** Выбран соперник: запоминаем и сразу показываем выбор кита. */
    void requestDuel(Player player, Player target) {
        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg(player, string("messages.self", "&#F8BEFBДуэли &8» &cНельзя вызвать самого себя."));
            return;
        }
        if (duels.busy(target.getUniqueId())) {
            msg(player, string("messages.target-busy", "&#F8BEFBДуэли &8» &c%name%&c сейчас в бою.")
                    .replace("%name%", target.getName()));
            return;
        }
        intents.remove(player.getUniqueId());
        // вызвал игрока — поиск в очереди больше не нужен
        if (queue.isQueued(player.getUniqueId())) queue.leave(player);
        targets.put(player.getUniqueId(), target.getUniqueId());
        player.closeInventory();
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("target", target.getName());
        menus.open(player, menuForOr("KIT_SELECT", "elytrix:duel_kit"), args);
    }

    /** Приглашение в пару: тиммейт сам решает, принять или отклонить. */
    void startTeam(Player owner, Player teammate) {
        if (!duels.feature("team-duels")) {
            msg(owner, string("messages.feature-off",
                    "&#F8BEFBДуэли &8» &cВыключено на сервере: &f%name%")
                    .replace("%name%", "Бои 2 на 2"));
            return;
        }
        teams.invite(owner, teammate);
        owner.closeInventory();
    }

    /** Окно выбора напарника (или сведения о текущей паре). */
    void pickTeam(Player player) {
        if (!duels.feature("team-duels")) {
            msg(player, string("messages.feature-off",
                    "&#F8BEFBДуэли &8» &cВыключено на сервере: &f%name%")
                    .replace("%name%", "Бои 2 на 2"));
            return;
        }
        UUID id = player.getUniqueId();
        if (teams.paired(id)) {
            // pairLine уже с никами, поэтому подставляем вручную
            Component line = Text.of(string("messages.team-status",
                    "&#F8BEFBДуэли &8» &fТвоя пара: &d%pair%&f. ")
                    .replace("%pair%", teams.pairLine(id)));
            Component kit = Text.button("&d[Выбрать кит] ", "/duel kitmenu",
                    "Открыть выбор кита и встать в очередь");
            Component out = Text.button("&c[Выйти из пары]", "/duel team leave", "Разорвать пару");
            Text.send(player, line.append(kit).append(out));
            return;
        }
        PlayerPicker.open(this, player, PlayerPicker.Mode.TEAM, 0);
    }

    /** Случайный свободный игрок получает приглашение в пару. */
    void pickTeamRandom(Player player) {
        if (!duels.feature("team-duels")) {
            msg(player, string("messages.feature-off",
                    "&#F8BEFBДуэли &8» &cВыключено на сервере: &f%name%")
                    .replace("%name%", "Бои 2 на 2"));
            return;
        }
        teams.randomInvite(player);
    }

    /** 2 на 2 без напарника: ищем случайного напарника сразу в бою. */
    void pickSoloTeam(Player player) {
        kitMenu(player, "TEAM_SOLO");
    }

    /** Открыть выбор кита с намерением: очередь 1 на 1, пара, соло 2 на 2. */
    void kitMenu(Player player, String intent) {
        UUID id = player.getUniqueId();
        // старые данные EpicDuels (вызов, поток пары) не должны мешать
        duels.call(duels.gui(), "clearChallengeData", id);
        if (intent == null) {
            intents.remove(id);
        } else {
            intents.put(id, intent);
        }
        targets.remove(id);
        Map<String, String> args = new LinkedHashMap<String, String>();
        UUID mate = teams.partnerOf(id);
        args.put("target", mate == null ? "—" : name(mate));
        menus.open(player, menuForOr("KIT_SELECT", "elytrix:duel_kit"), args);
    }

    void teamAccept(Player player, String ownerName) {
        teams.accept(player, ownerName);
    }

    void teamDeny(Player player) {
        teams.deny(player);
    }

    void teamLeave(Player player) {
        teams.leave(player);
    }

    void pickOpponent(Player player) {
        intents.remove(player.getUniqueId());
        targets.remove(player.getUniqueId());
        PlayerPicker.open(this, player, PlayerPicker.Mode.CHALLENGE, 0);
    }

    /** Кит выбран: вызов игрока, очередь 1 на 1 или очередь 2 на 2. */
    void kit(Player player, String kitName) {
        String kit = Kits.canonical(kitName);
        if (!duels.kitExists(kit)) {
            msg(player, string("messages.kit-gone",
                    "&#F8BEFBДуэли &8» &cКит &f%kit%&c не найден.")
                    .replace("%kit%", String.valueOf(kitName)));
            return;
        }
        UUID id = player.getUniqueId();
        UUID target = targets.remove(id);
        if (target == null) {
            Object pluginTarget = duels.call(duels.gui(), "getChallengeTarget", id);
            if (pluginTarget instanceof UUID) target = (UUID) pluginTarget;
        }
        player.closeInventory();

        if (target != null) {
            intents.remove(id);
            duels.call(duels.gui(), "openRoundsSelect", player, target, kit);
            return;
        }

        String intent = intents.remove(id);
        if ("TEAM_PAIR".equals(intent)) {
            if (!duels.feature("team-duels")) {
                msg(player, string("messages.feature-off",
                        "&#F8BEFBДуэли &8» &cВыключено на сервере: &f%name%")
                        .replace("%name%", "Бои 2 на 2"));
                return;
            }
            UUID mate = teams.partnerOf(id);
            Player partner = mate == null ? null : Bukkit.getPlayer(mate);
            if (partner == null || !partner.isOnline()) {
                msg(player, string("messages.team-partner-gone",
                        "&#F8BEFBДуэли &8» &cНапарник вышел — найди нового."));
                return;
            }
            queue.joinPair(player, partner, kit);
            return;
        }
        if ("TEAM_SOLO".equals(intent)) {
            queue.join(player, kit, true);
            return;
        }
        if ("QUEUE_1V1".equals(intent)) {
            queue.join(player, kit, false);
            return;
        }
        if (duels.flowModeValue(id) != null) {
            duels.flowKit(id, kit);
            menus.open(player, menuForOr("PARTY_CONFIRM", "elytrix:duel_confirm"),
                    menuArgs(player, menuForOr("PARTY_CONFIRM", "elytrix:duel_confirm")));
            return;
        }
        msg(player, string("messages.no-target",
                "&#F8BEFBДуэли &8» &cСначала выбери соперника: &f/duel ник"));
    }

    /** Подтверждение боя: пара идёт в очередь, остальное — старый поток EpicDuels. */
    void confirm(Player player) {
        player.closeInventory();
        UUID id = player.getUniqueId();
        if (teams.paired(id)) {
            kitMenu(player, "TEAM_PAIR");
            return;
        }
        duels.startFlow(player);
    }

    /**
     * Очередь: /duel queue, /duelx queue <кит>, queue team <кит>, queue leave.
     * Без аргументов открываем окно выбора кита для 1 на 1.
     */
    void queueCommand(Player player, String[] args) {
        if (args == null || args.length == 0) {
            openQueueMenu(player);
            return;
        }
        String first = lower(args[0]);
        if (first.equals("leave") || first.equals("выйти") || first.equals("cancel")
                || first.equals("отмена")) {
            queue.leave(player);
            return;
        }
        if (first.equals("team") || first.equals("2x2") || first.equals("пара")) {
            if (args.length >= 2) {
                queue.join(player, Kits.canonical(args[1]), true);
            } else {
                kitMenu(player, "TEAM_SOLO");
            }
            return;
        }
        if (first.equals("1x1") || first.equals("solo")) {
            if (args.length >= 2) {
                queue.join(player, Kits.canonical(args[1]), false);
            } else {
                kitMenu(player, "QUEUE_1V1");
            }
            return;
        }
        queue.join(player, Kits.canonical(args[0]), false);
    }

    private String name(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? "?" : player.getName();
    }

    void leaveQueue(Player player) {
        queue.leave(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        UUID id = player.getUniqueId();
        targets.remove(id);
        intents.remove(id);
        queue.quit(player);
        teams.quit(player);
    }

    void cancelFlow(Player player) {
        leaveQueue(player);
        duels.clearFlow(player.getUniqueId());
        player.closeInventory();
        msg(player, string("messages.cancelled",
                "&#F8BEFBДуэли &8» &7Отменено."));
    }

    String menuForOr(String type, String fallback) {
        String value = menuFor(type);
        return value == null ? fallback : value;
    }

    // ------------------------------------------------------------- утилиты

    Duels duels() {
        return duels;
    }

    Teams teams() {
        return teams;
    }

    Queue queue() {
        return queue;
    }

    /** Игрок больше не ждёт выбора кита для старой цели. */
    void forgetTarget(UUID id) {
        targets.remove(id);
    }

    Menus menus() {
        return menus;
    }

    String string(String path, String fallback) {
        String value = getConfig().getString(path);
        return value == null ? fallback : value;
    }

    void msg(Player player, String text) {
        if (player == null || text == null) return;
        player.sendMessage(colorize(text));
    }

    /** Переводит &-коды и &#RRGGBB в §-формат, который понимает Bukkit. */
    static String colorize(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '&') {
                out.append(c);
                continue;
            }
            if (i + 7 < text.length() && text.charAt(i + 1) == '#' && isHex(text, i + 2, i + 8)) {
                out.append('\u00A7').append('x');
                for (int j = i + 2; j < i + 8; j++) {
                    out.append('\u00A7').append(Character.toLowerCase(text.charAt(j)));
                }
                i += 7;
            } else {
                out.append('\u00A7');
            }
        }
        return out.toString();
    }

    private static boolean isHex(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    void messageTargetGone(Player player) {
        msg(player, string("messages.player-gone", "&#F8BEFBДуэли &8» &cИгрок уже вышел."));
    }

    static Map<String, String> emptyArgs() {
        return Collections.emptyMap();
    }
}
