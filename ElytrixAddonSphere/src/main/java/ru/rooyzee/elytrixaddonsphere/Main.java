package ru.rooyzee.elytrixaddonsphere;

import dev.by1337.bc.addon.AbstractAddon;
import dev.by1337.bc.animation.AnimationRegistry;
import org.bukkit.plugin.Plugin;
import org.by1337.blib.util.SpacedNameKey;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class Main extends AbstractAddon {

    public static final String ANIMATION_KEY = "elytrix:sphere";
    private static final String RESOURCE_NAME = "sphereAnimation.yml";
    private static final String TARGET_DIR = "animations/sphere";
    /** Версия конфига: если в файле другая — он уходит в .old и создаётся свежий. */
    private static final String CONFIG_VERSION = "1.38";

    @Override
    public void onEnable() {
        AnimationRegistry.INSTANCE.register(ANIMATION_KEY, SphereAnimator::new);
        saveDefaultAnimationFile();
        getPlugin().getLogger().info("Анимация " + ANIMATION_KEY + " зарегистрирована");
    }

    @Override
    public void onDisable() {
        AnimationRegistry.INSTANCE.unregister(new SpacedNameKey(ANIMATION_KEY));
    }

    private void saveDefaultAnimationFile() {
        Plugin plugin = getPlugin();
        try {
            File dir = new File(plugin.getDataFolder(), TARGET_DIR);
            Files.createDirectories(dir.toPath());

            File targetFile = new File(dir, RESOURCE_NAME);

            if (targetFile.exists()) {
                String current = new String(Files.readAllBytes(targetFile.toPath()), StandardCharsets.UTF_8);
                if (current.contains("configVersion: \"" + CONFIG_VERSION + "\"")) {
                    return; // конфиг актуальный
                }
                File backup = new File(dir, "sphereAnimation.old.yml");
                Files.move(targetFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Старый конфиг сохранён как " + backup.getName()
                        + ", создаю новый " + CONFIG_VERSION);
            }

            try (InputStream in = getResource(RESOURCE_NAME)) {
                if (in == null) {
                    plugin.getLogger().warning("Ресурс " + RESOURCE_NAME + " не найден внутри jar!");
                    return;
                }
                Files.copy(in, targetFile.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось сохранить " + RESOURCE_NAME, e);
        }
    }
}
