package org.bukkit.command;



public interface CommandSender {
    void sendMessage(String message);
    void sendMessage(String[] messages);
    void sendMessage(net.md_5.bungee.api.chat.BaseComponent component);
    void sendMessage(net.md_5.bungee.api.chat.BaseComponent... components);
    boolean hasPermission(String name);
    boolean isOp();
    String getName();
    org.bukkit.Server getServer();

    class Spigot {
        public Spigot() { }
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent component) { }
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent... components) { }
    }
}
