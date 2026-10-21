package org.bukkit.command;
public abstract class Command {
    public void setExecutor(CommandExecutor executor) { }
    public CommandExecutor getExecutor() { return null; }
    public void setTabCompleter(TabCompleter completer) { }
    public TabCompleter getTabCompleter() { return null; }
    public String getName() { return ""; }
}
