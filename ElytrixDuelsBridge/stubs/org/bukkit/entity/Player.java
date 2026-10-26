package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
public interface Player extends HumanEntity, OfflinePlayer, CommandSender {
    boolean performCommand(String command);
    void closeInventory();
    boolean isOnline();
    boolean hasPermission(String name);
    boolean isOp();
    Location getLocation();
    World getWorld();
    boolean teleport(Location location);
    void sendActionBar(net.kyori.adventure.text.Component message);
    boolean addPotionEffect(org.bukkit.potion.PotionEffect effect);
    boolean removePotionEffect(org.bukkit.potion.PotionEffectType type);
    java.util.Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects();
}
