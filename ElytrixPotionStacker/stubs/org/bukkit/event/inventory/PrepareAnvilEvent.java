package org.bukkit.event.inventory;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
public class PrepareAnvilEvent extends InventoryEvent {
    public PrepareAnvilEvent(AnvilInventory inventory) { super(inventory); }
    public AnvilInventory getInventory() { return null; }
    public ItemStack getResult() { return null; }
    public void setResult(ItemStack result) { }
}
