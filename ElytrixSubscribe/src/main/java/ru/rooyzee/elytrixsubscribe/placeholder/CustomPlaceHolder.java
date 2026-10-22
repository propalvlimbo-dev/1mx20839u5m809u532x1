package ru.rooyzee.elytrixsubscribe.placeholder;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.pet.PetType;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

/**
 * Плейсхолдеры для меню BMenu и табло.
 *
 *   %slidesubscribe_hassubscribe%       — остаток времени или «Нет»
 *   %slidesubscribe_hassubscribe_boolean%
 *   %slidesubscribe_sub_status%         — «Активна» / «Нет»
 *   %slidesubscribe_pet%                — включённый питомец
 *   %slidesubscribe_pet_active%         — его id
 *   %slidesubscribe_pet_parrot_status%  — «Включён» / «Выключен»
 *   %slidesubscribe_pet_parrot_name%    — имя или «не задано»
 *   %slidesubscribe_pet_parrot_behavior%— режим поведения
 *   %slidesubscribe_pet_parrot_look%    — окрас/свечение/размер
 *   %slidesubscribe_fn_auto-totem%      — состояние функции
 *   %slidesubscribe_fn_count%           — сколько функций включено
 */
public class CustomPlaceHolder extends PlaceholderExpansion {
    private final Main plugin;

    public CustomPlaceHolder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "slidesubscribe";
    }

    @Override
    public @NotNull String getAuthor() {
        return "elytrix";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer target, @NotNull String params) {
        if (target == null) return "";
        Player player = target.isOnline() ? Bukkit.getPlayer(target.getUniqueId()) : null;
        if (player == null) return "";
        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("hassubscribe")) {
            if (!this.plugin.getSubManager().hasSub(player)) {
                return Utils.color(this.plugin.setting("noHaveSubscribePlaceholder", "&7Нет"));
            }
            return Utils.color("&#F8BEFB" + this.plugin.getSubManager().timeLeft(player));
        }
        if (key.equals("hassubscribe_boolean")) {
            return this.plugin.getSubManager().hasSub(player) ? "true" : "false";
        }
        if (key.equals("sub_status")) {
            return this.plugin.getSubManager().hasSub(player)
                    ? Utils.color(this.plugin.setting("hasSubscribePlaceholder", "&#F8BEFBДа"))
                    : Utils.color(this.plugin.setting("noHaveSubscribePlaceholder", "&7Нет"));
        }
        if (key.equals("sub_time")) {
            return Utils.color("&#F8BEFB" + this.plugin.getSubManager().timeLeft(player));
        }
        if (key.equals("pet")) {
            return Utils.color(this.plugin.getPetManager().activeLine(player));
        }
        if (key.equals("pet_active")) {
            PetType active = this.plugin.getPetManager().activeType(player);
            return active == null ? "" : active.id();
        }
        if (key.equals("fn_count")) {
            int count = 0;
            for (String name : new String[]{"auto-totem", "speed", "anti-fall", "antifood"}) {
                if (this.functionOn(player, name)) count++;
            }
            return count + "/4";
        }
        if (key.startsWith("pet_")) {
            String[] parts = key.split("_");
            if (parts.length >= 3) {
                PetType type = PetType.byId(parts[1]);
                if (type != null) {
                    String what = parts[2];
                    if (what.equals("status")) {
                        return Utils.color(this.plugin.getPetManager().statusLine(player, type));
                    }
                    if (what.equals("name")) {
                        return Utils.color(this.plugin.getPetManager().nameLine(player, type));
                    }
                    if (what.equals("behavior") || what.equals("mode")) {
                        return Utils.color("&#F8BEFB"
                                + this.plugin.getPetManager().behaviorLine(player, type));
                    }
                    if (what.equals("look") || what.equals("variant")) {
                        return Utils.color("&#F8BEFB"
                                + this.plugin.getPetManager().lookLine(player, type));
                    }
                }
            }
            return "";
        }
        if (key.startsWith("fn_")) {
            String name = params.substring(3);
            return Utils.color(this.functionOn(player, name)
                    ? this.plugin.setting("functionEnable", "&aВключена")
                    : this.plugin.setting("functionDisable", "&cВыключена"));
        }
        if (key.startsWith("isenablefunction_")) {
            String name = params.substring("isenablefunction_".length());
            return Utils.color(this.functionOn(player, name)
                    ? this.plugin.setting("functionEnable", "&aВключена")
                    : this.plugin.setting("functionDisable", "&cВыключена"));
        }
        return "";
    }

    private boolean functionOn(Player player, String name) {
        if (!this.plugin.getFunctionsManager().has(name)) return false;
        return this.plugin.getSubManager().isFunctionEnabled(player,
                this.plugin.getFunctionsManager().find(name));
    }
}
