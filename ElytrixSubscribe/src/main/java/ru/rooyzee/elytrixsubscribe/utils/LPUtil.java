package ru.rooyzee.elytrixsubscribe.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Выдача прав через LuckPerms. Все ошибки гасим: если LuckPerms не успел
 * загрузиться, подписка продолжит работать, а права добавим при следующем
 * входе.
 */
public final class LPUtil {

    private LPUtil() {
    }

    private static LuckPerms luckPerms() {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return LuckPermsProvider.get();
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean addPermission(OfflinePlayer player, String[] permissions) {
        return change(player, permissions, true);
    }

    public static boolean removePermissions(OfflinePlayer player, String[] permissions) {
        return change(player, permissions, false);
    }

    private static boolean change(OfflinePlayer player, String[] permissions, boolean add) {
        LuckPerms api = luckPerms();
        if (api == null || player == null || permissions == null) return false;
        try {
            User user = api.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;
            for (String permission : permissions) {
                if (permission == null || permission.trim().isEmpty()) continue;
                Node node = Node.builder(permission.trim()).build();
                if (add) {
                    user.data().add(node);
                } else {
                    user.data().remove(node);
                }
            }
            api.getUserManager().saveUser(user);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
