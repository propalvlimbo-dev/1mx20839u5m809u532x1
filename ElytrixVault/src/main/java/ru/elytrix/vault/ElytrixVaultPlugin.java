package ru.elytrix.vault;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    private static final int CURRENT_CONFIG_VERSION = 2;

    private FileConfiguration messages;
    private KeyRegistry keyRegistry;
    private VaultManager vaultManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        upgradeConfigIfNeeded();
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
        upgradeConfigIfNeeded();
        loadMessages();
        keyRegistry.load();
        vaultManager.load();
    }

    private void upgradeConfigIfNeeded() {
        File file = new File(getDataFolder(), "config.yml");
        int oldVersion = YamlConfiguration.loadConfiguration(file).getInt("config-version", 1);
        if (oldVersion >= CURRENT_CONFIG_VERSION) return;

        File backup = new File(file.getParentFile(), file.getName() + ".pre-v" + oldVersion + ".bak");
        try {
            if (!backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException error) {
            getLogger().warning("Не удалось создать резервную копию config.yml; файл не изменён: "
                    + error.getMessage());
            return;
        }

        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (Exception error) {
            getLogger().warning("config.yml повреждён; исходник сохранён как " + backup.getName()
                    + ", будут восстановлены значения по умолчанию.");
            current = new YamlConfiguration();
        }

        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                getLogger().warning("Ресурс config.yml отсутствует в JAR; миграция пропущена.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            current.addDefaults(defaults);
            current.options().copyDefaults(true);
            current.set("config-version", CURRENT_CONFIG_VERSION);
            current.save(file);
            reloadConfig();
        } catch (IOException error) {
            getLogger().warning("Не удалось обновить config.yml: " + error.getMessage());
        }
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
