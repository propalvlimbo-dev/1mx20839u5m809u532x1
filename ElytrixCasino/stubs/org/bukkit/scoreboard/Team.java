package org.bukkit.scoreboard;

public interface Team {
    void addEntry(String entry);
    boolean hasEntry(String entry);
    void setOption(Option option, OptionStatus status);

    enum Option {
        NAME_TAG_VISIBILITY,
        DEATH_MESSAGE_VISIBILITY,
        COLLISION_RULE
    }

    enum OptionStatus {
        ALWAYS,
        NEVER,
        FOR_OTHER_TEAMS,
        FOR_OWN_TEAM
    }
}
