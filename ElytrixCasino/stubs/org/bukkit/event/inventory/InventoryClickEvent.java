package org.bukkit.event.inventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class InventoryClickEvent extends InventoryInteractEvent {
    public InventoryClickEvent(Inventory inventory) { super(inventory); }
    public Inventory getClickedInventory() { return null; }
    public int getSlot() { return 0; }
    public int getRawSlot() { return 0; }
    public HumanEntity getWhoClicked() { return null; }
    public InventoryAction getAction() { return null; }
    public ItemStack getCurrentItem() { return null; }
    public ItemStack getCursor() { return null; }
    public void setCursor(ItemStack item) { }
    public boolean isShiftClick() { return false; }
    public boolean isLeftClick() { return false; }
    public boolean isRightClick() { return false; }
    public void setResult(org.bukkit.event.Event.Result newResult) {}
}
