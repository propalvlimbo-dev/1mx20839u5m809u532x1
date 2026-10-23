package org.bukkit;
public class Location implements Cloneable {
    public org.bukkit.block.Block getBlock() { return null; }
    public org.bukkit.Chunk getChunk() { return null; }
    public Location subtract(org.bukkit.util.Vector vector) { return this; }
    public Location subtract(double x, double y, double z) { return this; }
    public Location add(Location other) { return this; }
    public Location multiply(double value) { return this; }
    public void setX(double x) {}
    public void setY(double y) {}
    public void setZ(double z) {}
    public double length() { return 0; }
    public boolean isWorldLoaded() { return true; }

    public Location(World world, double x, double y, double z) { }
    public Location(World world, double x, double y, double z, float yaw, float pitch) { }
    public World getWorld() { return null; }
    public void setWorld(World world) { }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public float getYaw() { return 0; }
    public float getPitch() { return 0; }
    public void setYaw(float yaw) { }
    public void setPitch(float pitch) { }
    public int getBlockX() { return 0; }
    public int getBlockY() { return 0; }
    public int getBlockZ() { return 0; }
    public Location clone() { return this; }
    public double distanceSquared(Location other) { return 0; }
    public double distance(Location other) { return 0; }
    public Location add(double x, double y, double z) { return this; }
    public Location add(org.bukkit.util.Vector vector) { return this; }
    public org.bukkit.util.Vector toVector() { return new org.bukkit.util.Vector(0, 0, 0); }
    public org.bukkit.util.Vector getDirection() { return new org.bukkit.util.Vector(0, 0, 0); }
    public void setDirection(org.bukkit.util.Vector vector) { }
}
