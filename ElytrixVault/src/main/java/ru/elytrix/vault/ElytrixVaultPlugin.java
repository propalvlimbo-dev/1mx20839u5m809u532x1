package ru.elytrix.vault;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.elytrix.vault.command.VaultCommand;
import ru.elytrix.vault.listener.GuiListener;
import ru.elytrix.vault.listener.VaultListener;
import ru.elytrix.vault.service.KeyRegistry;
import ru.elytrix.vault.service.VaultManager;
import ru.elytrix.vault.util.TextUtil;

public final class ElytrixVaultPlugin extends JavaPlugin {

    private FileConfiguration messages;
    private KeyRegistry keyRegistry;
    private VaultManager vaultManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessages();

        this.keyRegistry = new KeyRegistry(this);
        this.keyRegistry.load();

        this.vaultManager = new VaultManager(this);
        this.vaultManager.load();

        VaultCommand commandExecutor = new VaultCommand(this);
        PluginCommand cmd = getCommand("evault");
        if (cmd != null) {
            cmd.setExecutor(commandExecutor);
            cmd.setTabCompleter(commandExecutor);
        }

        Bukkit.getPluginManager().registerEvents(new VaultListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);

        for (Player online : Bukkit.getOnlinePlayers()) {
            keyRegistry.flushPendingFor(online);
        }
    }

    @Override
    public void onDisable() {
        if (vaultManager != null) {
            vaultManager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        loadMessages();
        keyRegistry.load();
        vaultManager.load();
    }

    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) {
            saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String rawMsg(String key) {
        return messages.getString(key, key);
    }

    public String msg(String key) {
        String prefix = messages.getString("prefix", "&7« &#F8BEFBХранилище &7» &f");
        return TextUtil.color(prefix + messages.getString(key, key));
    }

    public KeyRegistry getKeyRegistry() {
        return keyRegistry;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }
}
