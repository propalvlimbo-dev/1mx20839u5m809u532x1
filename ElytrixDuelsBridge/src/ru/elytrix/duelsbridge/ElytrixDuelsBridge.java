package ru.elytrix.duelsbridge;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Подменяет окна EpicDuels на меню BMenu.
 *
 * Плагин не трогает java-классы EpicDuels: он слушает InventoryOpenEvent,
 * определяет, какое окно собирается открыть EpicDuels (по MenuHolder),
 * отменяет открытие и через тик открывает наше меню BMenu командой
 * /bmenu open <id> <игрок>. Внутри меню BMenu кнопки вызывают /duelx ...,
 * который продолжает поток плагина (раунды, пати) через его публичные методы.
 */
public final class ElytrixDuelsBridge extends JavaPlugin implements Listener {

    private static final String EPICDUELS = "EpicDuels";
    private static final String HOLDER = "dev.epicduels.gui.MenuHolder";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

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
        getLogger().info("ElytriX: мосты меню EpicDuels -> BMenu включены");
    }

    @Override
    public void onDisable() {
        getLogger().info("ElytriX: мосты меню выключены");
    }

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

        String type;
        try {
            Object value = holder.getClass().getMethod("getType").invoke(holder);
            type = ((Enum<?>) value).name();
        } catch (ReflectiveOperationException | RuntimeException error) {
            return;
        }

        String menu = menuFor(type);
        if (menu == null || menu.isEmpty()) return;
        if (!(event.getPlayer() instanceof Player)) return;
        final Player player = (Player) event.getPlayer();

        // Окно плагина не показываем вообще — сразу наше меню.
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                open(player, menuFor(type));
            }
        });
    }

    /** id меню BMenu для типа окна EpicDuels, либо null (окно остаётся от плагина). */
    String menuFor(String type) {
        String value = getConfig().getString("menus." + type);
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("keep")) return null;
        return value;
    }

    void open(Player player, String menu) {
        if (menu == null || menu.isEmpty()) return;
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "bmenu open " + menu + " " + player.getName());
        if (!ok) {
            getLogger().warning("BMenu не принял команду: bmenu open " + menu
                    + " " + player.getName() + " (проверь id меню в config.yml)");
        }
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
}
