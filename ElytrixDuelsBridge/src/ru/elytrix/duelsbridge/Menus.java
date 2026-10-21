package ru.elytrix.duelsbridge;

import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Открытие меню BMenu напрямую через его API: так можно передать значения
 * ({wins}, {target}, ...) и не зависеть от PlaceholderAPI.
 *
 * Если API почему-то недоступно — откатываемся на команду /bmenu open.
 */
final class Menus {

    private final ElytrixDuelsBridge plugin;

    Menus(ElytrixDuelsBridge plugin) {
        this.plugin = plugin;
    }

    boolean open(Player player, String menuId) {
        return open(player, menuId, null);
    }

    /** Окно не открылось. */
    static final int FAILED = 0;
    /** Окно открылось, значения передать не удалось (старый BMenu). */
    static final int NO_ARGS = 1;
    /** Окно открылось, значения подставлены. */
    static final int WITH_ARGS = 2;

    boolean open(Player player, String menuId, Map<String, String> args) {
        return openMenu(player, menuId, args) != FAILED;
    }

    int openMenu(Player player, String menuId, Map<String, String> args) {
        if (menuId == null || menuId.isEmpty() || player == null) return FAILED;
        if (openViaApi(player, menuId, args)) {
            if (debug()) plugin.getLogger().info("BMenu: окно " + menuId
                    + " открыто API, значений: " + (args == null ? 0 : args.size()));
            return WITH_ARGS;
        }
        if (args != null && !args.isEmpty()) {
            plugin.getLogger().warning("BMenu: API недоступно для " + menuId
                    + " — открываю командой, без значений " + args.keySet());
        }

        String command = "bmenu open " + menuId + " " + player.getName();
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!ok) {
            plugin.getLogger().warning("BMenu не принял команду: " + command
                    + " (проверь id меню в config.yml)");
        }
        if (!ok) return FAILED;
        // окно открылось, но значения не переданы — пусть вызывающий покажет их в чате
        return args != null && !args.isEmpty() ? NO_ARGS : WITH_ARGS;
    }

    private boolean debug() {
        return plugin.getConfig().getBoolean("menus.debug", false);
    }

    private boolean openViaApi(Player player, String menuId, Map<String, String> args) {
        Plugin bmenu = Bukkit.getPluginManager().getPlugin("BMenu");
        if (bmenu == null) return false;
        try {
            Object loader = null;
            try {
                Method loaderGetter = bmenu.getClass().getMethod("menuLoader");
                loaderGetter.setAccessible(true);
                loader = loaderGetter.invoke(null);
            } catch (Throwable ignored) {
                for (Method method : bmenu.getClass().getMethods()) {
                    if (!method.getName().equals("menuLoader") || method.getParameterCount() != 0) continue;
                    method.setAccessible(true);
                    loader = method.invoke(null);
                    if (loader != null) break;
                }
            }
            if (loader == null) return false;

            Object menu = null;
            // BMenu разных версий: create(String, Player, Menu) или create(String, Player)
            for (int count = 3; count >= 2 && menu == null; count--) {
                for (Method method : loader.getClass().getMethods()) {
                    if (!method.getName().equals("create") || method.getParameterCount() != count) continue;
                    if (method.getParameterTypes()[0] != String.class) continue;
                    method.setAccessible(true);
                    Object[] params = new Object[count];
                    params[0] = menuId;
                    params[1] = player;
                    menu = method.invoke(loader, params);
                    if (menu != null) break;
                }
            }
            if (menu == null) return false;

            if (args != null) {
                Method add = find(menu.getClass(), "addArgument", 2);
                if (add == null) return false;
                add.setAccessible(true);
                for (Map.Entry<String, String> entry : args.entrySet()) {
                    add.invoke(menu, entry.getKey(), entry.getValue());
                }
            }
            // проверяем, что значения реально подставились: если {ключ} остался —
            // версия BMenu не умеет addArgument, отдаём окно без значений
            if (args != null && !args.isEmpty()) {
                Method resolve = find(menu.getClass(), "setPlaceholders", 1);
                if (resolve == null) return false;
                String key = args.keySet().iterator().next();
                Object probe = resolve.invoke(menu, "{" + key + "}");
                if (probe != null && String.valueOf(probe).contains("{" + key + "}")) return false;
            }
            Method open = find(menu.getClass(), "open", 0);
            if (open == null) return false;
            open.setAccessible(true);
            open.invoke(menu);
            return true;
        } catch (Throwable error) {
            plugin.getLogger().warning("BMenu API: " + error + " — открываю командой.");
            return false;
        }
    }

    /** Первый публичный метод с таким именем и числом аргументов (без наследования по типам). */
    private static Method find(Class<?> type, String name, int params) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != params) continue;
            if (params == 2 && (method.getParameterTypes()[0] != String.class
                    || method.getParameterTypes()[1] != String.class)) continue;
            return method;
        }
        return null;
    }
}
