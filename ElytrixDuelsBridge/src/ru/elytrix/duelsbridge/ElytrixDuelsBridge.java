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
    /** Оригинальные обработчики /duel из EpicDuels — нужны админам и /duelx. */
    private PluginCommand duelCommand;
    private CommandExecutor duelExecutor;
    private TabCompleter duelCompleter;
    /** Кого игрок выбрал в своём окне (до выбора кита). */
    private final Map<UUID, UUID> targets = new HashMap<UUID, UUID>();
    /** Очередь пар 2 на 2: ждём вторую пару с тем же китом. */
    private final List<Pair> pairs = new ArrayList<Pair>();

    /** Пара игроков, вставшая в очередь на 2 на 2. */
    private static final class Pair {
        final UUID owner;
        final UUID mate;
        final String kit;

        Pair(UUID owner, UUID mate, String kit) {
            this.owner = owner;
            this.mate = mate;
            this.kit = kit;
        }

        boolean has(UUID id) {
            return owner.equals(id) || mate.equals(id);
        }
    }

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
            if (config.getString("protection.block-place") == null) {
                config.set("protection.block-place", true);
                config.set("protection.explosions", true);
                changed = true;
            }
            if (changed) saveConfig();
        } catch (RuntimeException error) {
            getLogger().warning("Не удалось обновить config.yml: " + error);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new ArenaGuard(this), this);
        getServer().getPluginManager().registerEvents(new CommandGuard(this), this);
        hijackDuel();

        PluginCommand command = getCommand("duelx");
        if (command != null) {
            DuelXCommand executor = new DuelXCommand(this);
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
        String id = menuForOr("MAIN", "elytrix:duel");
        menus.open(player, id, menuArgs(player, id));
    }

    /** Выполнить подкоманду EpicDuels напрямую, минуя наш фильтр. */
    void runOriginal(Player player, String... args) {
        if (duelExecutor == null || duelCommand == null) {
            msg(player, "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cEpicDuels не отвечает — перезапусти сервер.");
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
                            "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fКомандные дуэли теперь в &f/duels&f: "
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
    private Map<String, String> menuArgs(Player player, String menuId) {
        Map<String, String> args = new LinkedHashMap<String, String>();
        if (menuId.endsWith(":duel") || menuId.endsWith(":duel_stats")) {
            args.putAll(duels.stats(player.getUniqueId()));
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
            msg(player, string("messages.player-gone", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cИгрок уже вышел."));
            PlayerPicker.open(this, player, picker.mode(), picker.page());
            return;
        }
        if (duels.busy(picked)) {
            msg(player, string("messages.target-busy", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &c%name%&c сейчас в бою.")
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
                        String id = menuForOr("MAIN", "elytrix:duel");
                        menus.open(viewer, id, menuArgs(viewer, id));
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

    /** /duelx kits — создать (или пересоздать) десять готовых наборов. */
    void installKits(Player player, boolean reset) {
        int created = kits.install(reset);
        if (created < 0) {
            msg(player, string("messages.kits-failed",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНе удалось: EpicDuels не отдал KitManager."));
            return;
        }
        msg(player, string("messages.kits-ready",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fНаборов готово: &f%count%")
                .replace("%count%", String.valueOf(created)));
    }

    /** Выбран соперник: запоминаем и сразу показываем выбор кита. */
    void requestDuel(Player player, Player target) {
        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg(player, string("messages.self", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНельзя вызвать самого себя."));
            return;
        }
        if (duels.busy(target.getUniqueId())) {
            msg(player, string("messages.target-busy", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &c%name%&c сейчас в бою.")
                    .replace("%name%", target.getName()));
            return;
        }
        targets.put(player.getUniqueId(), target.getUniqueId());
        player.closeInventory();
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("target", target.getName());
        menus.open(player, menuForOr("KIT_SELECT", "elytrix:duel_kit"), args);
    }

    /** Быстрый 2 на 2: группа из двух игроков без приглашений. */
    void startTeam(Player owner, Player teammate) {
        if (!duels.feature("team-duels")) {
            msg(owner, string("messages.feature-off",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cВыключено на сервере: &f%name%")
                    .replace("%name%", "Бои 2 на 2"));
            return;
        }
        if (teammate.getUniqueId().equals(owner.getUniqueId())) {
            msg(owner, string("messages.self", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНельзя выбрать себя в напарники."));
            return;
        }
        Object party = duels.ownParty(owner);
        if (party == null) {
            msg(owner, string("messages.party-failed",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНе удалось собрать пару."));
            return;
        }
        if (!duels.isOwner(party, owner.getUniqueId())) {
            msg(owner, string("messages.owner-only",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cТиммейта приглашает только лидер пары."));
            return;
        }
        if (!duels.addMember(party, teammate.getUniqueId())) {
            msg(owner, string("messages.party-full",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cВ паре уже &f%size%&c игроков.")
                    .replace("%size%", String.valueOf(duels.partySize(party))));
            return;
        }

        UUID id = owner.getUniqueId();
        duels.flowMode(id, "TEAM_DUEL");
        duels.flowSize(id, "TWO_VS_TWO");
        targets.remove(id);
        owner.closeInventory();

        msg(owner, string("messages.team-created",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fПара 2 на 2: &d%owner% &f+ &d%name%")
                .replace("%name%", teammate.getName()).replace("%owner%", owner.getName()));
        msg(teammate, string("messages.team-invited",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &d%owner% &fвзял тебя в напарники для боя 2 на 2")
                .replace("%owner%", owner.getName()));

        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("target", teammate.getName());
        menus.open(owner, menuForOr("KIT_SELECT", "elytrix:duel_kit"), args);
    }

    /** Открыть наше окно выбора игрока (для команды /duelx team). */
    void pickTeam(Player player) {
        if (!duels.feature("team-duels")) {
            msg(player, string("messages.feature-off",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cВыключено на сервере: &f%name%")
                    .replace("%name%", "Бои 2 на 2"));
            return;
        }
        PlayerPicker.open(this, player, PlayerPicker.Mode.TEAM, 0);
    }

    void pickOpponent(Player player) {
        PlayerPicker.open(this, player, PlayerPicker.Mode.CHALLENGE, 0);
    }

    /** Кит выбран: либо в бой с игроком, либо к подтверждению боя 2 на 2. */
    void kit(Player player, String kitName) {
        if (!duels.kitExists(kitName)) {
            msg(player, string("messages.kit-gone",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cКит &f%kit%&c не найден.")
                    .replace("%kit%", kitName));
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
            Object gui = duels.gui();
            duels.call(gui, "openRoundsSelect", player, target, kitName);
            return;
        }
        if (duels.flowModeValue(id) != null) {
            duels.flowKit(id, kitName);
            menus.open(player, menuForOr("PARTY_CONFIRM", "elytrix:duel_confirm"),
                    menuArgs(player, menuForOr("PARTY_CONFIRM", "elytrix:duel_confirm")));
            return;
        }
        msg(player, string("messages.no-target",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cСначала выбери соперника: &f/duel ник"));
    }

    void confirm(Player player) {
        player.closeInventory();
        UUID id = player.getUniqueId();
        Object mode = duels.flowModeValue(id);
        Object size = duels.flowSizeValue(id);
        Object party = duels.partyOf(id);
        int members = party == null ? 0 : duels.partySize(party);
        if ("TEAM_DUEL".equals(String.valueOf(mode))
                && "TWO_VS_TWO".equals(String.valueOf(size))
                && members == 2) {
            enqueuePair(player, party);
            return;
        }
        duels.startFlow(player);
    }

    /** Пара готова: ищем вторую пару с тем же китом. */
    private void enqueuePair(Player owner, Object party) {
        UUID id = owner.getUniqueId();
        Object kitValue = duels.flowKitValue(id);
        if (kitValue == null) {
            duels.startFlow(owner);
            return;
        }
        String kit = String.valueOf(kitValue);
        UUID mate = otherMember(party, id);
        if (mate == null) {
            msg(owner, string("messages.pair-broken",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cВ паре должен быть ровно один напарник."));
            return;
        }
        Pair mine = new Pair(id, mate, kit);
        Pair found = null;
        synchronized (pairs) {
            Iterator<Pair> iterator = pairs.iterator();
            while (iterator.hasNext()) {
                Pair candidate = iterator.next();
                if (candidate.has(id) || candidate.has(mate)) {
                    iterator.remove();
                    continue;
                }
                if (!candidate.kit.equalsIgnoreCase(kit)) continue;
                if (!online(candidate.owner) || !online(candidate.mate)) {
                    iterator.remove();
                    continue;
                }
                found = candidate;
                iterator.remove();
                break;
            }
            if (found == null) pairs.add(mine);
        }

        if (found == null) {
            msg(owner, string("messages.queue-wait",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fИщем вторую пару &7(кит &f%kit%&7)&f. &7Не выходи из игры.")
                    .replace("%kit%", kit));
            return;
        }
        startPairMatch(found, mine);
    }

    private void startPairMatch(Pair other, Pair mine) {
        List<UUID> all = Arrays.asList(other.owner, other.mate, mine.owner, mine.mate);
        if (!duels.startTeamDuel(all, "TWO_VS_TWO", mine.kit)) {
            String text = string("messages.start-failed",
                    "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cНе удалось начать бой. Попробуй ещё раз.");
            msg(Bukkit.getPlayer(other.owner), text);
            msg(Bukkit.getPlayer(mine.owner), text);
            return;
        }
        // EpicDuels перемешивает составы — возвращаем выбранных напарников.
        duels.fixTeams(other.owner, other.mate, mine.owner, mine.mate);
        duels.clearFlow(other.owner);
        duels.clearFlow(mine.owner);
        targets.remove(mine.owner);
        targets.remove(other.owner);

        String text = string("messages.pair-found",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &fПара найдена: &d%mate% &fпротив &d%a% &f+ &d%b%")
                .replace("%mate%", name(mine.mate))
                .replace("%a%", name(other.owner))
                .replace("%b%", name(other.mate));
        for (UUID id : all) msg(Bukkit.getPlayer(id), text);
    }

    private UUID otherMember(Object party, UUID id) {
        Object members = Duels.call(party, "getMembers");
        if (!(members instanceof List)) return null;
        UUID found = null;
        for (Object member : (List<?>) members) {
            if (!(member instanceof UUID) || member.equals(id)) continue;
            if (found != null) return null;
            found = (UUID) member;
        }
        return found;
    }

    private boolean online(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player != null && player.isOnline();
    }

    private String name(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? "?" : player.getName();
    }

    void leaveQueue(Player player) {
        UUID id = player.getUniqueId();
        synchronized (pairs) {
            Iterator<Pair> iterator = pairs.iterator();
            while (iterator.hasNext()) {
                Pair pair = iterator.next();
                if (!pair.has(id)) continue;
                iterator.remove();
                msg(Bukkit.getPlayer(pair.owner), string("messages.queue-left",
                        "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &7Поиск пары отменён."));
                msg(Bukkit.getPlayer(pair.mate), string("messages.queue-left",
                        "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &7Поиск пары отменён."));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        targets.remove(player.getUniqueId());
        leaveQueue(player);
    }

    void cancelFlow(Player player) {
        leaveQueue(player);
        duels.clearFlow(player.getUniqueId());
        player.closeInventory();
        msg(player, string("messages.cancelled",
                "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &7Отменено."));
    }

    String menuForOr(String type, String fallback) {
        String value = menuFor(type);
        return value == null ? fallback : value;
    }

    // ------------------------------------------------------------- утилиты

    Duels duels() {
        return duels;
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
        msg(player, string("messages.player-gone", "&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cИгрок уже вышел."));
    }

    static Map<String, String> emptyArgs() {
        return Collections.emptyMap();
    }
}
