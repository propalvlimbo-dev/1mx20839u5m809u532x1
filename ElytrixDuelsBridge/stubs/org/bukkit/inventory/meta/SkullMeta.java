package org.bukkit.inventory.meta;
import org.bukkit.OfflinePlayer;
public interface SkullMeta extends ItemMeta {
    boolean setOwningPlayer(OfflinePlayer owner);
}
