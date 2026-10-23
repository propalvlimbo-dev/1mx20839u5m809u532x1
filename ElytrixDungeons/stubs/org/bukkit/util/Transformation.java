package org.bukkit.util;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Transformation {
    public Transformation(Vector3f translation, AxisAngle4f leftRotation, Vector3f scale, AxisAngle4f rightRotation) {}
    public Transformation(Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {}
    public Vector3f getTranslation() { return null; }
    public Quaternionf getLeftRotation() { return null; }
    public Vector3f getScale() { return null; }
    public Quaternionf getRightRotation() { return null; }
}
