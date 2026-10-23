package ru.rooyzee.elytrixcasino.automate;

import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Автомат: блок-низ хранится как координата, а вывеска — прозрачный TextDisplay
 * без тёмной подложки и с тенью букв (billboard=CENTER, backgroundColor=0, shadow=true).
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
    private TextDisplay hologram;

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
        return hologramShown && hologram != null && !hologram.isDead();
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
            hologram = null;
            hologramShown = false;
        }
    }

    public void spawnHologram() {
        removeHologram();
        if (!isChunkLoaded()) {
            return;
        }
        World world = bottom.getWorld();
        if (world == null) {
            return;
        }
        Location loc = bottom.clone().add(0.5D, 2.25D, 0.5D);
        TextDisplay display;
        try {
            display = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось создать голограмму автомата '" + id + "': " + t);
            return;
        }
        if (display == null || !display.isValid()) {
            removeQuietly(display);
            return;
        }
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(true);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setLineWidth(400);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
        display.setText(Text.color("&#F8BEFB&lАВТОМАТ\n&#F8BEFBСТАВКА: &f" + bet));
        display.addScoreboardTag(HOLO_TAG);
        display.addScoreboardTag(ID_TAG_PREFIX + id);

        this.hologram = display;
        this.hologramShown = true;
    }

    public void removeHologram() {
        removeQuietly(hologram);
        hologram = null;
        hologramShown = false;

        if (!isChunkLoaded()) {
            return;
        }
        Location center = bottom.clone().add(0.5D, 2.25D, 0.5D);
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
        if (!(entity instanceof TextDisplay) && !(entity instanceof ArmorStand)) {
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
