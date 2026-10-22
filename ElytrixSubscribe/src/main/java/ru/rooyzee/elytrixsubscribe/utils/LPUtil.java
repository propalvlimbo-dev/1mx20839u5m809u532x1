package ru.rooyzee.elytrixsubscribe.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.OfflinePlayer;

public class LPUtil {
    private static final LuckPerms luckPerms = LuckPermsProvider.get();

    public static boolean addPermission(OfflinePlayer offlinePlayer, String[] permissions) {
        User user = luckPerms.getUserManager().getUser(offlinePlayer.getUniqueId());
        if (user == null) {
            return false;
        }
        for (String permission : permissions) {
            user.data().add(Node.builder(permission).build());
        }
        luckPerms.getUserManager().saveUser(user);
        return true;
    }

    public static boolean removePermissions(OfflinePlayer offlinePlayer, String[] permissions) {
        User user = luckPerms.getUserManager().getUser(offlinePlayer.getUniqueId());
        if (user == null) {
            return false;
        }
        for (String permission : permissions) {
            user.data().remove(Node.builder(permission).build());
        }
        luckPerms.getUserManager().saveUser(user);
        return true;
    }
}