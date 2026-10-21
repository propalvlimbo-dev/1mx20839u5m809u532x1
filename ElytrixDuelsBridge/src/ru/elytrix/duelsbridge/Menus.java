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

    boolean open(Player player, String menuId, Map<String, String> args) {
        if (menuId == null || menuId.isEmpty() || player == null) return false;
        if (openViaApi(player, menuId, args)) return true;

        String command = "bmenu open " + menuId + " " + player.getName();
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!ok) {
            plugin.getLogger().warning("BMenu не принял команду: " + command
                    + " (проверь id меню в config.yml)");
        }
        return ok;
    }

    private boolean openViaApi(Player player, String menuId, Map<String, String> args) {
        Plugin bmenu = Bukkit.getPluginManager().getPlugin("BMenu");
        if (bmenu == null) return false;
        try {
            Method loaderGetter = bmenu.getClass().getMethod("menuLoader");
            Object loader = loaderGetter.invoke(null);
            if (loader == null) return false;

            Object menu = null;
            for (Method method : loader.getClass().getMethods()) {
                if (!method.getName().equals("create") || method.getParameterCount() != 3) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types[0] != String.class) continue;
                method.setAccessible(true);
                menu = method.invoke(loader, menuId, player, null);
                if (menu != null) break;
            }
            if (menu == null) return false;

            if (args != null) {
                Method add = menu.getClass().getMethod("addArgument", String.class, String.class);
                add.setAccessible(true);
                for (Map.Entry<String, String> entry : args.entrySet()) {
                    add.invoke(menu, entry.getKey(), entry.getValue());
                }
            }
            Method open = menu.getClass().getMethod("open");
            open.setAccessible(true);
            open.invoke(menu);
            return true;
        } catch (Throwable error) {
            plugin.getLogger().warning("BMenu API: " + error + " — открываю командой.");
            return false;
        }
    }
}
