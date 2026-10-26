package ru.elytrix.dungeons.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;

/** Applies temporary Paper/Spigot log switches and restores their prior values when disabled. */
public final class ConsoleLogController {

    private static final String[] ADMIN_COMMAND_RULE_NAMES = {"logAdminCommands", "log_admin_commands"};

    private final ElytrixDungeonsPlugin plugin;
    private final Map<String, Boolean> previousSpigotFlags = new LinkedHashMap<>();
    private final Map<World, Boolean> previousAdminCommandRules = new IdentityHashMap<>();
    private boolean warnedAboutSpigotConfig;
    private boolean warnedAboutAdminCommandRule;

    public ConsoleLogController(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply() {
        restore();
        boolean applied = false;
        if (plugin.getConfig().getBoolean("console-logs.suppress-named-entity-deaths", true)) {
            applied |= suppressSpigotFlag("logNamedDeaths");
        }
        if (plugin.getConfig().getBoolean("console-logs.suppress-player-command-logs", true)) {
            applied |= suppressSpigotFlag("logCommands");
        }
        if (plugin.getConfig().getBoolean("console-logs.suppress-admin-command-feedback", true)) {
            for (World world : Bukkit.getWorlds()) applied |= suppressAdminCommandRule(world);
        }
        if (applied) {
            plugin.getLogger().fine("Configured Paper/Spigot entity-death and command logging for quieter console output.");
        }
    }

    public void restore() {
        for (Map.Entry<String, Boolean> entry : previousSpigotFlags.entrySet()) {
            try {
                Field field = spigotConfigField(entry.getKey());
                field.setBoolean(null, entry.getValue());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        previousSpigotFlags.clear();

        for (Map.Entry<World, Boolean> entry : previousAdminCommandRules.entrySet()) {
            writeAdminCommandRule(entry.getKey(), entry.getValue());
        }
        previousAdminCommandRules.clear();
    }

    private boolean suppressSpigotFlag(String fieldName) {
        try {
            Field field = spigotConfigField(fieldName);
            if (!field.getType().equals(boolean.class) || !Modifier.isStatic(field.getModifiers())) return false;
            previousSpigotFlags.put(fieldName, field.getBoolean(null));
            field.setBoolean(null, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warnedAboutSpigotConfig) {
                warnedAboutSpigotConfig = true;
                plugin.getLogger().warning("Cannot change Paper/Spigot logging flags at runtime; configure settings.log-named-deaths and commands.log in spigot.yml.");
            }
            return false;
        }
    }

    private static Field spigotConfigField(String name) throws ReflectiveOperationException {
        Class<?> configClass = Class.forName("org.spigotmc.SpigotConfig");
        return configClass.getField(name);
    }

    private boolean suppressAdminCommandRule(World world) {
        if (world == null) return false;
        Boolean current = readAdminCommandRule(world);
        if (current == null) {
            if (!warnedAboutAdminCommandRule) {
                warnedAboutAdminCommandRule = true;
                plugin.getLogger().warning("Cannot access logAdminCommands; disable that gamerule in the server world settings.");
            }
            return false;
        }
        previousAdminCommandRules.put(world, current);
        return writeAdminCommandRule(world, false);
    }

    private static Boolean readAdminCommandRule(World world) {
        try {
            Class<?> ruleClass = Class.forName("org.bukkit.GameRule");
            Object rule = findAdminCommandRule(ruleClass);
            Method getter = World.class.getMethod("getGameRuleValue", ruleClass);
            Object value = getter.invoke(world, rule);
            if (value instanceof Boolean bool) return bool;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        for (String ruleName : ADMIN_COMMAND_RULE_NAMES) {
            try {
                Method getter = World.class.getMethod("getGameRuleValue", String.class);
                Object value = getter.invoke(world, ruleName);
                if (value != null) return Boolean.parseBoolean(String.valueOf(value));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static boolean writeAdminCommandRule(World world, boolean value) {
        try {
            Class<?> ruleClass = Class.forName("org.bukkit.GameRule");
            Object rule = findAdminCommandRule(ruleClass);
            Method setter = World.class.getMethod("setGameRule", ruleClass, Object.class);
            Object result = setter.invoke(world, rule, value);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        for (String ruleName : ADMIN_COMMAND_RULE_NAMES) {
            try {
                Method setter = World.class.getMethod("setGameRuleValue", String.class, String.class);
                Object result = setter.invoke(world, ruleName, Boolean.toString(value));
                if (!(result instanceof Boolean) || (Boolean) result) return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
    }

    private static Object findAdminCommandRule(Class<?> ruleClass) throws ReflectiveOperationException {
        try {
            return ruleClass.getField("LOG_ADMIN_COMMANDS").get(null);
        } catch (NoSuchFieldException ignored) {
            Class<?> rulesClass = Class.forName("org.bukkit.GameRules");
            return rulesClass.getField("LOG_ADMIN_COMMANDS").get(null);
        }
    }
}
