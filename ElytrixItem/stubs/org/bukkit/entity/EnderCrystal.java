package org.bukkit.entity;
public interface EnderCrystal extends Entity {
    boolean isShowingBottom();
    void setShowingBottom(boolean showing);
    org.bukkit.Location getBeamTarget();
    void setBeamTarget(org.bukkit.Location location);
}
