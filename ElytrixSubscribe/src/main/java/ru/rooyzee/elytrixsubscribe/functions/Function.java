package ru.rooyzee.elytrixsubscribe.functions;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.sub.SubManager;

/**
 * Функция подписки (анти-голод, авто-тотем, анти-падение, скорость).
 *
 * Включение сохраняется в базу: после перезахода функция остаётся включённой
 * и её эффект (например, модификатор скорости) накладывается заново.
 */
public abstract class Function implements Listener {
    private final String name;
    private final String displayName;

    protected Function(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /** Игрок нажал «включить». */
    public void onEnable(Player player) {
        SubManager manager = Main.getInstance().getSubManager();
        if (!manager.setFunction(player, this, true)) {
            Main.getInstance().msg(player, Main.getInstance().message("noneSubscribe",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fУ вас нет активной подписки."));
            return;
        }
        this.apply(player);
        Main.getInstance().msg(player, Main.getInstance().message("moduleEnable",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                        + "&fФункция &#F8BEFB%functionName &fбыла &aвключена&f.")
                .replace("%functionName", this.displayName));
    }

    /** Игрок нажал «выключить». */
    public void onDisable(Player player) {
        SubManager manager = Main.getInstance().getSubManager();
        manager.setFunction(player, this, false);
        this.clear(player);
        Main.getInstance().msg(player, Main.getInstance().message("moduleDisable",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                        + "&fФункция &#F8BEFB%functionName &fбыла &cвыключена&f.")
                .replace("%functionName", this.displayName));
    }

    /** Наложить эффект (вызывается и при входе, если функция включена). */
    public void apply(Player player) {
    }

    /** Убрать эффект. */
    public void clear(Player player) {
    }
}
