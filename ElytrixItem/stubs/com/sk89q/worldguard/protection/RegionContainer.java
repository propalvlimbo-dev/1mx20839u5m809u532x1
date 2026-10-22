package com.sk89q.worldguard.protection;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionQuery;
public interface RegionContainer {
    RegionQuery createQuery();
    RegionManager get(com.sk89q.worldedit.world.World world);
}
