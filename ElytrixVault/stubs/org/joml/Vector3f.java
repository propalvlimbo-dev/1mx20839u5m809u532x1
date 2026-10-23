package org.joml;

public class Vector3f {
    public float x;
    public float y;
    public float z;
    public Vector3f() {}
    public Vector3f(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public Vector3f(Vector3f other) { this.x = other.x; this.y = other.y; this.z = other.z; }
}
