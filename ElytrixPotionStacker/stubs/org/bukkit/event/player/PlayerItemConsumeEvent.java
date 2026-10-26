package org.bukkit.event.player;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
public class PlayerItemConsumeEvent extends PlayerEvent {
    public PlayerItemConsumeEvent(Player player) { super(player); }
    public PlayerItemConsumeEvent(Player player, ItemStack item) { super(player); }
    public PlayerItemConsumeEvent(Player player, int previous, int current) { super(player); }
    public ItemStack getItem() { return null; }
    public int getAmount() { return 0; }
    public int getNewSlot() { return 0; }
    public int getPreviousSlot() { return 0; }
}
