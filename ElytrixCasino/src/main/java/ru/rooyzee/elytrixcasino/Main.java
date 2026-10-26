package ru.rooyzee.elytrixcasino;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixcasino.automate.AutomateManager;
import ru.rooyzee.elytrixcasino.automate.AutomateSettings;
import ru.rooyzee.elytrixcasino.bet.ActiveBetManager;
import ru.rooyzee.elytrixcasino.command.CasinoCommand;
import ru.rooyzee.elytrixcasino.config.Messages;
import ru.rooyzee.elytrixcasino.data.DataManager;
import ru.rooyzee.elytrixcasino.gui.AutomateGui;
import ru.rooyzee.elytrixcasino.gui.GuiConfig;
import ru.rooyzee.elytrixcasino.gui.MenuAnimator;
import ru.rooyzee.elytrixcasino.gui.MinerGui;
import ru.rooyzee.elytrixcasino.gui.RouletteGui;
import ru.rooyzee.elytrixcasino.listener.CasinoListener;
import ru.rooyzee.elytrixcasino.listener.VirtualInteractionInjector;
import ru.rooyzee.elytrixcasino.miner.MinerManager;
import ru.rooyzee.elytrixcasino.miner.MinerSettings;
import ru.rooyzee.elytrixcasino.restore.CasinoLoader;
import ru.rooyzee.elytrixcasino.roulette.RouletteManager;
import ru.rooyzee.elytrixcasino.roulette.RouletteSettings;
import ru.rooyzee.elytrixcasino.util.SkinFetcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Main extends JavaPlugin {

    private static final int CURRENT_CONFIG_VERSION = 3;

    private static Main instance;

    private Economy economy;
    private ActiveBetManager activeBetManager;
    private MenuAnimator animator;
    private DataManager dataManager;
    private RouletteManager rouletteManager;
    private AutomateManager automateManager;
    private MinerManager minerManager;
    private RouletteGui gui;
    private AutomateGui automateGui;
    private MinerGui minerGui;
    private Messages messages;
    private GuiConfig rouletteGuiConfig;
    private GuiConfig automateGuiConfig;
    private RouletteSettings settings;
    private AutomateSettings automateSettings;
    private MinerSettings minerSettings;
    private VirtualInteractionInjector interactionInjector;
    private CasinoListener casinoListener;
    private CasinoLoader loader;
    private String skinValue = "";
    private String skinSignature = "";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfigsIfNeeded();

        if (Bukkit.getPluginManager().getPlugin("VirtualEntityApi") == null) {
            getLogger().severe("VirtualEntityApi не найден! Отключаюсь.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!setupEconomy()) {
            getLogger().severe("Vault (или плагин экономики) не найден! Отключаюсь.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        activeBetManager = new ActiveBetManager();
        animator = new MenuAnimator(this);

        messages = new Messages(this);
        rouletteGuiConfig = new GuiConfig(this, "roulette.yml");
        automateGuiConfig = new GuiConfig(this, "automate.yml");
        settings = RouletteSettings.load(getConfig(), getLogger());
        automateSettings = AutomateSettings.load(getConfig(), getLogger());
        minerSettings = new MinerSettings();
        minerSettings.load(getConfig());

        skinValue = settings.getSkinValue();
        skinSignature = settings.getSkinSignature();

        dataManager = new DataManager(this);
        rouletteManager = new RouletteManager(this);
        automateManager = new AutomateManager(this);
        minerManager = new MinerManager(this);

        gui = new RouletteGui(this);
        automateGui = new AutomateGui(this);
        minerGui = new MinerGui(this);
        loader = new CasinoLoader(this);

        casinoListener = new CasinoListener(this, gui);
        interactionInjector = new VirtualInteractionInjector(this, casinoListener);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(automateGui, this);
        Bukkit.getPluginManager().registerEvents(minerGui, this);
        Bukkit.getPluginManager().registerEvents(casinoListener, this);
        Bukkit.getPluginManager().registerEvents(interactionInjector, this);
        Bukkit.getPluginManager().registerEvents(loader, this);
        interactionInjector.injectAll();

        PluginCommand command = getCommand("elytrixcasino");
        if (command != null) {
            CasinoCommand executor = new CasinoCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Команда elytrixcasino не найдена в plugin.yml!");
        }

        loader.start();
        loadSkinAsync();
    }

    private void migrateConfigsIfNeeded() {
        reloadConfig();
        File currentConfigFile = new File(getDataFolder(), "config.yml");
        int oldVersion = YamlConfiguration.loadConfiguration(currentConfigFile)
                .getInt("config-version", 1);
        if (oldVersion >= CURRENT_CONFIG_VERSION) {
            return;
        }

        String[] resources = {
                "config.yml",
                "messages.yml",
                "GUI/roulette.yml",
                "GUI/miner.yml",
                "GUI/automate.yml"
        };
        for (String resource : resources) {
            if (!mergeResourceDefaults(resource, oldVersion)) {
                // Не помечаем миграцию завершённой: следующий запуск сможет повторить её.
                return;
            }
        }

        reloadConfig();
        getConfig().set("config-version", CURRENT_CONFIG_VERSION);
        saveConfig();
    }

    /** Сохраняет пользовательские значения и добавляет только новые значения из ресурса. */
    private boolean mergeResourceDefaults(String resourcePath, int oldVersion) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            try {
                saveResource(resourcePath, false);
                return true;
            } catch (IllegalArgumentException error) {
                getLogger().warning("Не найден ресурс конфигурации " + resourcePath + ": " + error.getMessage());
                return false;
            }
        }

        File backup = new File(file.getParentFile(), file.getName() + ".pre-v" + oldVersion + ".bak");
        try {
            if (!backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException error) {
            getLogger().warning("Не удалось создать резервную копию " + backup.getName()
                    + "; конфиг не изменён: " + error.getMessage());
            return false;
        }

        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (Exception error) {
            getLogger().warning("Конфиг " + file.getName() + " повреждён; исходник сохранён как "
                    + backup.getName() + ", будут восстановлены значения по умолчанию.");
            current = new YamlConfiguration();
        }

        try (InputStream stream = getResource(resourcePath)) {
            if (stream == null) {
                getLogger().warning("Не найден ресурс конфигурации " + resourcePath);
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            current.addDefaults(defaults);
            current.options().copyDefaults(true);
            current.save(file);
            return true;
        } catch (IOException error) {
            getLogger().warning("Не удалось обновить конфиг " + file.getName() + ": " + error.getMessage());
            return false;
        }
    }

    private void loadSkinAsync() {
        String owner = settings.getSkinPlayer();
        if (owner.isEmpty() || !skinValue.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            String[] skin;
            try {
                skin = SkinFetcher.fetch(owner);
            } catch (Exception e) {
                getLogger().warning("Не удалось загрузить скин игрока " + owner + ": " + e.getMessage());
                return;
            }
            if (skin == null) {
                getLogger().warning("Скин игрока " + owner + " не найден.");
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                skinValue = skin[0];
                skinSignature = skin[1];
                if (rouletteManager != null) {
                    rouletteManager.applySkin(skinValue, skinSignature);
                }
                getLogger().info("Скин NPC загружен с профиля " + owner);
            });
        });
    }

    @Override
    public void onDisable() {
        if (animator != null) {
            animator.stopAll();
        }
        if (minerGui != null) {
            minerGui.shutdown();
        }
        if (loader != null) {
            loader.shutdown();
        }
        if (interactionInjector != null) {
            interactionInjector.uninjectAll();
        }
        if (rouletteManager != null) {
            rouletteManager.removeAll();
        }
        if (automateManager != null) {
            automateManager.unloadAll();
        }
        if (minerManager != null) {
            minerManager.removeAll();
        }
        if (activeBetManager != null) {
            activeBetManager.clearAll();
        }
        instance = null;
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    public void reload() {
        animator.stopAll();
        minerGui.shutdown();
        rouletteManager.removeAll();
        automateManager.removeAll();
        minerManager.removeAll();
        activeBetManager.clearAll();

        migrateConfigsIfNeeded();
        reloadConfig();
        messages.reload();
        rouletteGuiConfig.reload();
        automateGuiConfig.reload();
        minerGui.reload();

        settings = RouletteSettings.load(getConfig(), getLogger());
        automateSettings = AutomateSettings.load(getConfig(), getLogger());
        minerSettings.load(getConfig());
        skinValue = settings.getSkinValue();
        skinSignature = settings.getSkinSignature();

        dataManager.reload();
        loader.reload();
        loadSkinAsync();
    }

    public void send(CommandSender receiver, String path, String... replacements) {
        messages.send(receiver, path, replacements);
    }

    public String rawMsg(String path) { return messages.raw(path); }
    public Messages getMessages() { return messages; }
    public GuiConfig getRouletteGuiConfig() { return rouletteGuiConfig; }
    public GuiConfig getAutomateGuiConfig() { return automateGuiConfig; }
    public static Main getInstance() { return instance; }
    public Economy getEconomy() { return economy; }
    public ActiveBetManager getActiveBets() { return activeBetManager; }
    public MenuAnimator getAnimator() { return animator; }
    public DataManager getDataManager() { return dataManager; }
    public CasinoLoader getLoader() { return loader; }
    public RouletteManager getRouletteManager() { return rouletteManager; }
    public AutomateManager getAutomateManager() { return automateManager; }
    public MinerManager getMinerManager() { return minerManager; }
    public RouletteGui getGui() { return gui; }
    public AutomateGui getAutomateGui() { return automateGui; }
    public MinerGui getMinerGui() { return minerGui; }
    public CasinoListener getCasinoListener() { return casinoListener; }
    public RouletteSettings getSettings() { return settings; }
    public AutomateSettings getAutomateSettings() { return automateSettings; }
    public MinerSettings getMinerSettings() { return minerSettings; }
    public String getSkinValue() { return skinValue; }
    public String getSkinSignature() { return skinSignature; }
}
