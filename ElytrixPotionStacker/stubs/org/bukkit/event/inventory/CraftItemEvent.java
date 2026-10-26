package org.bukkit.event.inventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class CraftItemEvent extends InventoryClickEvent {
    public CraftItemEvent(Inventory inventory) { super(inventory); }
    public org.bukkit.inventory.Recipe getRecipe() { return null; }
}
