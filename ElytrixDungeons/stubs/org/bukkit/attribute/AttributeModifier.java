package org.bukkit.attribute;
public class AttributeModifier {
    public enum Operation { ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1 }
    public AttributeModifier(org.bukkit.NamespacedKey key, double amount, Operation operation) { }
    public AttributeModifier(org.bukkit.NamespacedKey key, double amount, Operation operation, org.bukkit.inventory.EquipmentSlotGroup slotGroup) { }
    public AttributeModifier(java.util.UUID uuid, String name, double amount, Operation operation, org.bukkit.inventory.EquipmentSlot slot) { }
    public AttributeModifier(java.util.UUID uuid, String name, double amount, Operation operation) { }
    public org.bukkit.NamespacedKey getKey() { return null; }
    public double getAmount() { return 0; }
    public Operation getOperation() { return Operation.ADD_NUMBER; }
    public org.bukkit.inventory.EquipmentSlotGroup getSlotGroup() { return null; }
    public org.bukkit.inventory.EquipmentSlot getSlot() { return null; }
    public java.util.UUID getUniqueId() { return null; }
    public String getName() { return ""; }
}
