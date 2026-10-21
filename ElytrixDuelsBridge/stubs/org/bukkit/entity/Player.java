package org.bukkit.entity;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

public interface Player extends HumanEntity, OfflinePlayer, CommandSender {
    boolean performCommand(String command);
    void closeInventory();
    boolean isOnline();
}
