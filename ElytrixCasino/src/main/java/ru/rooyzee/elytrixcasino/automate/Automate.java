package ru.rooyzee.elytrixcasino.automate;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Автомат: сам блок-низ хранится как координата, а «вывеска» — настоящие армор-стенды.
 * <p>
 * Армор-стенды живут в чанке, поэтому показываем их только когда чанк загружен:
 * на старте сервера чанки ещё спят, и сущность, созданная в невыгруженном чанке,
 * молча пропадала (автомат «исчезал» после рестарта, хотя запись в data.yml была).
 * Голограмма поднимается заново на {@code ChunkLoadEvent} и убирается на
 * {@code ChunkUnloadEvent}, чтобы стенды не записывались в сейв мира и не дублировались.
 */
public class Automate {

    private static final String HOLO_TAG = "elytrixcasino_holo";
    private static final String ID_TAG_PREFIX = "elytrixcasino_";

    private final Main plugin;
    private final String id;
    private final Location bottom;
    private final long bet;
    private boolean busy;
    private boolean hologramShown;
    private final List<ArmorStand> holograms = new ArrayList<>();

    public Automate(Main plugin, String id, Location bottom, long bet) {
        this.plugin = plugin;
        this.id = id;
        this.bottom = bottom.getBlock().getLocation();
        this.bet = Math.max(1L, bet);
    }

    public boolean isValid() {
        World world = bottom.getWorld();
        return world != null;
    }

    public boolean isInChunk(Chunk chunk) {
        World world = bottom.getWorld();
        if (chunk == null || world == null || !chunk.getWorld().equals(world)) {
            return false;
        }
        return chunk.getX() == (bottom.getBlockX() >> 4) && chunk.getZ() == (bottom.getBlockZ() >> 4);
    }

    public boolean isChunkLoaded() {
        World world = bottom.getWorld();
        return world != null && world.isChunkLoaded(bottom.getBlockX() >> 4, bottom.getBlockZ() >> 4);
    }

    /**
     * Видна ли сейчас вывеска автомата.
     */
    public boolean isHologramShown() {
        return hologramShown && !holograms.isEmpty();
    }

    /**
     * Поднять вывеску, если чанк уже загружен. Иначе она поднимется вместе с чанком.
     */
    public void refreshHologram() {
        if (isChunkLoaded()) {
            spawnHologram();
        }
    }

    /**
     * Снять вывеску (чанк выгружается / плагин выключается).
     */
    public void hideHologram() {
        if (isChunkLoaded()) {
            removeHologram();
        } else {
            holograms.clear();
            hologramShown = false;
        }
    }

    public void spawnHologram() {
        removeHologram();
        if (!isChunkLoaded()) {
            return;
        }
        Location base = bottom.clone().add(0.5D, 2.15D, 0.5D);
        ArmorStand title = line(base.clone().add(0.0D, 0.28D, 0.0D), "&#F8BEFB&lАВТОМАТ");
        ArmorStand amount = line(base, "&#F8BEFBСТАВКА: &f" + bet);
        if (title != null) {
            holograms.add(title);
        }
        if (amount != null) {
            holograms.add(amount);
        }
        hologramShown = !holograms.isEmpty();
    }

    private ArmorStand line(Location location, String text) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        ArmorStand stand;
        try {
            stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось создать голограмму автомата '" + id + "': " + t);
            return null;
        }
        if (stand == null) {
            return null;
        }
        if (!stand.isValid()) {
            // чанк успел выгрузиться прямо во время спавна
            removeQuietly(stand);
            return null;
        }
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setCustomName(Text.color(text));
        stand.setCustomNameVisible(true);
        stand.addScoreboardTag(HOLO_TAG);
        stand.addScoreboardTag(ID_TAG_PREFIX + id);
        return stand;
    }

    public void removeHologram() {
        for (ArmorStand stand : holograms) {
            removeQuietly(stand);
        }
        holograms.clear();
        hologramShown = false;

        if (!isChunkLoaded()) {
            return;
        }
        Location center = bottom.clone().add(0.5D, 2.2D, 0.5D);
        for (Entity nearby : nearby(center, 1.2D, 1.5D, 1.2D)) {
            if (isStaleHologram(nearby)) {
                removeQuietly(nearby);
            }
        }
    }

    private Collection<Entity> nearby(Location center, double x, double y, double z) {
        World world = center.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }
        try {
            return world.getNearbyEntities(center, x, y, z);
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    /**
     * Наша вывеска (или сирота от прошлых запусков), но НЕ вывеска соседнего автомата:
     * автоматы в казино стоят вплотную, и прежняя чистка «всех по общему тегу»
     * сносила надписи соседей при загрузке.
     */
    private boolean isStaleHologram(Entity entity) {
        if (!(entity instanceof ArmorStand)) {
            return false;
        }
        Set<String> tags = entity.getScoreboardTags();
        if (tags.contains(ID_TAG_PREFIX + id)) {
            return true;
        }
        if (!tags.contains(HOLO_TAG)) {
            return false;
        }
        for (String tag : tags) {
            if (!tag.startsWith(ID_TAG_PREFIX) || tag.equals(HOLO_TAG)) {
                continue;
            }
            Automate owner = plugin.getAutomateManager().get(tag.substring(ID_TAG_PREFIX.length()));
            if (owner != null && owner != this) {
                return false;
            }
        }
        return true;
    }

    private void removeQuietly(Entity entity) {
        if (entity == null || entity.isDead()) {
            return;
        }
        try {
            entity.remove();
        } catch (Throwable ignored) {
        }
    }

    public boolean isBlock(Location location) {
        if (location.getWorld() == null || bottom.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().equals(bottom.getWorld())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x == bottom.getBlockX()
                && z == bottom.getBlockZ()
                && (y == bottom.getBlockY() || y == bottom.getBlockY() + 1);
    }

    /**
     * Сторона, с которой к автомату вплотную примыкает указанный блок: «+1 блок вперёд»,
     * только по прямой (север/юг/запад/восток, без диагоналей) и на высоте самого автомата
     * (нижний и верхний блок). Возвращает направление ОТ автомата К блоку либо {@code null},
     * если блок не примыкает.
     * <p>
     * Специально считается на лету по координатам: направление автомата нигде не хранится,
     * поэтому свойство одинаково работает и у новых автоматов, и у всех уже созданных
     * (в data.yml для них ничего дописывать не нужно).
     */
    public BlockFace adjacentFace(Location location) {
        if (location == null || location.getWorld() == null || bottom.getWorld() == null) {
            return null;
        }
        if (!location.getWorld().equals(bottom.getWorld())) {
            return null;
        }
        int y = location.getBlockY();
        if (y != bottom.getBlockY() && y != bottom.getBlockY() + 1) {
            return null;
        }
        int dx = location.getBlockX() - bottom.getBlockX();
        int dz = location.getBlockZ() - bottom.getBlockZ();
        if (dx == 0 && dz == -1) {
            return BlockFace.NORTH;
        }
        if (dx == 0 && dz == 1) {
            return BlockFace.SOUTH;
        }
        if (dx == -1 && dz == 0) {
            return BlockFace.WEST;
        }
        if (dx == 1 && dz == 0) {
            return BlockFace.EAST;
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return bottom.clone();
    }

    public long getBet() {
        return bet;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }
}
