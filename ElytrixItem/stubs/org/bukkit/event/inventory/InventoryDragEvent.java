package org.bukkit.event.inventory;
public class InventoryDragEvent extends InventoryInteractEvent {
    public InventoryDragEvent(org.bukkit.inventory.Inventory inventory) { super(inventory); }
    public java.util.Map<Integer, org.bukkit.inventory.ItemStack> getNewItems() { return new java.util.HashMap<>(); }
    public java.util.Set<Integer> getRawSlots() { return new java.util.HashSet<>(); }
    public java.util.Set<Integer> getInventorySlots() { return new java.util.HashSet<>(); }
    public org.bukkit.inventory.ItemStack getOldCursor() { return null; }
    public org.bukkit.inventory.ItemStack getCursor() { return null; }
    public org.bukkit.event.inventory.DragType getType() { return null; }
}
