package ru.rooyzee.elytrixsubscribe.sub;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.functions.FunctionManager;

/** Подписка игрока: до какого времени и какие функции включены. */
public class PlayerSub {
    private final Player player;
    private final Set<String> functions = new LinkedHashSet<String>();
    private Long timeToEnd;

    public PlayerSub(Player player, Long timeToEnd) {
        this.player = player;
        this.timeToEnd = timeToEnd;
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

    /** Имена включённых функций (они же хранятся в базе). */
    public Set<String> enabledFunctions() {
        return this.functions;
    }

    public boolean has(String function) {
        return this.functions.contains(function);
    }

    /** Включённые функции объектами — для снятия эффектов. */
    public List<Function> functions(FunctionManager manager) {
        List<Function> out = new ArrayList<Function>();
        for (String name : this.functions) {
            Function function = manager.find(name);
            if (function != null) out.add(function);
        }
        return out;
    }

    public void set(String function, boolean enabled) {
        if (enabled) {
            this.functions.add(function);
        } else {
            this.functions.remove(function);
        }
    }
}
