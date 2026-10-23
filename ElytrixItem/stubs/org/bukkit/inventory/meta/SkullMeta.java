package org.bukkit.inventory.meta;

import org.bukkit.OfflinePlayer;
import org.bukkit.profile.PlayerProfile;

public interface SkullMeta extends ItemMeta {
    boolean setOwningPlayer(OfflinePlayer owner);
    PlayerProfile getOwnerProfile();
    void setOwnerProfile(PlayerProfile profile);
}
