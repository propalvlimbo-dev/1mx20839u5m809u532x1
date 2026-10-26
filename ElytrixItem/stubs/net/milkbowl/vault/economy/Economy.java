package net.milkbowl.vault.economy;
public interface Economy {
    boolean isEnabled();
    String getName();
    boolean hasAccount(String playerName);
    double getBalance(String playerName);
    boolean has(String playerName, double amount);
    boolean withdrawPlayer(String playerName, double amount);
    boolean depositPlayer(String playerName, double amount);
    net.milkbowl.vault.economy.EconomyResponse withdrawPlayer(org.bukkit.OfflinePlayer player, double amount);
    net.milkbowl.vault.economy.EconomyResponse depositPlayer(org.bukkit.OfflinePlayer player, double amount);
    double getBalance(org.bukkit.OfflinePlayer player);
    boolean has(org.bukkit.OfflinePlayer player, double amount);
}
