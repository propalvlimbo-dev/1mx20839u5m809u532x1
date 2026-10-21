package org.bukkit.command;
public abstract class Command {
    public void setExecutor(CommandExecutor executor) { }
    public void setTabCompleter(TabCompleter completer) { }
    public String getName() { return ""; }
}
