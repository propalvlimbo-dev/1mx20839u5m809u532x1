package ru.rooyzee.elytrixsubscribe;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixsubscribe.command.SubscribeCommand;
import ru.rooyzee.elytrixsubscribe.database.Storage;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.functions.FunctionManager;
import ru.rooyzee.elytrixsubscribe.menu.MenuService;
import ru.rooyzee.elytrixsubscribe.menu.Menus;
import ru.rooyzee.elytrixsubscribe.pet.PetManager;
import ru.rooyzee.elytrixsubscribe.placeholder.CustomPlaceHolder;
import ru.rooyzee.elytrixsubscribe.service.SubService;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;
import ru.rooyzee.elytrixsubscribe.sub.SubManager;
import ru.rooyzee.elytrixsubscribe.utils.ChatInput;
import ru.rooyzee.elytrixsubscribe.utils.SignInput;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

/**
 * ElytrixSubscribe 2.0 — подписка ElytriX+.
 *
 * Что внутри: меню на BMenu, три питомца (попугай, аллей, пчела) со своими
 * режимами поведения, функции подписки и хранение настроек в data.yml.
 */
public final class Main extends JavaPlugin {
    private static Main instance;
    private SubService service;
    private Storage storage;
    private SubManager subManager;
    private FunctionManager functionsManager;
    private PetManager petManager;
    private MenuService menuService;
    private Menus menus;
    private CustomPlaceHolder placeholder;
    private ChatInput chatInput;
    private SignInput signInput;

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        this.mergeDefaults();

        if (!this.checkPlugin("LuckPerms") || !this.checkPlugin("PlaceholderAPI")) {
            this.setEnabled(false);
            return;
        }
        if (!this.checkPlugin("BMenu")) {
            this.getLogger().warning("BMenu не найден: меню подписки не откроются.");
        }
        if (!this.checkPlugin("VirtualEntityApi", false)) {
            this.getLogger().warning("VirtualEntityApi не найден: питомцы выключены.");
        }

        this.storage = new Storage(this);
        this.service = new SubService(this);
        this.subManager = new SubManager(this);

        this.signInput = new SignInput(this);
        try {
            Bukkit.getPluginManager().registerEvents(this.signInput, this);
        } catch (Throwable error) {
            this.getLogger().warning("Табличка для ввода недоступна: " + error);
        }

        this.chatInput = new ChatInput(this);
        try {
            Bukkit.getPluginManager().registerEvents(this.chatInput, this);
            this.chatInput.registerPaper(Bukkit.getPluginManager());
            this.chatInput.markRegistered();
        } catch (Throwable error) {
            // не Paper — ввода имени в чате не будет, работает команда с именем
            this.getLogger().warning("Ввод в чате недоступен: " + error);
        }

        this.functionsManager = new FunctionManager(this);
        this.petManager = new PetManager(this);
        this.petManager.start();
        Bukkit.getPluginManager().registerEvents(this.petManager, this);

        this.menus = new Menus(this);
        this.menuService = new MenuService(this);

        SubscribeCommand command = new SubscribeCommand(this);
        this.getCommand("subscribe").setExecutor(command);
        this.getCommand("subscribe").setTabCompleter(command);

        this.placeholder = new CustomPlaceHolder(this);
        try {
            this.placeholder.register();
        } catch (Throwable error) {
            this.getLogger().warning("Плейсхолдеры не зарегистрированы: " + error);
        }

    }

    @Override
    public void onDisable() {
        if (this.signInput != null) {
            this.signInput.shutdown();
        }
        if (this.petManager != null) {
            this.petManager.shutdown();
        }
        if (this.subManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    PlayerSub sub = this.subManager.convertPlayer(player);
                    List<Function> copy = new ArrayList<Function>(sub.functions(this.functionsManager));
                    for (Function function : copy) {
                        try {
                            function.clear(player);
                        } catch (RuntimeException ignored) {
                        }
                    }
                } catch (ConvertPlayerException ignored) {
                }
            }
            this.subManager.saveAll();
        }
        if (this.placeholder != null) {
            try {
                this.placeholder.unregister();
            } catch (RuntimeException ignored) {
            }
        }
        if (this.storage != null) {
            this.storage.saveNow();
        }
        instance = null;
    }

    /**
     * Дописывает в config.yml ключи, которых там ещё нет (после обновления
     * плагина старый конфиг не должен ломать меню и питомцев).
     */
    private void mergeDefaults() {
        InputStream stream = null;
        try {
            stream = this.getResource("config.yml");
            if (stream == null) return;
            Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue;
                if (this.getConfig().isSet(key)) continue;
                this.getConfig().set(key, defaults.get(key));
                changed = true;
            }
            if (changed) this.saveConfig();
        } catch (RuntimeException error) {
            this.getLogger().warning("Не удалось обновить config.yml: " + error);
        } finally {
            try {
                if (stream != null) stream.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean checkPlugin(String name) {
        return this.checkPlugin(name, true);
    }

    private boolean checkPlugin(String name, boolean required) {
        if (Bukkit.getPluginManager().isPluginEnabled(name)) return true;
        if (required) {
            this.getLogger().severe("Установи " + name + " — без него подписка не работает.");
        }
        return false;
    }

    public static Main getInstance() {
        return instance;
    }

    public SubService getService() {
        return this.service;
    }

    public Storage getStorage() {
        return this.storage;
    }

    public SubManager getSubManager() {
        return this.subManager;
    }

    public FunctionManager getFunctionsManager() {
        return this.functionsManager;
    }

    public PetManager getPetManager() {
        return this.petManager;
    }

    public MenuService getMenuService() {
        return this.menuService;
    }

    public Menus getMenus() {
        return this.menus;
    }

    public ChatInput getChatInput() {
        return this.chatInput;
    }

    public SignInput getSignInput() {
        return this.signInput;
    }

    /** Файл настроек меню — нужен, чтобы не открывать несуществующее меню. */
    public File menusFolder() {
        return new File(this.getDataFolder().getParentFile(), "BMenu/menu");
    }

    public void msg(Player player, String text) {
        if (player == null || text == null) return;
        player.sendMessage(Utils.color(text));
    }

    public String message(String key, String fallback) {
        String value = this.getConfig().getString("messages." + key);
        return value == null ? fallback : value;
    }

    public String setting(String key, String fallback) {
        String value = this.getConfig().getString("settings." + key);
        return value == null ? fallback : value;
    }
}
