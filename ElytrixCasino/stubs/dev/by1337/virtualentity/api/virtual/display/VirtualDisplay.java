package dev.by1337.virtualentity.api.virtual.display;

import dev.by1337.virtualentity.api.entity.BillboardConstraints;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import org.bukkit.Color;

public interface VirtualDisplay extends VirtualEntity {
    void setPosRotInterpolationDuration(int i);
    int getPosRotInterpolationDuration();
    void setInterpolationDuration(int i);
    void setTransformationInterpolationDuration(int i);
    void setInterpolationDelay(int i);
    void setTransformationInterpolationDelay(int i);
    void setBillboardConstraints(BillboardConstraints billboardConstraints);
    void setBrightnessOverride(int block, int sky);
    void setViewRange(float f);
    void setShadowRadius(float f);
    void setShadowStrength(float f);
    void setWidth(float f);
    void setHeight(float f);
    void setGlowColorOverride(Color color, int alpha);
}
