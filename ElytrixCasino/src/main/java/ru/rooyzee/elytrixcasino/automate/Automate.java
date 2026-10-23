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
 * Автомат: блок-низ хранится как координата, а над крышкой висит 3-строчная голограмма
 * в фирменном стиле dHolograms ElytriX с живой переливающейся волной WAVE.
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
    private ArmorStand waveStand;

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

    public boolean isHologramShown() {
        return hologramShown && !holograms.isEmpty();
    }

    public void refreshHologram() {
        if (isChunkLoaded()) {
            spawnHologram();
        }
    }

    public void hideHologram() {
        if (isChunkLoaded()) {
            removeHologram();
        } else {
            holograms.clear();
            waveStand = null;
            hologramShown = false;
        }
    }

    public void spawnHologram() {
        removeHologram();
        if (!isChunkLoaded()) {
            return;
        }
        Location base = bottom.clone().add(0.5D, 2.10D, 0.5D);
        ArmorStand title = line(base.clone().add(0.0D, 0.50D, 0.0D),
                Text.color("<gradient:#F8BEFB:#89A0FF><bold>АВТОМАТ</bold></gradient>"));
        ArmorStand wave = line(base.clone().add(0.0D, 0.25D, 0.0D),
                Text.waveBar(0));
        ArmorStand amount = line(base,
                Text.color("&7● &fСтавка: &#F8BEFB" + Text.formatMoney(bet) + " ⛂"));
        if (title != null) {
            holograms.add(title);
        }
        if (wave != null) {
            holograms.add(wave);
            this.waveStand = wave;
        }
        if (amount != null) {
            holograms.add(amount);
        }
        hologramShown = !holograms.isEmpty();
    }

    public void updateWave(int waveStep) {
        if (!hologramShown || waveStand == null || waveStand.isDead()) {
            return;
        }
        waveStand.setCustomName(Text.waveBar(waveStep));
    }

    private ArmorStand line(Location location, String coloredText) {
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
        stand.setCustomName(coloredText);
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
        waveStand = null;
        hologramShown = false;

        if (!isChunkLoaded()) {
            return;
        }
        Location center = bottom.clone().add(0.5D, 2.35D, 0.5D);
        for (Entity nearby : nearby(center, 1.2D, 1.6D, 1.2D)) {
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

    public String getId() { return id; }
    public Location getLocation() { return bottom.clone(); }
    public long getBet() { return bet; }
    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }
}
