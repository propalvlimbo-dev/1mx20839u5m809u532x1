package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
public interface Player extends HumanEntity, OfflinePlayer, CommandSender {
    void updateInventory();
    void damage(double amount);
    void damage(double amount, org.bukkit.entity.Entity source);
    org.bukkit.GameMode getGameMode();
    void setGameMode(org.bukkit.GameMode mode);
    boolean isGliding();
    void setGliding(boolean gliding);
    boolean isSneaking();
    void setSneaking(boolean sneaking);
    void setSprinting(boolean sprinting);
    boolean isSprinting();
    org.bukkit.potion.PotionEffect getPotionEffect(org.bukkit.potion.PotionEffectType type);
    boolean hasCooldown(org.bukkit.Material material);
    int getCooldown(org.bukkit.Material material);
    void setCooldown(org.bukkit.Material material, int ticks);
    org.bukkit.util.Vector getVelocity();
    void setVelocity(org.bukkit.util.Vector velocity);
    float getFallDistance();
    void setFallDistance(float distance);
    Player.Spigot spigot();
    int getLevel();
    void setLevel(int level);
    int getExp();
    void setExp(float exp);
    void giveExp(int amount);
    int getTotalExperience();
    void setTotalExperience(int exp);
    String getDisplayName();
    void setDisplayName(String name);
    void sendMessage(String message);
    void sendMessage(String[] messages);
    Player getPlayer();
    org.bukkit.inventory.Inventory getEnderChest();

    class Spigot extends org.bukkit.command.CommandSender.Spigot {
        public void sendMessage(net.md_5.bungee.api.ChatMessageType type, net.md_5.bungee.api.chat.BaseComponent... components) { }
    }

    boolean performCommand(String command);
    void closeInventory();
    boolean isOnline();
    boolean hasPermission(String name);
    boolean isOp();
    Location getLocation();
    World getWorld();
    boolean teleport(Location location);
    void sendActionBar(String message);
    boolean addPotionEffect(org.bukkit.potion.PotionEffect effect);
    java.util.Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects();
    double getHealth();
    void setHealth(double health);
    int getFoodLevel();
    void setFoodLevel(int food);
    AttributeInstance getAttribute(Attribute attribute);
    void playSound(Location location, Sound sound, float volume, float pitch);
    void playSound(Location location, Sound sound, org.bukkit.SoundCategory category, float volume, float pitch);
    void stopSound(Sound sound);
    void stopSound(Sound sound, org.bukkit.SoundCategory category);
    void setNoGravity(boolean gravity);
    float getPitch();
    Location getEyeLocation();
    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);
    void openSign(org.bukkit.block.Sign sign);
    float getYaw();
    org.bukkit.inventory.ItemStack getItemOnCursor();
    void setItemOnCursor(org.bukkit.inventory.ItemStack item);
    void playSound(org.bukkit.Location loc, String sound, float volume, float pitch);
}
