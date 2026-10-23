package org.bukkit.block.data.type;

import org.bukkit.block.data.Waterlogged;

public interface SculkShrieker extends Waterlogged {
    boolean isCanSummon();
    void setCanSummon(boolean can_summon);
    boolean isShrieking();
    void setShrieking(boolean shrieking);
}
