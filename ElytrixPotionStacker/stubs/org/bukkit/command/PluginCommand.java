package org.bukkit.command;
/** Заглушка: в Bukkit JavaPlugin#getCommand возвращает именно PluginCommand. */
public class PluginCommand extends Command {
    public void setExecutor(CommandExecutor executor) { }
    public void setTabCompleter(TabCompleter completer) { }
}
