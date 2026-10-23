package com.sk89q.worldguard.protection.regions;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.flags.StateFlag;
public class RegionQuery {
    public boolean testState(Location location, LocalPlayer player, StateFlag... flags) { return true; }
    public boolean testState(Location location, RegionAssociable associable, StateFlag... flags) { return true; }
    public boolean testBuild(Location location, LocalPlayer player, StateFlag... flags) { return true; }
    public boolean testBuild(Location location, RegionAssociable associable, StateFlag... flags) { return true; }
}
