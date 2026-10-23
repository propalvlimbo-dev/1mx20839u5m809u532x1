package ru.elytrix.dungeons;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.elytrix.dungeons.command.DungeonCommand;
import ru.elytrix.dungeons.gui.DungeonMenuService;
import ru.elytrix.dungeons.hook.VaultAndItemsHook;
import ru.elytrix.dungeons.hook.WorldEditHook;
import ru.elytrix.dungeons.hook.WorldGuardHook;
import ru.elytrix.dungeons.listener.DungeonListener;
import ru.elytrix.dungeons.listener.DungeonMenuListener;
import ru.elytrix.dungeons.service.DungeonLootRegistry;
import ru.elytrix.dungeons.service.DungeonManager;
import ru.elytrix.dungeons.service.GhostController;
import ru.elytrix.dungeons.service.TrialController;
import ru.elytrix.dungeons.util.TextUtil;

public final class ElytrixDungeonsPlugin extends JavaPlugin {

    private FileConfiguration messages;
    private WorldGuardHook worldGuardHook;
    private WorldEditHook worldEditHook;
    private VaultAndItemsHook itemsHook;
    private DungeonLootRegistry lootRegistry;
    private GhostController ghostController;
    private TrialController trialController;
    private DungeonManager dungeonManager;
    private DungeonMenuService menuService;
    private DungeonCommand commandHandler;

    private NamespacedKey guiTargetPdc;
    private NamespacedKey wandPdc;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLegacyConfig();
        saveResourceIfMissing("messages.yml");
        reloadMessages();

        this.guiTargetPdc = new NamespacedKey(this, "gui_target");
        this.wandPdc = new NamespacedKey(this, "builder_wand");

        this.worldGuardHook = new WorldGuardHook();
        this.worldEditHook = new WorldEditHook();
        this.itemsHook = new VaultAndItemsHook();
        this.lootRegistry = new DungeonLootRegistry(this);
        this.lootRegistry.load();

        this.ghostController = new GhostController(this);
        this.trialController = new TrialController(this);
        this.dungeonManager = new DungeonManager(this);
        this.menuService = new DungeonMenuService(this);
        this.commandHandler = new DungeonCommand(this);

        PluginCommand dCmd = getCommand("dungeons");
        if (dCmd != null) {
            dCmd.setExecutor(commandHandler);
            dCmd.setTabCompleter(commandHandler);
        }
        PluginCommand adminCmd = getCommand("edungeon");
        if (adminCmd != null) {
            adminCmd.setExecutor(commandHandler);
            adminCmd.setTabCompleter(commandHandler);
        }

        Bukkit.getPluginManager().registerEvents(new DungeonListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DungeonMenuListener(this), this);

        this.dungeonManager.startup();
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) {
            dungeonManager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        migrateLegacyConfig();
        reloadMessages();
        lootRegistry.load();
        dungeonManager.loadSchematics();
        dungeonManager.syncAllWorldGuardRegions();
    }

    private void migrateLegacyConfig() {
        int version = getConfig().isSet("config-version") ? getConfig().getInt("config-version", 0) : 0;
        if (version >= 2) {
            return;
        }

        // Upgrade the old built-in 5% default without changing newer user tuning.
        if (Math.abs(getConfig().getDouble("surface-dungeon.ghosts.key-drop-chance", 5.0) - 5.0) < 0.001) {
            getConfig().set("surface-dungeon.ghosts.key-drop-chance", 35.0);
        }
        if (getConfig().getDouble("surface-dungeon.ambience.volume", 0.55) < 1.5) {
            getConfig().set("surface-dungeon.ambience.volume", 2.0);
        }
        getConfig().set("surface-dungeon.shulker-stacks", Math.max(14,
                getConfig().getInt("surface-dungeon.shulker-stacks", 14)));
        getConfig().set("surface-dungeon.shulker-refresh-seconds", 21600);
        getConfig().set("surface-dungeon.landscaping.enabled", true);
        getConfig().set("surface-dungeon.landscaping.radius", 6);
        getConfig().set("config-version", 2);
        saveConfig();
    }

    private void saveResourceIfMissing(String name) {
        File f = new File(getDataFolder(), name);
        if (!f.exists()) {
            saveResource(name, false);
        }
    }

    private void reloadMessages() {
        this.messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    public String prefix() {
        return TextUtil.color(messages.getString("prefix", "&#F8BEFBДанжи &8» &f"));
    }

    public String msg(String key) {
        return prefix() + TextUtil.color(messages.getString(key, key));
    }

    public WorldGuardHook getWorldGuardHook() { return worldGuardHook; }
    public WorldEditHook getWorldEditHook() { return worldEditHook; }
    public VaultAndItemsHook getItemsHook() { return itemsHook; }
    public DungeonLootRegistry getLootRegistry() { return lootRegistry; }
    public GhostController getGhostController() { return ghostController; }
    public TrialController getTrialController() { return trialController; }
    public DungeonManager getDungeonManager() { return dungeonManager; }
    public DungeonMenuService getMenuService() { return menuService; }
    public DungeonCommand getCommandHandler() { return commandHandler; }
    public NamespacedKey getGuiTargetPdc() { return guiTargetPdc; }
    public NamespacedKey getWandPdc() { return wandPdc; }
}
