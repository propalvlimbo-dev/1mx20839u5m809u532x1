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
        dungeonManager.removeLegacyDungeonDisplays();
        dungeonManager.loadSchematics();
        dungeonManager.syncAllWorldGuardRegions();
    }

    private void migrateLegacyConfig() {
        int version = getConfig().isSet("config-version") ? getConfig().getInt("config-version", 0) : 0;
        boolean changed = false;

        if (version < 2) {
            // Upgrade the old built-in defaults without changing newer user tuning.
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
            version = 2;
            changed = true;
        }

        if (version < 3) {
            if (!getConfig().isSet("surface-dungeon.ghosts.initial-count")) {
                getConfig().set("surface-dungeon.ghosts.initial-count", 6);
            }
            if (!getConfig().isSet("surface-dungeon.ghosts.respawn-cooldown-seconds")) {
                getConfig().set("surface-dungeon.ghosts.respawn-cooldown-seconds", 7200);
            }
            getConfig().set("surface-dungeon.ghosts.max-alive", null);
            getConfig().set("surface-dungeon.ghosts.respawn-every-seconds", null);
            getConfig().set("hologram.enabled", false);
            getConfig().set("hologram.chest-hint.enabled", false);
            if (!getConfig().isSet("auctionbar.enabled")) getConfig().set("auctionbar.enabled", true);
            if (!getConfig().isSet("auctionbar.cycle-seconds")) getConfig().set("auctionbar.cycle-seconds", 15);
            String auctionBrand = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» ";
            if (!getConfig().isSet("auctionbar.reset-line")) {
                getConfig().set("auctionbar.reset-line", auctionBrand + "&7{dungeon} &8┃ &fПолный сброс через &#96FF91{timer}");
            }
            if (!getConfig().isSet("auctionbar.ghosts-line")) {
                getConfig().set("auctionbar.ghosts-line", auctionBrand
                        + "&7{dungeon} &8┃ &f{ghost-label}: &#F8BEFB{ghosts}&7/{ghost-total}");
            }
            if (!getConfig().isSet("auctionbar.loot-line")) {
                getConfig().set("auctionbar.loot-line", auctionBrand
                        + "&7{dungeon} &8┃ &fЛут обновится через &#96FF91{timer}");
            }
            getConfig().set("config-version", 3);
            changed = true;
        }

        if (version < 4) {
            if (!getConfig().isSet("hologram.ghost.enabled")) getConfig().set("hologram.ghost.enabled", true);
            if (!getConfig().isSet("hologram.shulker.enabled")) getConfig().set("hologram.shulker.enabled", true);
            String auctionBrand = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» ";
            String ghostsLine = getConfig().getString("auctionbar.ghosts-line", "");
            if (ghostsLine != null && ghostsLine.contains("{ghost-state}")) {
                getConfig().set("auctionbar.ghosts-line", auctionBrand
                        + "&7{dungeon} &8┃ &f{ghost-label}: &#F8BEFB{ghosts}&7/{ghost-total}");
            }
            String resetLine = getConfig().getString("auctionbar.reset-line", "");
            if (resetLine != null && resetLine.contains("Данж восстановится через")) {
                getConfig().set("auctionbar.reset-line", auctionBrand
                        + "&7{dungeon} &8┃ &fПолный сброс через &#96FF91{timer}");
            }
            String lootLine = getConfig().getString("auctionbar.loot-line", "");
            if (lootLine != null && lootLine.contains("Лут обновится")) {
                getConfig().set("auctionbar.loot-line", auctionBrand
                        + "&7{dungeon} &8┃ &fЛут шалкеров обновится через &#96FF91{timer}");
            }
            getConfig().set("config-version", 4);
            changed = true;
        }

        if (version < 5) {
            // Ghosts now use their entity name only; no separate floating text hologram is created.
            getConfig().set("hologram.ghost.enabled", false);
            getConfig().set("config-version", 5);
            changed = true;
        }
        if (version < 6) {
            if (!getConfig().isSet("underground-dungeon.claim-padding-blocks")) {
                getConfig().set("underground-dungeon.claim-padding-blocks", 8);
            }
            getConfig().set("config-version", 6);
            changed = true;
        }
        if (changed) saveConfig();
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
