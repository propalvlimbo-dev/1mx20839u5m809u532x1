package ru.rooyzee.elytrixsubscribe.menu;

import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.impl.ParrotFunction;
import ru.rooyzee.elytrixsubscribe.utils.SignInputUtil;

public class ParrotNameMenu {
    public static void open(Player player) {
        SignInputUtil.openSignInput(player, input -> {
            String name = input == null ? "" : input.trim();
            if (name.isEmpty()) {
                ParrotMenu.open(player);
                return;
            }
            try {
                ParrotFunction function = (ParrotFunction) Main.getInstance().getFunctionsManager().getFunction("parrot");
                ParrotFunction.ParrotConfig config = function.getConfig(player.getUniqueId());
                config.name = name;
                function.saveConfig(player.getUniqueId());
                function.updateParrot(player);
            } catch (Exception ignored) {}
            ParrotMenu.open(player);
        });
    }
}