package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldguard.protection.flags.Flag;
public interface RegionQuery {
    boolean testState(Object location, Object entity, Flag<?>... flags);
    <T> T queryValue(Object location, Object entity, Flag<T> flag);
}
