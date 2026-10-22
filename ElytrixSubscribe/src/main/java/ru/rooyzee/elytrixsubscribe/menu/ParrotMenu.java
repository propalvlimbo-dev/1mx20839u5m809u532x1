package ru.rooyzee.elytrixsubscribe.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.functions.impl.ParrotFunction;
import ru.rooyzee.elytrixsubscribe.sub.PlayerSub;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class ParrotMenu implements Listener {
    private static final String MENU_TITLE = Utils.color("&#F8BEFBПодписка ElytriX+");

    public ParrotMenu() {
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
    }

    public static void open(Player player) {
        if (!Main.getInstance().getSubManager().hasSub(player)) {
            player.sendMessage(Utils.color(Main.getInstance().getConfig().getString("messages.noneSubscribe")));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, MENU_TITLE);
        ItemStack gray = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack purple = createItem(Material.PURPLE_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, gray);
        }

        int[] purpleSlots = {27, 35, 36, 37, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int slot : purpleSlots) {
            inv.setItem(slot, purple);
        }

        try {
            ParrotFunction function = (ParrotFunction) Main.getInstance().getFunctionsManager().getFunction("parrot");
            ParrotFunction.ParrotConfig config = function.getConfig(player.getUniqueId());

            ItemStack info = createItem(Material.PAPER, "&#F8BEFBИнформация",
                    "&7Настройка вашего личного",
                    "&7летающего питомца-попугая.",
                    "",
                    "&fСтатус подписки: &aАктивна"
            );
            inv.setItem(4, info);

            ItemStack behaviorItem = createItem(Material.NETHERITE_HOE, "&#F8BEFBРежим поведения",
                    "&7Текущий режим: &#F8BEFB" + getBehaviorName(config.behavior),
                    "",
                    "&7» Нажмите, чтобы изменить"
            );
            inv.setItem(20, behaviorItem);

            ItemStack variantItem = createItem(Material.TOTEM_OF_UNDYING, "&#F8BEFBОкрас попугая",
                    "&7Текущий цвет: &#F8BEFB" + getColorName(config.variant),
                    "",
                    "&7» Нажмите, чтобы изменить"
            );
            inv.setItem(21, variantItem);

            String currentName = (config.name != null) ? config.name : "&7Не установлено";
            ItemStack nameItem = createItem(Material.NETHERITE_SWORD, "&#F8BEFBИмя питомца",
                    "&7Текущее имя: &f" + currentName,
                    "",
                    "&7» Нажмите, чтобы ввести имя"
            );
            inv.setItem(22, nameItem);

            boolean hasModule = false;
            try {
                hasModule = Main.getInstance().getSubManager().convertPlayer(player).getEnabledFunctions().contains(function);
            } catch (Exception ignored) {}

            ItemStack toggleItem = createItem(Material.GOLD_NUGGET, "&#F8BEFBОтображение",
                    "&7Статус: " + (hasModule ? "&aВключен" : "&cВыключен"),
                    "",
                    "&7» Нажмите для переключения"
            );
            inv.setItem(23, toggleItem);

        } catch (Exception ignored) {}

        ItemStack foodStatus = createItem(Material.COOKED_BEEF, "&#F8BEFBДругие функции",
                "&7Быстрый переход к",
                "&7настройкам вашего аккаунта."
        );
        inv.setItem(24, foodStatus);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(MENU_TITLE)) {
            e.setCancelled(true);
            Player player = (Player) e.getWhoClicked();
            int slot = e.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            try {
                ParrotFunction function = (ParrotFunction) Main.getInstance().getFunctionsManager().getFunction("parrot");
                ParrotFunction.ParrotConfig config = function.getConfig(player.getUniqueId());

                if (slot == 20) {
                    if (config.behavior.equals("orbit")) {
                        config.behavior = "hover";
                    } else if (config.behavior.equals("hover")) {
                        config.behavior = "head";
                    } else {
                        config.behavior = "orbit";
                    }
                    function.saveConfig(player.getUniqueId());
                    function.updateParrot(player);
                    open(player);
                } else if (slot == 21) {
                    // FIX: Переключение через ordinal (int)
                    Parrot.Variant[] variants = Parrot.Variant.values();
                    int currentOrdinal = config.variant;

                    // Защита от некорректных данных из БД
                    if (currentOrdinal < 0 || currentOrdinal >= variants.length) {
                        currentOrdinal = 0;
                    }

                    int nextIndex = (currentOrdinal + 1) % variants.length;
                    config.variant = nextIndex; // Сохраняем как int

                    function.saveConfig(player.getUniqueId());
                    function.updateParrot(player);
                    open(player);
                } else if (slot == 22) {
                    player.closeInventory();
                    ParrotNameMenu.open(player);
                } else if (slot == 23) {
                    if (Main.getInstance().getSubManager().hasSub(player)) {
                        try {
                            PlayerSub playerSub = Main.getInstance().getSubManager().convertPlayer(player);
                            if (playerSub.getEnabledFunctions().contains(function)) {
                                function.onDisable(player);
                            } else {
                                function.onEnable(player);
                            }
                            open(player);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.color(name));
            List<String> list = new ArrayList<>();
            for (String s : lore) {
                list.add(Utils.color(s));
            }
            meta.setLore(list);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String getBehaviorName(String behavior) {
        switch (behavior) {
            case "hover": return "На месте";
            case "head": return "На плече";
            default: return "Вокруг вас";
        }
    }

    // FIX: Принимаем int и маппим на Bukkit Variant для красивого названия
    private static String getColorName(int variantId) {
        Parrot.Variant[] variants = Parrot.Variant.values();
        if (variantId >= 0 && variantId < variants.length) {
            Parrot.Variant variant = variants[variantId];
            switch (variant) {
                case BLUE: return "Синий";
                case GREEN: return "Зеленый";
                case CYAN: return "Бирюзовый";
                case GRAY: return "Серый";
                default: return "Красный";
            }
        }
        return "Красный";
    }
}