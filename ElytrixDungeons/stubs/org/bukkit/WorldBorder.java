package org.bukkit;

public interface WorldBorder {
    double getSize();
    void setSize(double newSize);
    boolean isInside(Location location);
}
