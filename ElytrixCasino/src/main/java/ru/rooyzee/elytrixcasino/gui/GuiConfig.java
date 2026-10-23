package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.config.YamlFile;

import java.util.List;

/**
 * Конфиг одного меню из папки GUI/ (меню будет несколько, у каждого свой файл).
 */
public class GuiConfig {

    private final Main plugin;
    private final YamlFile file;

    public GuiConfig(Main plugin, String fileName) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "GUI/" + fileName);
    }

    public void reload() {
        file.reload();
    }

    public FileConfiguration raw() {
        return file.get();
    }

    public String getString(String path, String def) {
        return file.get().getString(path, def);
    }

    public int getInt(String path, int def) {
        return file.get().getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return file.get().getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return file.get().getStringList(path);
    }

    public List<Integer> getIntegerList(String path) {
        return file.get().getIntegerList(path);
    }

    /**
     * Материал с понятным предупреждением вместо тихой подмены.
     */
    public Material getMaterial(String path, Material def) {
        String raw = file.get().getString(path);
        if (raw == null || raw.isEmpty()) {
            return def;
        }
        Material material = Material.matchMaterial(raw);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Неизвестный предмет '" + raw + "' в " + file.getName() + " (" + path + ")");
            return def;
        }
        return material;
    }
}
