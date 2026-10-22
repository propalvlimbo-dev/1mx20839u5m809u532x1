package com.sk89q.worldguard.protection.managers;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
public interface RegionManager {
    java.util.Map<String, ProtectedRegion> getRegions();
    ProtectedRegion getRegion(String id);
}
