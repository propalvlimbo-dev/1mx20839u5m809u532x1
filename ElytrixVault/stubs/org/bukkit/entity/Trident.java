package org.bukkit.entity;
public interface Trident extends AbstractArrow {
    boolean hasGlint();
    void setGlint(boolean glint);
    int getLoyaltyLevel();
    void setLoyaltyLevel(int level);
}
