package ru.rooyzee.elytrixsubscribe;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixsubscribe.command.SubscribeCommand;
import ru.rooyzee.elytrixsubscribe.database.SQLite;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.functions.FunctionManager;
import ru.rooyzee.elytrixsubscribe.menu.ParrotMenu;
import ru.rooyzee.elytrixsubscribe.menu.ParrotNameMenu;
import ru.rooyzee.elytrixsubscribe.placeholder.CustomPlaceHolder;
import ru.rooyzee.elytrixsubscribe.service.SubService;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;
import ru.rooyzee.elytrixsubscribe.sub.SubManager;

public final class Main extends JavaPlugin {
    private static Main instance;
    private SubService service;
    private SQLite sqlite;
    private SubManager subManager;
    private FunctionManager functionsManager;
    private CommandExecutor subscribeCommand;
    private ProtocolManager protocolManager;

    public void onEnable() {
        if (this.checkPlugin("LuckPerms") && this.checkPlugin("PlaceholderAPI")) {
            instance = this;
            this.saveDefaultConfig();
            this.sqlite = new SQLite(this);
            this.protocolManager = ProtocolLibrary.getProtocolManager();
            this.functionsManager = new FunctionManager(this);
            this.service = new SubService(this);
            this.service.createTable();
            this.subManager = new SubManager(this);

            // Регистрация команды
            this.subscribeCommand = new SubscribeCommand();
            if (getCommand("subscribe") != null) {
                getCommand("subscribe").setExecutor(this.subscribeCommand);
            }

            // Регистрация меню (слушателей инвентаря)
            new ParrotMenu();
            new ParrotNameMenu();

            // Регистрация плейсхолдеров
            (new CustomPlaceHolder()).register();
        } else {
            this.setEnabled(false);
        }
    }

    private boolean checkPlugin(String name) {
        if (!Bukkit.getPluginManager().isPluginEnabled(name)) {
            System.err.println("Please, install " + name + "!");
            return false;
        }
        return true;
    }

    public void onDisable() {
        if (this.subManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    PlayerSub sub = this.subManager.convertPlayer(player);
                    List<Function> functionsCopy = new ArrayList<>(sub.getEnabledFunctions());
                    for (Function function : functionsCopy) {
                        try {
                            function.onDisable(player);
                        } catch (Exception ignored) {}
                    }
                } catch (ConvertPlayerException ignored) {}
            }
            HandlerList.unregisterAll(this.subManager);
        }
        try {
            (new CustomPlaceHolder()).unregister();
        } catch (Exception ignored) {}
        if (this.sqlite != null) {
            this.sqlite.close();
        }
    }

    public static Main getInstance() {
        return instance;
    }

    public SubService getService() {
        return this.service;
    }

    public SQLite getSQLite() {
        return this.sqlite;
    }

    public SubManager getSubManager() {
        return this.subManager;
    }

    public FunctionManager getFunctionsManager() {
        return this.functionsManager;
    }

    public CommandExecutor getSubscribeCommand() {
        return this.subscribeCommand;
    }

    public ProtocolManager getProtocolManager() {
        return this.protocolManager;
    }
}