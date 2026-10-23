package org.bukkit.attribute;
public interface AttributeInstance {
    double getValue();
    double getBaseValue();
    void setBaseValue(double value);
    void addModifier(AttributeModifier modifier);
    void removeModifier(AttributeModifier modifier);
    java.util.Collection<AttributeModifier> getModifiers();
}
