package ru.rooyzee.elytrixsubscribe.service;

import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;

public interface ISubService {
    void remSub(Player player);

    void insertSub(Player player, long time, Consumer<Boolean> callback);

    void updatePlayer(Player player, long time, Consumer<Boolean> callback);

    void setSub(Player player, long time);

    boolean hasSub(UUID uuid);

    long getTimeToEnd(UUID uuid);
}
