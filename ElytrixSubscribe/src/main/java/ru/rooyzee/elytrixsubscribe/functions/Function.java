package ru.rooyzee.elytrixsubscribe.functions;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

public abstract class Function implements Listener {
    private final String name;
    private final String displayName;

    public Function(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public void onEnable(Player player) {
        player.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.moduleEnable").replace("%functionName", this.displayName)));
        this.enableFunction(player);
    }

    public void onDisable(Player player) {
        player.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.moduleDisable").replace("%functionName", this.displayName)));
        this.disableFunction(player);
    }

    private void disableFunction(Player player) {
        try {
            PlayerSub playerSub = Main.getInstance().getSubManager().convertPlayer(player);
            playerSub.getEnabledFunctions().remove(this);
        } catch (ConvertPlayerException ignored) {}
    }

    private void enableFunction(Player player) {
        try {
            PlayerSub playerSub = Main.getInstance().getSubManager().convertPlayer(player);
            playerSub.getEnabledFunctions().add(this);
        } catch (ConvertPlayerException ignored) {}
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}