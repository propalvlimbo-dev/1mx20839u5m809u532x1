package ru.rooyzee.elytrixsubscribe.sub;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.functions.Function;

public class PlayerSub {
    private final List<Function> enabledFunctions;
    private final Player player;
    private Long timeToEnd;

    public PlayerSub(Player player, Long timeToEnd) {
        this.player = player;
        this.timeToEnd = timeToEnd;
        this.enabledFunctions = new ArrayList<>();
    }

    public List<Function> getEnabledFunctions() {
        return this.enabledFunctions;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Long getTimeToEnd() {
        return this.timeToEnd;
    }

    public void setTimeToEnd(Long timeToEnd) {
        this.timeToEnd = timeToEnd;
    }
}