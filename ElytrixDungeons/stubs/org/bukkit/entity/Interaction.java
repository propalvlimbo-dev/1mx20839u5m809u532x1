package org.bukkit.entity;

/** Слепок org.bukkit.entity.Interaction (1.19.4+): невидимый хитбокс для кликов. */
public interface Interaction extends Entity {
    void setInteractionWidth(float width);
    float getInteractionWidth();
    void setInteractionHeight(float height);
    float getInteractionHeight();
    void setResponsive(boolean responsive);
    boolean isResponsive();
    void setResponse(boolean response);
    String getLastInteraction();
}
