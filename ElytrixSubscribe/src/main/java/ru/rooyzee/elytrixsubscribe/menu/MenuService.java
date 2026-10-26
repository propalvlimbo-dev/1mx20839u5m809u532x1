package ru.rooyzee.elytrixsubscribe.menu;

import org.bukkit.entity.Player;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.pet.PetType;

/**
 * Какое меню открыть. Тексты и иконки лежат в BMenu
 * (plugins/BMenu/menu/elytrix/main/sub.yml и .../sub/*.yml), а id меню
 * можно поменять в config.yml.
 */
public class MenuService {
    private final Main plugin;

    public MenuService(Main plugin) {
        this.plugin = plugin;
    }

    public String mainId() {
        return this.plugin.getConfig().getString("menus.main", "elytrix:sub");
    }

    public String petsId() {
        return this.plugin.getConfig().getString("menus.pets", "elytrix:sub_pets");
    }

    public String functionsId() {
        return this.plugin.getConfig().getString("menus.functions", "elytrix:sub");
    }

    public String petId(PetType type) {
        return this.plugin.getConfig().getString("menus.pet-" + type.id(),
                "elytrix:sub_pet_" + type.id());
    }

    public boolean openMain(Player player) {
        if (!this.plugin.getSubManager().hasSub(player)) {
            this.plugin.msg(player, this.plugin.message("noneSubscribe",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fУ вас нет активной подписки."));
            return false;
        }
        if (this.plugin.getMenus().open(player, this.mainId())) return true;
        this.fallback(player);
        return false;
    }

    public boolean openPets(Player player) {
        if (!this.plugin.getSubManager().hasSub(player)) {
            this.plugin.msg(player, this.plugin.message("noneSubscribe",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fУ вас нет активной подписки."));
            return false;
        }
        if (this.plugin.getMenus().open(player, this.petsId())) return true;
        this.fallbackPets(player);
        return false;
    }

    public boolean openPet(Player player, PetType type) {
        if (type == null) return this.openPets(player);
        if (!this.plugin.getSubManager().hasSub(player)) {
            this.plugin.msg(player, this.plugin.message("noneSubscribe",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fУ вас нет активной подписки."));
            return false;
        }
        if (this.plugin.getMenus().open(player, this.petId(type))) return true;
        this.fallbackPet(player, type);
        return false;
    }

    public boolean openFunctions(Player player) {
        if (!this.plugin.getSubManager().hasSub(player)) {
            this.plugin.msg(player, this.plugin.message("noneSubscribe",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                            + "&fУ вас нет активной подписки."));
            return false;
        }
        if (this.plugin.getMenus().open(player, this.functionsId())) return true;
        this.fallbackFunctions(player);
        return false;
    }

    // ------------------------------------------------------------ без BMenu

    private void fallback(Player player) {
        this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                + "&fПодписка активна до &f" + this.plugin.getSubManager().timeLeft(player));
        this.fallbackFunctions(player);
        this.fallbackPets(player);
    }

    private void fallbackPets(Player player) {
        PetType active = this.plugin.getPetManager().activeType(player);
        this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                + "&fПитомцы: &f/subscribe pet enable <" + PetType.ids() + ">"
                + " &7(включён: &f" + (active == null ? "нет" : active.displayName()) + "&7)");
    }

    private void fallbackPet(Player player, PetType type) {
        this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                + "&f" + type.displayName() + ": &7режим &f"
                + this.plugin.getPetManager().behaviorLine(player, type)
                + "&7, " + type.lookName().toLowerCase() + " &f"
                + this.plugin.getPetManager().lookLine(player, type));
    }

    private void fallbackFunctions(Player player) {
        StringBuilder out = new StringBuilder();
        for (String name : new String[]{"auto-totem", "speed", "anti-fall", "antifood"}) {
            if (out.length() > 0) out.append("&7, &f");
            boolean on = this.plugin.getFunctionsManager().has(name)
                    && this.plugin.getSubManager().isFunctionEnabled(player,
                    this.plugin.getFunctionsManager().find(name));
            out.append(name).append(on ? "&a+&f" : "&c-&f");
        }
        this.plugin.msg(player, "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» "
                + "&fФункции: " + out + " &7(/subscribe toggle <функция>)");
    }
}
