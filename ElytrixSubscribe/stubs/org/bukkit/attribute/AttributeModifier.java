package org.bukkit.attribute;
public class AttributeModifier {
    public enum Operation { ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1 }
    public AttributeModifier(org.bukkit.NamespacedKey key, double amount, Operation operation) { }
    public AttributeModifier(java.util.UUID uuid, String name, double amount, Operation operation) { }
    public org.bukkit.NamespacedKey getKey() { return null; }
    public double getAmount() { return 0; }
    public Operation getOperation() { return Operation.ADD_NUMBER; }
}
