package ru.rooyzee.elytrixsubscribe.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.exceptions.ConvertPlayerException;
import ru.rooyzee.elytrixsubscribe.exceptions.NoneFunctionException;
import ru.rooyzee.elytrixsubscribe.functions.Function;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

public class CustomPlaceHolder extends PlaceholderExpansion {
    @NotNull
    public String getIdentifier() {
        return "slidesubscribe";
    }

    @NotNull
    public String getAuthor() {
        return "elytrix";
    }

    @NotNull
    public String getVersion() {
        return "1.0";
    }

    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        if (params.equals("hassubscribe")) {
            if (!Main.getInstance().getSubManager().hasSub(player)) {
                return Utils.color(Main.getInstance().getConfig().getString("settings.noHaveSubscribePlaceholder"));
            }
            try {
                PlayerSub sub = Main.getInstance().getSubManager().convertPlayer(player);
                return Utils.color("&#F8BEFB" + Utils.formatRemaining(sub.getTimeToEnd()));
            } catch (ConvertPlayerException e) {
                return Utils.color(Main.getInstance().getConfig().getString("settings.noHaveSubscribePlaceholder"));
            }
        }

        if (params.equals("hassubscribe_boolean")) {
            return Main.getInstance().getSubManager().hasSub(player) ? "true" : "false";
        }

        String[] param = params.split("_");
        if (param[0].equals("isenablefunction") && param.length > 1) {
            try {
                Function function = Main.getInstance().getFunctionsManager().getFunction(param[1]);
                PlayerSub playerSub = Main.getInstance().getSubManager().convertPlayer(player);
                return playerSub.getEnabledFunctions().contains(function) ?
                        Utils.color(Main.getInstance().getConfig().getString("settings.functionEnable")) :
                        Utils.color(Main.getInstance().getConfig().getString("settings.functionDisable"));
            } catch (ConvertPlayerException | NoneFunctionException e) {
                return Utils.color("&cНе найдено");
            }
        }
        return "";
    }
}