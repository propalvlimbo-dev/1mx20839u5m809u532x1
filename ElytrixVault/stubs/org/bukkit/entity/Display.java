package org.bukkit.entity;

import org.bukkit.Color;
import org.bukkit.util.Transformation;

public interface Display extends Entity {
    Transformation getTransformation();
    void setTransformation(Transformation transformation);
    int getInterpolationDuration();
    void setInterpolationDuration(int duration);
    int getTeleportDuration();
    void setTeleportDuration(int duration);
    float getViewRange();
    void setViewRange(float range);
    float getShadowRadius();
    void setShadowRadius(float radius);
    float getShadowStrength();
    void setShadowStrength(float strength);
    float getDisplayWidth();
    void setDisplayWidth(float width);
    float getDisplayHeight();
    void setDisplayHeight(float height);
    int getInterpolationDelay();
    void setInterpolationDelay(int ticks);
    Billboard getBillboard();
    void setBillboard(Billboard billboard);
    Color getGlowColorOverride();
    void setGlowColorOverride(Color color);
    Brightness getBrightness();
    void setBrightness(Brightness brightness);

    enum Billboard {
        FIXED,
        VERTICAL,
        HORIZONTAL,
        CENTER
    }

    class Brightness {
        public Brightness(int blockLight, int skyLight) {}
        public int getBlockLight() { return 0; }
        public int getSkyLight() { return 0; }
    }
}
