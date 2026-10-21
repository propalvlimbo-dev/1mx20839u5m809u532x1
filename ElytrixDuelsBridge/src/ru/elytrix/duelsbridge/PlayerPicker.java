package ru.elytrix.duelsbridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Своё окно выбора игрока: головы онлайн-игроков, страницы, наши цвета.
 * Заменяет окно DUELS из EpicDuels (и используется для быстрого 2 на 2).
 */
final class PlayerPicker implements InventoryHolder {

    enum Mode {
        CHALLENGE("Выбор соперника", "challenge"),
        TEAM("Выбор тиммейта", "team");

        private final String title;
        private final String id;

        Mode(String title, String id) {
            this.title = title;
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREV = 45;
    private static final int RANDOM = 48;
    private static final int CLOSE = 49;
    private static final int SOLO = 50;
    private static final int NEXT = 53;
    static final int SIZE = 54;

    private Inventory inventory;
    private final Mode mode;
    private final int page;
    private final int pages;
    private final Map<Integer, UUID> shown = new HashMap<Integer, UUID>();

    private PlayerPicker(Mode mode, int page, int pages) {
        this.mode = mode;
        this.page = page;
        this.pages = pages;
    }

    Mode mode() {
        return mode;
    }

    int page() {
        return page;
    }

    Map<Integer, UUID> shown() {
        return shown;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static void open(ElytrixDuelsBridge plugin, Player viewer, Mode mode, int requestedPage) {
        List<Player> online = new ArrayList<Player>();
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.getUniqueId().equals(viewer.getUniqueId())) continue;
            online.add(candidate);
        }
        Collections.sort(online, new Comparator<Player>() {
            @Override
            public int compare(Player a, Player b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        int perPage = ITEM_SLOTS.length;
        int pages = Math.max(1, (online.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        PlayerPicker holder = new PlayerPicker(mode, page, pages);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, "\u00A77\u00AB \u00A7d" + mode.title + " \u00A77\u00BB");
        holder.inventory = inventory;

        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE, "\u00A7r");
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE, "\u00A7r");
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, black);
        }
        for (int slot : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            inventory.setItem(slot, purple);
        }

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < online.size(); i++) {
            Player target = online.get(start + i);
            int slot = ITEM_SLOTS[i];
            holder.shown.put(slot, target.getUniqueId());
            inventory.setItem(slot, head(plugin, target));
        }

        if (online.isEmpty()) {
            ItemStack info = pane(Material.PAPER, "\u00A7cНикого нет онлайн");
            ItemMeta meta = info.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList("\u00A77\u25CF \u00A7fЗагляни позже"));
                info.setItemMeta(meta);
            }
            inventory.setItem(22, info);
        }

        if (page > 0) {
            inventory.setItem(PREV, pane(Material.ARROW, "\u00A7d\u00AB Страница " + page + " \u00A77»"));
        }
        if (page + 1 < pages) {
            inventory.setItem(NEXT, pane(Material.ARROW, "\u00A7dСтраница " + (page + 2) + " \u00A77»"));
        }
        if (mode == Mode.TEAM) {
            inventory.setItem(RANDOM, button(Material.NETHER_STAR,
                    "\u00A77« \u00A7dСлучайный тиммейт \u00A77»",
                    "\u00A77\u25CF \u00A7fПриглашение уйдёт случайному игроку",
                    "\u00A77\u25CF \u00A7fОн сам решит: принять или нет"));
            inventory.setItem(SOLO, button(Material.COMPASS,
                    "\u00A77« \u00A7dРандом без тиммейта \u00A77»",
                    "\u00A77\u25CF \u00A7fВстанем в очередь 2 на 2 одни",
                    "\u00A77\u25CF \u00A7fНапарника подберём в бою"));
        }
        inventory.setItem(CLOSE, pane(Material.RED_DYE, "\u00A77« \u00A7cЗакрыть меню \u00A77»"));

        viewer.openInventory(inventory);
    }

    private static ItemStack head(ElytrixDuelsBridge plugin, Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) rawMeta;
            meta.setOwningPlayer(target);
            meta.setDisplayName("\u00A7f" + target.getName());
            List<String> lore = new ArrayList<String>();
            lore.add("\u00A7d\u00A7l\u2503 \u00A7r");
            if (plugin.duels().busy(target.getUniqueId())) {
                lore.add("\u00A7d\u00A7l\u2503 \u00A7fСейчас: \u00A7cв бою");
            } else {
                lore.add("\u00A7d\u00A7l\u2503 \u00A7fСейчас: \u00A7aсвободен");
            }
            lore.add("\u00A7d\u00A7l\u2503 \u00A7r");
            lore.add("\u00A77\u25CF \u00A7fНажмите, чтобы выбрать");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    static int prevSlot() {
        return PREV;
    }

    static int closeSlot() {
        return CLOSE;
    }

    static int randomSlot() {
        return RANDOM;
    }

    static int soloSlot() {
        return SOLO;
    }

    static int nextSlot() {
        return NEXT;
    }

    int pages() {
        return pages;
    }
}
