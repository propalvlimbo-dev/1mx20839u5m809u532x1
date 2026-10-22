package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public class EntityDamageEvent extends Event implements Cancellable {
    public enum DamageCause {
        FALL, ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, MAGIC, VOID, FIRE,
        FIRE_TICK, LAVA, DROWNING, STARVATION, SUFFOCATION, CONTACT, CRAMMING,
        CUSTOM, WORLD_BORDER, FLY_INTO_WALL, HOT_FLOOR, FREEZE
    }

    private boolean cancelled;

    public Entity getEntity() { return null; }

    public DamageCause getCause() { return DamageCause.CUSTOM; }

    public double getDamage() { return 0.0; }

    public double getFinalDamage() { return 0.0; }

    public void setDamage(double damage) { }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
