package org.by1337.blib.geom;
public class Vec3d {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);
    public double x, y, z;
    public Vec3d(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3d(org.bukkit.Location location) { }
    public double getX() { return this.x; }
    public double getY() { return this.y; }
    public double getZ() { return this.z; }
    public Vec3d add(Vec3d other) { return this; }
    public Vec3d subtract(Vec3d other) { return this; }
    public Vec3d multiply(double value) { return this; }
    public Vec3d normalize() { return this; }
    public double lengthSquared() { return 0; }
    public double length() { return 0; }
    public org.bukkit.Location toLocation(org.bukkit.World world) { return null; }
}
