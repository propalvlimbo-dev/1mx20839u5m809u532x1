package dev.by1337.virtualentity.api.virtual.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;

public interface VirtualTextDisplay extends VirtualDisplay {
    Component getText();
    void setText(Component component);
    int getLineWidth();
    void setLineWidth(int width);
    byte getTextOpacity();
    void setTextOpacity(byte textOpacity);
    Color getBackgroundColor();
    void setBackgroundColor(Color color, int alpha);
    boolean isShadow();
    void setShadow(boolean shadow);
    boolean isSeeThrough();
    void setSeeThrough(boolean seeThrough);
    boolean isDefaultBackground();
    void setDefaultBackground(boolean defaultBackground);
    boolean isAlignLeft();
    void setAlignLeft(boolean alignLeft);
    boolean isAlignRight();
    void setAlignRight(boolean alignRight);
    static VirtualTextDisplay create() { return null; }
}
