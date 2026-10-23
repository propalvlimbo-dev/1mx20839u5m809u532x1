package com.sk89q.worldguard.protection.regions;
public abstract class RegionContainer {
    public abstract RegionQuery createQuery();
    public abstract com.sk89q.worldguard.protection.managers.RegionManager get(com.sk89q.worldedit.world.World world);
}
