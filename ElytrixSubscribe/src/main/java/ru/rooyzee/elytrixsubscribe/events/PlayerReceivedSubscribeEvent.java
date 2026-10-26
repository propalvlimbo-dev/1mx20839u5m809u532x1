package ru.rooyzee.elytrixsubscribe.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import ru.rooyzee.elytrixsubscribe.events.state.ReceivedSubscribeState;

public class PlayerReceivedSubscribeEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();
    private final ReceivedSubscribeState state;
    private final long timeToEnd;

    public PlayerReceivedSubscribeEvent(Player player, long timeToEnd, ReceivedSubscribeState state) {
        super(player);
        this.state = state;
        this.timeToEnd = timeToEnd;
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    public ReceivedSubscribeState getState() {
        return this.state;
    }

    public long getTimeToEnd() {
        return this.timeToEnd;
    }
}