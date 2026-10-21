package org.bukkit.entity;
import org.bukkit.command.CommandSender;
public interface Player extends HumanEntity, CommandSender {
    boolean performCommand(String command);
    void closeInventory();
    boolean isOnline();
}
