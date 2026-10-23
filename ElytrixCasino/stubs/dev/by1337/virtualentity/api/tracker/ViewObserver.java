package dev.by1337.virtualentity.api.tracker;

import org.bukkit.entity.Player;

public interface ViewObserver {
    void onStartViewing(Player player);
    void onStopViewing(Player player);
}
