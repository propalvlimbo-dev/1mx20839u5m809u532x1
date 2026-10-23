package ru.rooyzee.elytrixcasino;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixcasino.automate.AutomateManager;
import ru.rooyzee.elytrixcasino.automate.AutomateSettings;
import ru.rooyzee.elytrixcasino.command.CasinoCommand;
import ru.rooyzee.elytrixcasino.config.Messages;
import ru.rooyzee.elytrixcasino.data.DataManager;
import ru.rooyzee.elytrixcasino.gui.AutomateGui;
import ru.rooyzee.elytrixcasino.gui.GuiConfig;
import ru.rooyzee.elytrixcasino.gui.RouletteGui;
import ru.rooyzee.elytrixcasino.listener.CasinoListener;
import ru.rooyzee.elytrixcasino.listener.VirtualInteractionInjector;
import ru.rooyzee.elytrixcasino.restore.CasinoLoader;
import ru.rooyzee.elytrixcasino.roulette.RouletteManager;
import ru.rooyzee.elytrixcasino.roulette.RouletteSettings;
import ru.rooyzee.elytrixcasino.util.SkinFetcher;

public class Main extends JavaPlugin {

    private static Main instance;

    private Economy economy;
    private DataManager dataManager;
    private RouletteManager rouletteManager;
    private AutomateManager automateManager;
    private RouletteGui gui;
    private AutomateGui automateGui;
    private Messages messages;
    private GuiConfig rouletteGuiConfig;
    private GuiConfig automateGuiConfig;
    private RouletteSettings settings;
    private AutomateSettings automateSettings;
    private VirtualInteractionInjector interactionInjector;
    private CasinoListener casinoListener;
    private CasinoLoader loader;
    private String skinValue = "";
    private String skinSignature = "";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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

        messages = new Messages(this);
        rouletteGuiConfig = new GuiConfig(this, "roulette.yml");
        automateGuiConfig = new GuiConfig(this, "automate.yml");
        settings = RouletteSettings.load(getConfig(), getLogger());
        automateSettings = AutomateSettings.load(getConfig(), getLogger());
        skinValue = settings.getSkinValue();
        skinSignature = settings.getSkinSignature();
        dataManager = new DataManager(this);
        rouletteManager = new RouletteManager(this);
        automateManager = new AutomateManager(this);
        gui = new RouletteGui(this);
        automateGui = new AutomateGui(this);
        loader = new CasinoLoader(this);

        casinoListener = new CasinoListener(this, gui);
        interactionInjector = new VirtualInteractionInjector(this, casinoListener);

        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(automateGui, this);
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

        // Рулетки и автоматы восстанавливаются не здесь, а на следующем тике (CasinoLoader):
        // в onEnable миры и чанки могут быть ещё не готовы, из-за чего записи из data.yml
        // молча пропускались и после рестарта казино «пропадало».
        loader.start();
        loadSkinAsync();
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
        rouletteManager.removeAll();
        automateManager.removeAll();
        reloadConfig();
        messages.reload();
        rouletteGuiConfig.reload();
        automateGuiConfig.reload();
        settings = RouletteSettings.load(getConfig(), getLogger());
        automateSettings = AutomateSettings.load(getConfig(), getLogger());
        if (!settings.getSkinValue().isEmpty()) {
            skinValue = settings.getSkinValue();
            skinSignature = settings.getSkinSignature();
        }
        dataManager.reload();
        loader.reload();
        loadSkinAsync();
    }

    public void send(CommandSender receiver, String path, String... replacements) {
        messages.send(receiver, path, replacements);
    }

    public String rawMsg(String path) {
        return messages.raw(path);
    }

    public Messages getMessages() {
        return messages;
    }

    public GuiConfig getRouletteGuiConfig() {
        return rouletteGuiConfig;
    }

    public GuiConfig getAutomateGuiConfig() {
        return automateGuiConfig;
    }

    public static Main getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public CasinoLoader getLoader() {
        return loader;
    }

    public RouletteManager getRouletteManager() {
        return rouletteManager;
    }

    public AutomateManager getAutomateManager() {
        return automateManager;
    }

    public RouletteGui getGui() {
        return gui;
    }

    public AutomateGui getAutomateGui() {
        return automateGui;
    }

    public CasinoListener getCasinoListener() {
        return casinoListener;
    }

    public RouletteSettings getSettings() {
        return settings;
    }

    public AutomateSettings getAutomateSettings() {
        return automateSettings;
    }

    public String getSkinValue() {
        return skinValue;
    }

    public String getSkinSignature() {
        return skinSignature;
    }
}
