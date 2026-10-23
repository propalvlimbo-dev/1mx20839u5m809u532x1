package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.roulette.Roulette;
import ru.rooyzee.elytrixcasino.roulette.RouletteSettings;
import ru.rooyzee.elytrixcasino.roulette.Sector;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RouletteGui implements Listener {

    private final Main plugin;

    public RouletteGui(Main plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Roulette roulette) {
        RouletteSettings settings = plugin.getSettings();
        GuiConfig gui = plugin.getRouletteGuiConfig();
        int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));
        int size = rows * 9;

        RouletteHolder holder = new RouletteHolder(roulette.getId());
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.color(gui.getString("title", "&#F8BEFB&lРулетка")));
        holder.setInventory(inventory);

        paint(inventory, gui.getIntegerList("black-slots"),
                gui.getMaterial("black-pane", Material.BLACK_STAINED_GLASS_PANE));
        paint(inventory, gui.getIntegerList("purple-slots"),
                gui.getMaterial("purple-pane", Material.PURPLE_STAINED_GLASS_PANE));

        List<Long> bets = new ArrayList<>();
        for (Number number : readBets()) {
            long bet = number.longValue();
            if (bet >= settings.getMinBet() && bet <= settings.getMaxBet()) {
                bets.add(bet);
            }
        }
        if (bets.isEmpty()) {
            bets.add(settings.getMinBet());
        }

        Material betMaterial = gui.getMaterial("bet-item", Material.GOLD_INGOT);
        List<Integer> betSlots = gui.getIntegerList("bet-slots");
        if (betSlots.isEmpty()) {
            betSlots = Arrays.asList(20, 21, 22, 23, 24);
        }
        for (int i = 0; i < bets.size() && i < betSlots.size(); i++) {
            int slot = betSlots.get(i);
            if (slot < 0 || slot >= size) {
                continue;
            }
            long bet = bets.get(i);
            inventory.setItem(slot, createBetItem(betMaterial, bet));
            holder.getBets().put(slot, bet);
        }

        int closeSlot = gui.getInt("close-slot", 45);
        if (closeSlot >= 0 && closeSlot < size) {
            inventory.setItem(closeSlot, closeItem());
        }

        player.openInventory(inventory);
    }

    private void paint(Inventory inventory, List<Integer> slots, Material material) {
        ItemStack pane = pane(material);
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, pane);
            }
        }
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&r"));
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private ItemStack closeItem() {
        GuiConfig gui = plugin.getRouletteGuiConfig();
        ItemStack item = new ItemStack(gui.getMaterial("close-item", Material.RED_DYE), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(gui.getString("close-name", "&7« &cЗакрыть меню &7»")));
            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("close-lore")) {
                lore.add(Text.color(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private List<? extends Number> readBets() {
        List<Integer> ints = plugin.getRouletteGuiConfig().getIntegerList("bets");
        if (!ints.isEmpty()) {
            return ints;
        }
        return List.of(plugin.getSettings().getMinBet());
    }

    private ItemStack createBetItem(Material material, long bet) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GuiConfig gui = plugin.getRouletteGuiConfig();
            String name = gui.getString("bet-name", "&7« &#F8BEFBСтавка %bet% &7»");
            meta.setDisplayName(Text.color(name.replace("%bet%", String.valueOf(bet))));

            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("bet-lore")) {
                lore.add(Text.color(line.replace("%bet%", String.valueOf(bet))));
            }
            if (gui.getBoolean("show-chances", true)) {
                int totalWeight = plugin.getSettings().getTotalWeight();
                if (totalWeight > 0) {
                    lore.add(Text.color("&#F8BEFB&l┃ "));
                    for (Sector sector : plugin.getSettings().getSectors()) {
                        double percent = 100.0D * sector.getWeight() / totalWeight;
                        String payout = sector.getMultiplier() > 0.0D
                                ? "&#F8BEFB" + Math.round(bet * sector.getMultiplier())
                                : "&c—";
                        lore.add(Text.color("&#F8BEFB&l┃ &f" + sector.getLabel() + ": " + payout
                                + " &8| &#F8BEFB" + Text.formatPercent(percent) + "%"));
                    }
                    lore.add(Text.color("&#F8BEFB&l┃ "));
                    lore.add(Text.color("&7● &fНажмите, чтобы крутить"));
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RouletteHolder)) {
            return;
        }
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof RouletteHolder)) {
            return;
        }
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player)) {
            return;
        }
        Player player = (Player) clicker;
        if (GuiGuard.isMarked(plugin, player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        int closeSlot = plugin.getRouletteGuiConfig().getInt("close-slot", 45);
        if (event.getRawSlot() == closeSlot) {
            player.closeInventory();
            return;
        }
        RouletteHolder holder = (RouletteHolder) event.getClickedInventory().getHolder();
        Long bet = holder.getBets().get(event.getRawSlot());
        if (bet == null) {
            return;
        }

        Roulette roulette = plugin.getRouletteManager().get(holder.getRouletteId());
        if (roulette == null || !roulette.isSpawned()) {
            player.closeInventory();
            plugin.send(player, "not-found");
            return;
        }
        if (roulette.isBusy()) {
            plugin.send(player, "busy");
            return;
        }
        player.closeInventory();
        roulette.startGame(player, bet);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RouletteHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RouletteHolder)) {
            return;
        }
        if (event.getPlayer() instanceof Player) {
            GuiGuard.strip(plugin, (Player) event.getPlayer());
        }
    }
}
