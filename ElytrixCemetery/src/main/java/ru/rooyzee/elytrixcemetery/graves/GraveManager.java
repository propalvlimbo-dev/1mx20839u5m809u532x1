package ru.rooyzee.elytrixcemetery.graves;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;
import ru.rooyzee.elytrixcemetery.utils.ParticleUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GraveManager {

    private final Main plugin;
    private final Map<String, Grave> gravesByDig = new ConcurrentHashMap<>();
    private final Map<String, Grave> gravesByChest = new ConcurrentHashMap<>();
    private final Map<String, List<Grave>> gravesByColumn = new ConcurrentHashMap<>();
    private final Map<String, List<Grave>> gravesByHologramChunk = new ConcurrentHashMap<>();
    private long nextRefillTime;
    private long nextHologramRefreshTime;

    public GraveManager(Main plugin) {
        this.plugin = plugin;
        this.nextRefillTime = System.currentTimeMillis() + plugin.getConfigManager().getRefillIntervalSeconds() * 1000L;
    }

    public void registerGraves(List<Grave> graveList) {
        clearAll();
        for (Grave grave : graveList) {
            gravesByDig.put(locKey(grave.getDigLocation()), grave);
            gravesByChest.put(locKey(grave.getChestLocation()), grave);
            gravesByColumn.computeIfAbsent(columnKey(grave.getDigLocation()), key -> new ArrayList<>()).add(grave);
            gravesByHologramChunk.computeIfAbsent(chunkKey(grave.getHologramLocation()), key -> new ArrayList<>())
                    .add(grave);
            grave.updateHologram(plugin);
        }
    }

    /** True for any block vertically above a grave's dig block. */
    public boolean isAboveGraveColumn(Location location) {
        if (location == null || location.getWorld() == null) return false;
        List<Grave> candidates = gravesByColumn.get(columnKey(location));
        if (candidates == null) return false;
        int blockY = location.getBlockY();
        for (Grave grave : candidates) {
            if (blockY > grave.getDigLocation().getBlockY()) return true;
        }
        return false;
    }

    public Grave getGraveAt(Location loc) {
        if (loc == null) return null;
        return gravesByDig.get(locKey(loc));
    }

    public Grave getGraveByChest(Location loc) {
        if (loc == null) return null;
        return gravesByChest.get(locKey(loc));
    }

    public Collection<Grave> getAllGraves() {
        return gravesByDig.values();
    }

    public int getDugCount() {
        int count = 0;
        for (Grave g : gravesByDig.values()) {
            if (g.isDug()) count++;
        }
        return count;
    }

    public int getTotalCount() {
        return gravesByDig.size();
    }

    public long getRemainingRefillSeconds() {
        return Math.max(0, (nextRefillTime - System.currentTimeMillis()) / 1000L);
    }

    public String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextRefillTime) {
            refillAndBuryAll();
            nextRefillTime = now + plugin.getConfigManager().getRefillIntervalSeconds() * 1000L;
        }
        if (now >= nextHologramRefreshTime) {
            refreshHolograms();
        }
    }

    public void refreshHolograms() {
        for (Grave grave : gravesByDig.values()) {
            if (isHologramChunkLoaded(grave)) grave.updateHologram(plugin);
        }
        nextHologramRefreshTime = System.currentTimeMillis()
                + plugin.getConfigManager().getHologramRefreshIntervalSeconds() * 1000L;
    }

    public void refreshHologramsInChunk(Chunk chunk) {
        if (chunk == null) return;
        String key = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        List<Grave> graves = gravesByHologramChunk.get(key);
        if (graves == null) return;
        for (Grave grave : graves) grave.updateHologram(plugin);
    }

    private boolean isHologramChunkLoaded(Grave grave) {
        Location location = grave.getHologramLocation();
        return location.getWorld() != null
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public void refillAndBuryAll() {
        if (gravesByDig.isEmpty()) return;

        int buried = 0;
        for (Grave g : gravesByDig.values()) {
            if (g.isDug()) {
                g.bury(plugin);
                buried++;
            }
        }

        Location center = plugin.getConfigManager().getCenterLocation();
        World world = center != null ? center.getWorld() : null;

        if (world != null) {
            world.playSound(center, Sound.BLOCK_BELL_RESONATE, 2.0f, 0.8f);
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
            ParticleUtil.spawn(world, ParticleUtil.SOUL, center.clone().add(0, 2, 0), 50, 15, 2, 15, 0.05);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &#F8BEFBМогилы на кладбище закопаны! Лут обновлён."));
            }
        }

        plugin.getLogger().info("Могилы на кладбище обновлены и закопаны (" + buried + " раскопанных).");
    }

    public void clearAll() {
        for (Grave grave : gravesByDig.values()) {
            grave.removeHologram();
        }
        gravesByDig.clear();
        gravesByChest.clear();
        gravesByColumn.clear();
        gravesByHologramChunk.clear();
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private String columnKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockZ();
    }

    private String chunkKey(Location loc) {
        return loc.getWorld().getName() + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }
}
