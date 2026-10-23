package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.bet.ActiveBet;
import ru.rooyzee.elytrixcasino.roulette.Roulette;
import ru.rooyzee.elytrixcasino.roulette.RouletteSettings;
import ru.rooyzee.elytrixcasino.roulette.Sector;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouletteGui implements Listener {

    private static final List<Material> BET_ICONS = Arrays.asList(
            Material.GOLD_NUGGET,
            Material.RAW_GOLD,
            Material.GOLD_INGOT,
            Material.AMETHYST_SHARD,
            Material.EMERALD,
            Material.DIAMOND
    );

    private final Main plugin;

    public RouletteGui(Main plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Roulette roulette) {
        RouletteSettings settings = plugin.getSettings();
        GuiConfig gui = plugin.getRouletteGuiConfig();
        int size = 54;

        RouletteHolder holder = new RouletteHolder(roulette.getId());
        Inventory inventory = Bukkit.createInventory(holder, size,
                Text.color(gui.getString("title", "&#F8BEFB&lРУЛЕТКА &8┃ &7Выбор ставки")));
        holder.setInventory(inventory);

        Map<Integer, ItemStack> actionButtons = new LinkedHashMap<>();

        if (gui.getBoolean("info-enabled", true)) {
            int infoSlot = gui.getInt("info-slot", 13);
            if (infoSlot >= 0 && infoSlot < size) {
                actionButtons.put(infoSlot, createInfoItem(gui));
            }
        }

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

        List<Integer> betSlots = gui.getIntegerList("bet-slots");
        if (betSlots.isEmpty()) {
            betSlots = Arrays.asList(20, 22, 24, 29, 31, 33);
        }
        List<String> customIcons = gui.getStringList("bet-icons");
        Material fallbackMaterial = gui.getMaterial("bet-item", Material.GOLD_INGOT);

        for (int i = 0; i < bets.size() && i < betSlots.size(); i++) {
            int slot = betSlots.get(i);
            if (slot < 0 || slot >= size) {
                continue;
            }
            long bet = bets.get(i);
            Material mat = fallbackMaterial;
            if (i < customIcons.size()) {
                Material matched = Material.matchMaterial(customIcons.get(i));
                if (matched != null && matched.isItem()) {
                    mat = matched;
                }
            } else if (i < BET_ICONS.size()) {
                mat = BET_ICONS.get(i);
            }
            boolean glow = i >= bets.size() - 2;
            actionButtons.put(slot, createBetItem(mat, bet, glow));
            holder.getBets().put(slot, bet);
        }

        plugin.getAnimator().openStandardMenu(player, inventory, closeItem(), actionButtons);
    }

    private ItemStack createInfoItem(GuiConfig gui) {
        Material mat = gui.getMaterial("info-item", Material.AMETHYST_SHARD);
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(gui.getString("info-name", "&#F8BEFB&lКолесо Фортуны")));
            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("info-lore")) {
                lore.add(Text.color(line));
            }
            meta.setLore(lore);
            GuiGuard.applyGlow(meta);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private ItemStack closeItem() {
        GuiConfig gui = plugin.getRouletteGuiConfig();
        ItemStack item = new ItemStack(gui.getMaterial("close-item", Material.RED_DYE), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(gui.getString("close-name", "&cЗакрыть")));
            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("close-lore")) {
                lore.add(Text.color(line));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
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

    private ItemStack createBetItem(Material material, long bet, boolean glow) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            GuiConfig gui = plugin.getRouletteGuiConfig();
            String formattedBet = Text.formatMoney(bet);
            String name = gui.getString("bet-name", "&#F8BEFB&lСтавка: &f%bet% ⛂");
            meta.setDisplayName(Text.color(name.replace("%bet%", formattedBet)));

            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("bet-lore")) {
                lore.add(Text.color(line.replace("%bet%", formattedBet)));
            }
            if (gui.getBoolean("show-chances", true)) {
                int totalWeight = plugin.getSettings().getTotalWeight();
                if (totalWeight > 0) {
                    lore.add(Text.color("&#F8BEFB&l┃ "));
                    for (Sector sector : plugin.getSettings().getSectors()) {
                        double percent = 100.0D * sector.getWeight() / totalWeight;
                        String payout = sector.getMultiplier() > 0.0D
                                ? "&#F8BEFB+" + Text.formatMoney(Math.round(bet * sector.getMultiplier())) + " ⛂"
                                : "&8проигрыш";
                        lore.add(Text.color("&#F8BEFB&l┃ &f" + sector.getLabel() + ": " + payout
                                + " &8(" + Text.formatPercent(percent) + "%)"));
                    }
                    lore.add("");
                    lore.add(Text.color("&#F8BEFB▶ &fНажмите, чтобы крутить"));
                }
            }
            if (glow) {
                GuiGuard.applyGlow(meta);
            }
            meta.addItemFlags(ItemFlag.values());
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
        event.setResult(Event.Result.DENY);
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
            plugin.getAnimator().stop(player.getUniqueId());
            player.closeInventory();
            return;
        }
        RouletteHolder holder = (RouletteHolder) event.getClickedInventory().getHolder();
        Long bet = holder.getBets().get(event.getRawSlot());
        if (bet == null) {
            return;
        }

        ActiveBet active = plugin.getActiveBets().getActiveBet(player.getUniqueId());
        if (active != null) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", active.getGameType().getDisplayName(),
                    "%amount%", Text.formatMoney(active.getAmount()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        Roulette roulette = plugin.getRouletteManager().get(holder.getRouletteId());
        if (roulette == null || !roulette.isSpawned()) {
            plugin.getAnimator().stop(player.getUniqueId());
            player.closeInventory();
            plugin.send(player, "not-found");
            return;
        }
        if (roulette.isBusy()) {
            plugin.send(player, "busy");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }
        plugin.getAnimator().stop(player.getUniqueId());
        player.closeInventory();
        roulette.startGame(player, bet);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RouletteHolder) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RouletteHolder)) {
            return;
        }
        plugin.getAnimator().stop(event.getPlayer().getUniqueId());
        if (event.getPlayer() instanceof Player) {
            GuiGuard.strip(plugin, (Player) event.getPlayer());
        }
    }
}
