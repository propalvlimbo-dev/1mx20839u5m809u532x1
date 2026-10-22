package ru.rooyzee.elytrixsubscribe.menu;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixsubscribe.Main;

/**
 * Открытие меню BMenu.
 *
 * Сначала пробуем API BMenu (loader.create + open) — так окно открывается
 * сразу и с нашей анимацией. Если API недоступно (другая сборка BMenu) —
 * команда /bmenu open, её понимает любая версия.
 */
public final class Menus {
    private final Main plugin;

    public Menus(Main plugin) {
        this.plugin = plugin;
    }

    public boolean open(Player player, String menuId) {
        if (player == null || menuId == null || menuId.isEmpty()) return false;
        if (this.openViaApi(player, menuId)) return true;
        String command = "bmenu open " + menuId + " " + player.getName();
        boolean sent = false;
        try {
            sent = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (RuntimeException error) {
            this.plugin.getLogger().warning("BMenu: " + command + " — " + error);
        }
        if (!sent) {
            this.plugin.getLogger().warning("BMenu не открыл меню " + menuId
                    + " — проверь, что файл меню лежит в plugins/BMenu/menu и id совпадает.");
        }
        return sent;
    }

    private boolean openViaApi(Player player, String menuId) {
        Plugin bmenu = Bukkit.getPluginManager().getPlugin("BMenu");
        if (bmenu == null) return false;
        try {
            Object loader = null;
            for (Method method : bmenu.getClass().getMethods()) {
                if (!method.getName().equals("menuLoader") || method.getParameterCount() != 0) continue;
                method.setAccessible(true);
                loader = method.invoke(null);
                if (loader != null) break;
            }
            if (loader == null) return false;

            Object menu = null;
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
            Method open = null;
            for (Method method : menu.getClass().getMethods()) {
                if (method.getName().equals("open") && method.getParameterCount() == 0) {
                    open = method;
                    break;
                }
            }
            if (open == null) return false;
            open.setAccessible(true);
            open.invoke(menu);
            return true;
        } catch (Throwable error) {
            this.plugin.getLogger().warning("BMenu API: " + error + " — открываю командой.");
            return false;
        }
    }
}
