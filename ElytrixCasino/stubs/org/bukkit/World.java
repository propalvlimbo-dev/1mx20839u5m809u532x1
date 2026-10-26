package org.bukkit;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Player;
import java.util.List;

public interface World {
    String getName();
    UUID getUID();
    Location getSpawnLocation();
    boolean setSpawnLocation(Location location);
    List<Player> getPlayers();
    <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> type);
    org.bukkit.block.Block getBlockAt(int x, int y, int z);
    org.bukkit.block.Block getBlockAt(Location location);
    int getMaxHeight();
    int getMinHeight();
    boolean isChunkLoaded(int x, int z);
    org.bukkit.entity.Item dropItemNaturally(Location location, org.bukkit.inventory.ItemStack item);
    org.bukkit.entity.Item dropItem(Location location, org.bukkit.inventory.ItemStack item);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void playSound(Location location, String sound, float volume, float pitch);
    void spawnParticle(org.bukkit.Particle particle, Location location, int count);
    <T> void spawnParticle(org.bukkit.Particle particle, Location location, int count, T data);
    <T> void spawnParticle(org.bukkit.Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data);
    void spawnParticle(org.bukkit.Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ);
    void spawnParticle(org.bukkit.Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra);
    <T> void spawnParticle(org.bukkit.Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data);
    <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data);
    <T> void spawnParticle(org.bukkit.Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force);
    Collection<org.bukkit.entity.Entity> getNearbyEntities(Location location, double x, double y, double z);
    List<org.bukkit.entity.Entity> getEntities();
    List<org.bukkit.entity.LivingEntity> getLivingEntities();
    org.bukkit.block.data.BlockData createBlockData(Material material);
    boolean createExplosion(Location location, float power, boolean setFire, boolean breakBlocks);
    boolean createExplosion(double x, double y, double z, float power, boolean setFire);
    org.bukkit.entity.LightningStrike strikeLightning(Location location);
    int getHighestBlockYAt(Location location);
    int getHighestBlockYAt(int x, int z);
    long getTime();
    void setTime(long time);
    boolean isDayTime();
    org.bukkit.entity.Entity getEntity(java.util.UUID uuid);
    long getSeed();
    Difficulty getDifficulty();
    void setDifficulty(Difficulty difficulty);
    java.io.File getWorldFolder();
    Environment getEnvironment();

    enum Environment { NORMAL, NETHER, THE_END, CUSTOM }
    org.bukkit.entity.Entity spawnEntity(Location loc, org.bukkit.entity.EntityType type);
}
