package org.bukkit.scoreboard;

public interface Scoreboard {
    Team getTeam(String teamName);
    Team registerNewTeam(String name);
}
