package org.bukkit.attribute;
public interface Attributable {
    AttributeInstance getAttribute(Attribute attribute);
    void registerAttribute(Attribute attribute);
}
