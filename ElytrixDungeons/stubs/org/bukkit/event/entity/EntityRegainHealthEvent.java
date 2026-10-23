package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class EntityRegainHealthEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private double amount;
    private final RegainReason regainReason;
    private final boolean isFastRegen;
    private boolean cancelled;

    public EntityRegainHealthEvent(Entity entity, double amount, RegainReason regainReason) {
        this(entity, amount, regainReason, false);
    }

    public EntityRegainHealthEvent(Entity entity, double amount, RegainReason regainReason, boolean isFastRegen) {
        super(entity);
        this.amount = amount;
        this.regainReason = regainReason;
        this.isFastRegen = isFastRegen;
    }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public RegainReason getRegainReason() { return regainReason; }
    public boolean isFastRegen() { return isFastRegen; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    public HandlerList getHandlers() { return HANDLER_LIST; }
    public static HandlerList getHandlerList() { return HANDLER_LIST; }

    public enum RegainReason {
        REGEN, SATIATED, EATING, ENDER_CRYSTAL, MAGIC, MAGIC_REGEN, WITHER_SPAWN, WITHER, CUSTOM
    }
}
