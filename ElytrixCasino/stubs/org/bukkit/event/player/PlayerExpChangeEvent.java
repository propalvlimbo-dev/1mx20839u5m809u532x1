package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
public class PlayerExpChangeEvent extends PlayerEvent {
    public void setAmount(int amount) { }

    public PlayerExpChangeEvent(Player player) { super(player); }
    public PlayerExpChangeEvent(Player player, ItemStack item) { super(player); }
    public PlayerExpChangeEvent(Player player, int previous, int current) { super(player); }
    public ItemStack getItem() { return null; }
    public int getAmount() { return 0; }
    public int getNewSlot() { return 0; }
    public int getPreviousSlot() { return 0; }
}
