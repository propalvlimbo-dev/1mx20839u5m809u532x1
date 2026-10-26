package ru.rooyzee.elytrixcemetery.graves;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;
import ru.rooyzee.elytrixcemetery.utils.ParticleUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Grave {

    public static final int REQUIRED_DIG_CYCLES = 20;

    private static final String DISPLAY_API = "org.bukkit.entity.Display";
    private static final String TEXT_DISPLAY_API = "org.bukkit.entity.TextDisplay";

    private final int id;
    private final Location digLocation;
    private final Location chestLocation;
    private final Location hologramLocation;

    private boolean dug = false;
    private long dugAt = 0L;
    private int digProgress = 0;
    private final String hologramName;
    private List<String> lastHologramLines = Collections.emptyList();
    private NamespacedKey hologramMarkerKey;

    public Grave(int id, Location digLocation, Location chestLocation, Location hologramLocation) {
        this.id = id;
        this.digLocation = digLocation;
        this.chestLocation = chestLocation;
        this.hologramLocation = hologramLocation;
        this.hologramName = "cemetery_grave_" + id;
    }

    public int getId() { return id; }
    public Location getDigLocation() { return digLocation; }
    public Location getChestLocation() { return chestLocation; }
    public Location getHologramLocation() { return hologramLocation; }
    public boolean isDug() { return dug; }
    public long getDugAt() { return dugAt; }
    public int getDigProgress() { return digProgress; }
    public String getHologramName() { return hologramName; }

    public void setDug(boolean dug) {
        this.dug = dug;
        if (dug) {
            digProgress = REQUIRED_DIG_CYCLES;
        } else {
            digProgress = 0;
            dugAt = 0L;
        }
    }

    /** Records one completed block-breaking cycle, never an individual hit. */
    public int recordDigCycle() {
        if (dug) return digProgress;
        if (digProgress < REQUIRED_DIG_CYCLES) digProgress++;
        return digProgress;
    }

    public boolean dig(Main plugin, Player player) {
        if (dug || digProgress < REQUIRED_DIG_CYCLES) return false;

        World world = digLocation.getWorld();
        if (world == null) return false;

        dug = true;
        dugAt = System.currentTimeMillis();

        // 1. Открываем могилу и оставляем игроку свободный двухблочный проход.
        Block block = digLocation.getBlock();
        block.setType(Material.AIR, false);
        block.getRelative(0, 1, 0).setType(Material.AIR, false);
        block.getRelative(0, 2, 0).setType(Material.AIR, false);

        // 2. Наполняем сундук лутом с учетом текущего онлайна
        Block cb = chestLocation.getBlock();
        if (cb.getType() != Material.CHEST) {
            cb.setType(Material.CHEST, false);
        }

        if (cb.getState() instanceof Chest) {
            Chest chest = (Chest) cb.getState();
            Inventory inv = chest.getInventory();
            inv.clear();

            int online = Bukkit.getOnlinePlayers().size();
            List<ItemStack> loot = plugin.getLootStorageManager().rollGraveDrops(online);

            ThreadLocalRandom rng = ThreadLocalRandom.current();
            int size = inv.getSize();
            for (ItemStack item : loot) {
                if (item != null && item.getType() != Material.AIR) {
                    for (int tries = 0; tries < 10; tries++) {
                        int slot = rng.nextInt(size);
                        if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                            inv.setItem(slot, item.clone());
                            break;
                        }
                    }
                }
            }
        }

        // 3. Шанс на появление мощного визер-скелета (Осквернённого Стража)
        double guardianChance = plugin.getConfigManager().getGuardianSpawnChance();
        if (ThreadLocalRandom.current().nextDouble(100.0) < guardianChance) {
            plugin.getGhostController().spawnGuardian(digLocation, player, this);
        }

        // 4. Звуки и эффекты раскопки
        world.playSound(digLocation, Sound.ITEM_SHOVEL_FLATTEN, 1.2f, 0.8f);
        world.playSound(digLocation, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 1.2f);
        world.playSound(digLocation, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 0.6f);

        ParticleUtil.spawnBlock(world, digLocation.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, Material.COARSE_DIRT);
        ParticleUtil.spawn(world, ParticleUtil.SOUL, digLocation.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.4, 0.2, 0.02);

        updateHologram(plugin);
        return true;
    }

    public void openChestFor(Player player) {
        Block cb = chestLocation.getBlock();
        if (cb.getState() instanceof Chest) {
            Chest chest = (Chest) cb.getState();
            player.openInventory(chest.getInventory());
            player.playSound(chestLocation, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    public void bury(Main plugin) {
        dug = false;
        dugAt = 0L;
        digProgress = 0;

        World world = digLocation.getWorld();
        if (world == null) return;

        Block block = digLocation.getBlock();
        block.setType(Material.COARSE_DIRT, false);

        // Очищаем сундук под землей
        Block cb = chestLocation.getBlock();
        if (cb.getState() instanceof Chest) {
            Chest chest = (Chest) cb.getState();
            chest.getInventory().clear();
        }

        ParticleUtil.spawnBlock(world, digLocation.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, Material.COARSE_DIRT);
        updateHologram(plugin);
    }

    public void updateHologram(Main plugin) {
        World hologramWorld = hologramLocation.getWorld();
        if (hologramWorld == null || !hologramWorld.isChunkLoaded(
                hologramLocation.getBlockX() >> 4, hologramLocation.getBlockZ() >> 4)) return;
        if (!plugin.getConfigManager().isGraveHologramsEnabled()) {
            removeHologram();
            return;
        }

        List<String> lines = buildHoloLines();
        if (updateTransparentTextDisplay(plugin, lines)) {
            removeDecentHologram();
            lastHologramLines = new ArrayList<>(lines);
            return;
        }

        // Older servers do not have TextDisplay. Keep the DecentHolograms fallback there.
        removeTextDisplays(plugin);
        try {
            eu.decentsoftware.holograms.api.holograms.Hologram hologram = DHAPI.getHologram(hologramName);
            if (hologram == null) {
                DHAPI.createHologram(hologramName, hologramLocation, lines);
            } else if (!lines.equals(lastHologramLines)) {
                DHAPI.setHologramLines(hologram, lines);
            }
            lastHologramLines = new ArrayList<>(lines);
        } catch (Throwable ignored) {
            // Retry on the next grave state transition if DecentHolograms is not ready yet.
        }
    }

    public void removeHologram() {
        Main plugin = Main.getInstance();
        if (plugin != null) removeTextDisplays(plugin);
        removeDecentHologram();
        lastHologramLines = Collections.emptyList();
    }

    private boolean updateTransparentTextDisplay(Main plugin, List<String> lines) {
        World world = hologramLocation.getWorld();
        if (world == null || !world.isChunkLoaded(
                hologramLocation.getBlockX() >> 4, hologramLocation.getBlockZ() >> 4)) return false;

        Entity display = findTextDisplay(world, getHologramMarkerKey(plugin));
        if (display == null) {
            EntityType textDisplayType;
            try {
                textDisplayType = EntityType.valueOf("TEXT_DISPLAY");
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            try {
                display = world.spawnEntity(hologramLocation, textDisplayType);
            } catch (Throwable ignored) {
                return false;
            }
        } else if (display.getLocation().distanceSquared(hologramLocation) > 0.01) {
            display.teleport(hologramLocation);
        }

        try {
            display.setPersistent(false);
            display.getPersistentDataContainer().set(
                    getHologramMarkerKey(plugin), PersistentDataType.STRING, hologramName);
            configureTransparentTextDisplay(display, String.join("\n", lines));
            return true;
        } catch (Throwable ignored) {
            display.remove();
            return false;
        }
    }

    private Entity findTextDisplay(World world, NamespacedKey markerKey) {
        Entity found = null;
        for (Entity entity : world.getNearbyEntities(hologramLocation, 1.5, 2.5, 1.5)) {
            if (!"TEXT_DISPLAY".equals(entity.getType().name())) continue;
            String marker = entity.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
            if (!hologramName.equals(marker)) continue;
            if (found == null) {
                found = entity;
            } else {
                entity.remove();
            }
        }
        return found;
    }

    private void removeTextDisplays(Main plugin) {
        World world = hologramLocation.getWorld();
        if (world == null || hologramMarkerKey == null) return;
        int chunkX = hologramLocation.getBlockX() >> 4;
        int chunkZ = hologramLocation.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;

        for (Entity entity : world.getNearbyEntities(hologramLocation, 1.5, 2.5, 1.5)) {
            String marker = entity.getPersistentDataContainer()
                    .get(getHologramMarkerKey(plugin), PersistentDataType.STRING);
            if (hologramName.equals(marker)) entity.remove();
        }
    }

    private NamespacedKey getHologramMarkerKey(Main plugin) {
        if (hologramMarkerKey == null) {
            hologramMarkerKey = new NamespacedKey(plugin, "grave_hologram");
        }
        return hologramMarkerKey;
    }

    private void configureTransparentTextDisplay(Entity entity, String text) throws ReflectiveOperationException {
        Class<?> displayApi = Class.forName(DISPLAY_API);
        Class<?> textDisplayApi = Class.forName(TEXT_DISPLAY_API);
        Class<?> billboardType = Class.forName(DISPLAY_API + "$Billboard");
        Class<?> alignmentType = Class.forName(TEXT_DISPLAY_API + "$TextAlignment");

        invoke(displayApi, entity, "setBillboard", new Class<?>[]{billboardType}, enumValue(billboardType, "CENTER"));
        invoke(textDisplayApi, entity, "setAlignment", new Class<?>[]{alignmentType}, enumValue(alignmentType, "CENTER"));
        invoke(textDisplayApi, entity, "setDefaultBackground", new Class<?>[]{boolean.class}, false);
        setTransparentBackground(entity, textDisplayApi);
        invoke(textDisplayApi, entity, "setShadowed", new Class<?>[]{boolean.class}, true);
        invoke(textDisplayApi, entity, "setSeeThrough", new Class<?>[]{boolean.class}, false);
        invoke(textDisplayApi, entity, "setLineWidth", new Class<?>[]{int.class}, 240);
        // setViewRange is a multiplier: 0.09375 gives about six blocks on the default 64-block tracking range.
        invoke(displayApi, entity, "setViewRange", new Class<?>[]{float.class}, 0.09375f);
        invoke(textDisplayApi, entity, "setText", new Class<?>[]{String.class}, text);
    }

    private void setTransparentBackground(Entity entity, Class<?> textDisplayApi) throws ReflectiveOperationException {
        Class<?> colorApi = Class.forName("org.bukkit.Color");
        try {
            Object transparent = colorApi.getMethod("fromARGB", int.class, int.class, int.class, int.class)
                    .invoke(null, 0, 0, 0, 0);
            invoke(textDisplayApi, entity, "setBackgroundColor", new Class<?>[]{colorApi}, transparent);
        } catch (NoSuchMethodException ignored) {
            // setDefaultBackground(false) above still ensures no opaque default background.
        }
    }

    private static void invoke(Class<?> api, Entity target, String method, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        api.getMethod(method, parameterTypes).invoke(target, args);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private void removeDecentHologram() {
        try {
            if (DHAPI.getHologram(hologramName) != null) {
                DHAPI.removeHologram(hologramName);
            }
        } catch (Throwable ignored) {
        }
    }

    private List<String> buildHoloLines() {
        if (!dug) {
            return Arrays.asList(
                    ColorUtil.colorize("&#F8BEFB&lНЕРАСКОПАННАЯ МОГИЛА"),
                    ColorUtil.colorize("&7ЛКМ лопатой: &f" + digProgress + "/" + REQUIRED_DIG_CYCLES + " циклов"),
                    ColorUtil.colorize("&7ПКМ не засчитывается • внутри может быть сокровище")
            );
        }
        return Arrays.asList(
                ColorUtil.colorize("&#96FF91&lМОГИЛА РАСКОПАНА"),
                ColorUtil.colorize("&7ПКМ по сундуку под землёй — забрать лут")
        );
    }
}
