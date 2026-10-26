package org.bukkit.util;
public class Vector implements Cloneable {
    public Vector(double x, double y, double z) { }
    public Vector() { }
    public double getX() { return 0; }
    public double getY() { return 0; }
    public double getZ() { return 0; }
    public Vector setX(double x) { return this; }
    public Vector setY(double y) { return this; }
    public Vector setZ(double z) { return this; }
    public Vector add(Vector other) { return this; }
    public Vector subtract(Vector other) { return this; }
    public Vector multiply(double value) { return this; }
    public Vector normalize() { return this; }
    public double length() { return 0; }
    public double lengthSquared() { return 0; }
    public double distanceSquared(Vector other) { return 0; }
    public Vector clone() { return this; }
    public static Vector getRandom() { return new Vector(0, 0, 0); }
    public org.bukkit.Location toLocation(org.bukkit.World world) { return null; }
}
