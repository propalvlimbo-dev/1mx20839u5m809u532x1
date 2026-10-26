package org.bukkit.entity;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;

public interface TextDisplay extends Display {
    String getText();
    void setText(String text);
    Component text();
    void text(Component text);
    int getLineWidth();
    void setLineWidth(int width);
    Color getBackgroundColor();
    void setBackgroundColor(Color color);
    byte getTextOpacity();
    void setTextOpacity(byte opacity);
    boolean isShadowed();
    void setShadowed(boolean shadow);
    boolean isSeeThrough();
    void setSeeThrough(boolean seeThrough);
    boolean isDefaultBackground();
    void setDefaultBackground(boolean defaultBackground);
    TextAlignment getAlignment();
    void setAlignment(TextAlignment alignment);

    enum TextAlignment {
        CENTER,
        LEFT,
        RIGHT
    }
}
