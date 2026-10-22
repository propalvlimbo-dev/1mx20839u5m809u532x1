package ru.rooyzee.elytrixsubscribe.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerGoneSubscribeEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    public PlayerGoneSubscribeEvent(Player player) {
        super(player);
    }

    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}