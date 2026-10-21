package dev.epicduels.model;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Арена дуэлей.
 *
 * ElytriX-патч: мир арены больше не обязан называться arena_template_&lt;имя&gt;.
 * Мост ElytrixDuelsBridge вызывает {@link #setDefaultWorld(String)}, и все арены
 * живут в одном общем мире (например, в готовом мире duel), отличаясь только
 * точками спавна. Если общий мир не задан, поведение прежнее.
 */
public class Arena {

    private static volatile String defaultWorld = null;
    private static final Map<String, String> WORLDS = new ConcurrentHashMap<String, String>();

    /** Общий мир для всех арен; null или пусто — старое поведение. */
    public static void setDefaultWorld(String world) {
        defaultWorld = world;
    }

    public static String getDefaultWorld() {
        return defaultWorld;
    }

    /** Свой мир для одной арены (важнее общего). */
    public static void setWorld(String arenaName, String world) {
        if (arenaName == null) return;
        if (world == null || world.isEmpty()) WORLDS.remove(arenaName.toLowerCase(Locale.ROOT));
        else WORLDS.put(arenaName.toLowerCase(Locale.ROOT), world);
    }

    private String name;
    private Location spawn1;
    private Location spawn2;
    private boolean ready;
    private Material icon;

    public Arena(String name) {
        this.name = name;
        this.ready = false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorldName() {
        if (name != null) {
            String own = WORLDS.get(name.toLowerCase(Locale.ROOT));
            if (own != null && !own.isEmpty()) return own;
        }
        String shared = defaultWorld;
        if (shared != null && !shared.isEmpty()) return shared;
        return "arena_template_" + name;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public void setSpawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void setSpawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public Material getDisplayIcon() {
        return icon != null ? icon : Material.GRASS_BLOCK;
    }
}
