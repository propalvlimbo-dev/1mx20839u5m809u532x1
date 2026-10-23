package ru.rooyzee.elytrixcasino.gui;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.automate.Automate;
import ru.rooyzee.elytrixcasino.automate.AutomateSettings;
import ru.rooyzee.elytrixcasino.automate.SlotSymbol;
import ru.rooyzee.elytrixcasino.bet.ActiveBet;
import ru.rooyzee.elytrixcasino.bet.GameType;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class AutomateGui implements Listener {

    private static final int SIZE = 5;
    private static final int REEL_LENGTH = 80;
    private static final int CLOSE_LEFT = 45;
    private static final int PLAY_SLOT = 49;
    private static final int CLOSE_RIGHT = 53;
    private static final int[] LEFT_COMBO_SLOTS = {0, 9, 18, 27, 36};
    private static final int[] RIGHT_COMBO_SLOTS = {8, 17, 26, 35, 44};

    private final Main plugin;

    public AutomateGui(Main plugin) {
        this.plugin = plugin;
    }

    public void applyPending(Player player) {
        GuiGuard.strip(plugin, player);
        ru.rooyzee.elytrixcasino.data.DataManager.Pending pending =
                plugin.getDataManager().takePending(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (pending.win > 0L) {
            plugin.getEconomy().depositPlayer(player, pending.win);
            plugin.send(player, "automate-win",
                    "%player%", player.getName(),
                    "%amount%", String.valueOf(pending.win),
                    "%combo%", pending.combo,
                    "%bet%", String.valueOf(pending.bet));
        } else {
            plugin.send(player, "automate-lose", "%bet%", String.valueOf(pending.bet));
        }
    }

    public void open(Player player, Automate automate) {
        GuiConfig gui = plugin.getAutomateGuiConfig();
        AutomateSettings settings = plugin.getAutomateSettings();
        AutomateHolder holder = new AutomateHolder(automate.getId());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.color(gui.getString("title", "&#F8BEFB&lАвтомат")
                        .replace("%bet%", String.valueOf(automate.getBet()))));
        holder.setInventory(inventory);
        holder.setPlaySlot(PLAY_SLOT);

        Map<Integer, ItemStack> comboItems = buildComboItems(settings);
        Map<Integer, ItemStack> controls = new LinkedHashMap<>(comboItems);
        controls.put(PLAY_SLOT, playItem(automate.getBet()));

        plugin.getAnimator().openAutomateMenu(player, inventory, closeItem(), controls, row -> {
            ItemStack leftCombo = comboItems.get(LEFT_COMBO_SLOTS[row]);
            if (leftCombo != null) {
                inventory.setItem(LEFT_COMBO_SLOTS[row], leftCombo);
            }
            for (int col = 0; col < SIZE; col++) {
                inventory.setItem(gridSlot(row, col), tagged(randomSymbol().icon()));
            }
            ItemStack rightCombo = comboItems.get(RIGHT_COMBO_SLOTS[row]);
            if (rightCombo != null) {
                inventory.setItem(RIGHT_COMBO_SLOTS[row], rightCombo);
            }
        });
    }

    private Map<Integer, ItemStack> buildComboItems(AutomateSettings settings) {
        Map<Integer, ItemStack> map = new LinkedHashMap<>();
        List<SlotSymbol> symbols = settings.getSymbols();
        int[] slots = {0, 9, 18, 27, 36, 8, 17, 26, 35, 44};
        int index = 0;
        for (SlotSymbol symbol : symbols) {
            if (index >= slots.length) {
                break;
            }
            ItemStack item = symbol.icon();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Text.color(symbol.getName()));
                List<String> lore = new ArrayList<>();
                lore.add(Text.color("&#F8BEFB&l┃ &fЛиния: &#F8BEFBx" + Text.formatMultiplier(symbol.pay(5))));
                lore.add(Text.color("&#F8BEFB&l┃ &f2 линии: &#F8BEFBx" + Text.formatMultiplier(symbol.pay(5) * 2.0D)));
                lore.add(Text.color("&#F8BEFB&l┃ &f3 линии: &#F8BEFBx" + Text.formatMultiplier(symbol.pay(5) * 3.0D)));
                lore.add(Text.color("&#F8BEFB&l┃ "));
                lore.add(Text.color("&7● &fРяд, столбец или крест — одинаково"));
                lore.add(Text.color("&7● &fМаксимум за спин: &#F8BEFBx" + Text.formatMultiplier(settings.getMaxMultiplier())));
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.values());
                item.setItemMeta(meta);
            }
            map.put(slots[index], GuiGuard.mark(plugin, item));
            index++;
        }
        return map;
    }

    private ItemStack playItem(long bet) {
        GuiConfig gui = plugin.getAutomateGuiConfig();
        ItemStack item = new ItemStack(gui.getMaterial("play-item", Material.PINK_DYE), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(gui.getString("play-name", "&7« &#F8BEFBИграть &7»")));
            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("play-lore")) {
                lore.add(Text.color(line.replace("%bet%", String.valueOf(bet))));
            }
            meta.addItemFlags(ItemFlag.values());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private ItemStack closeItem() {
        GuiConfig gui = plugin.getAutomateGuiConfig();
        ItemStack item = new ItemStack(gui.getMaterial("close-item", Material.RED_DYE), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(gui.getString("close-name", "&7« &cЗакрыть меню &7»")));
            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("close-lore")) {
                lore.add(Text.color(line));
            }
            meta.addItemFlags(ItemFlag.values());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private ItemStack tagged(ItemStack item) {
        return GuiGuard.mark(plugin, item);
    }

    private static int gridSlot(int row, int col) {
        return row * 9 + (2 + col);
    }

    private SlotSymbol randomSymbol() {
        List<SlotSymbol> symbols = plugin.getAutomateSettings().getSymbols();
        int total = plugin.getAutomateSettings().getTotalWeight();
        if (total <= 0) {
            return symbols.get(0);
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (SlotSymbol symbol : symbols) {
            cumulative += symbol.getWeight();
            if (roll < cumulative) {
                return symbol;
            }
        }
        return symbols.get(symbols.size() - 1);
    }

    private SlotSymbol symbolAt(int index) {
        List<SlotSymbol> symbols = plugin.getAutomateSettings().getSymbols();
        if (symbols.isEmpty()) {
            return new SlotSymbol("flesh", Material.ROTTEN_FLESH, 1, "&8Гнилая плоть", 1.2, 1.2, 1.2);
        }
        int clamped = Math.max(0, Math.min(symbols.size() - 1, index));
        return symbols.get(clamped);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AutomateHolder)) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (GuiGuard.isMarked(plugin, player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof AutomateHolder)) {
            return;
        }
        AutomateHolder holder = (AutomateHolder) event.getClickedInventory().getHolder();
        if (holder.isSpinning()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_LEFT || slot == CLOSE_RIGHT) {
            plugin.getAnimator().stop(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (slot != holder.getPlaySlot()) {
            return;
        }
        Automate automate = plugin.getAutomateManager().get(holder.getAutomateId());
        if (automate == null) {
            plugin.getAnimator().stop(player.getUniqueId());
            player.closeInventory();
            plugin.send(player, "not-found");
            return;
        }
        startSpin(player, automate, holder, event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AutomateHolder) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AutomateHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AutomateHolder)) {
            return;
        }
        plugin.getAnimator().stop(event.getPlayer().getUniqueId());
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        GuiGuard.strip(plugin, player);
    }

    private void startSpin(Player player, Automate automate, AutomateHolder holder, Inventory inventory) {
        UUID uuid = player.getUniqueId();
        ActiveBet existing = plugin.getActiveBets().getActiveBet(uuid);
        if (existing != null) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", existing.getGameType().getDisplayName(),
                    "%amount%", String.valueOf(existing.getAmount()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }
        if (automate.isBusy()) {
            plugin.send(player, "automate-busy");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        long bet = automate.getBet();
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, bet)) {
            plugin.send(player, "no-money");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }
        if (!plugin.getActiveBets().tryAcquire(uuid, GameType.AUTOMATE, automate.getId(), bet)) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", GameType.AUTOMATE.getDisplayName(),
                    "%amount%", String.valueOf(bet));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(player, bet);
        if (!response.transactionSuccess()) {
            plugin.getActiveBets().release(uuid);
            plugin.send(player, "no-money");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        plugin.getAnimator().stop(uuid);
        automate.setBusy(true);
        holder.setSpinning(true);
        plugin.send(player, "bet-placed", "%bet%", String.valueOf(bet));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9F, 0.65F);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        SlotSymbol[][] grid = createGrid(rng);
        SlotSymbol[][] reels = new SlotSymbol[SIZE][REEL_LENGTH];
        double[] pos = new double[SIZE];
        double[] speed = new double[SIZE];
        int[] stopAt = new int[SIZE];
        int[] land = new int[SIZE];
        boolean[] stopped = new boolean[SIZE];
        AutomateSettings settings = plugin.getAutomateSettings();
        int stagger = settings.getReelStopDelay();
        int total = Math.max(32, settings.getSpinTicks());

        for (int col = 0; col < SIZE; col++) {
            for (int i = 0; i < REEL_LENGTH; i++) {
                reels[col][i] = randomSymbol();
            }
            land[col] = rng.nextInt(REEL_LENGTH);
            for (int row = 0; row < SIZE; row++) {
                reels[col][floorMod(land[col] - row, REEL_LENGTH)] = grid[row][col];
            }
            pos[col] = land[col] - 28.0D;
            speed[col] = 3.2D;
            stopAt[col] = total - (SIZE - 1 - col) * stagger;
        }

        new BukkitRunnable() {
            private int tick = 0;
            private int lastStep = -1;

            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    plugin.getActiveBets().release(uuid);
                    holder.setSpinning(false);
                    automate.setBusy(false);
                    cancel();
                    return;
                }
                tick++;
                boolean viewing = player.isOnline() && !inventory.getViewers().isEmpty();
                boolean allStopped = true;
                for (int col = 0; col < SIZE; col++) {
                    if (stopped[col]) {
                        if (viewing) {
                            renderColumn(inventory, reels[col], col, pos[col], false);
                        }
                        continue;
                    }
                    allStopped = false;
                    double progress = Math.min(1.0D, (double) tick / (double) stopAt[col]);
                    if (tick >= stopAt[col]) {
                        int shown = floorMod((int) Math.floor(pos[col]), REEL_LENGTH);
                        int dist = floorMod(land[col] - shown, REEL_LENGTH);
                        if (dist == 0) {
                            stopped[col] = true;
                            pos[col] = land[col];
                            if (player.isOnline()) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.85F, 0.80F + col * 0.16F);
                            }
                        } else {
                            speed[col] = dist <= 5 ? 0.18D : 0.45D;
                            pos[col] += speed[col];
                        }
                    } else if (progress < 0.50D) {
                        speed[col] = 2.8D;
                        pos[col] += speed[col];
                    } else if (progress < 0.72D) {
                        speed[col] = 1.15D;
                        pos[col] += speed[col];
                    } else if (progress < 0.88D) {
                        speed[col] = 0.38D;
                        pos[col] += speed[col];
                    } else {
                        speed[col] = 0.16D;
                        pos[col] += speed[col];
                    }
                    boolean blur = !stopped[col] && speed[col] >= 1.0D;
                    if (viewing) {
                        renderColumn(inventory, reels[col], col, pos[col], blur);
                    }
                }
                int step = (int) Math.floor(pos[0]);
                if (step != lastStep) {
                    lastStep = step;
                    if (player.isOnline()) {
                        float pitch = allStopped ? 1.0F : (float) Math.min(1.9D, 0.55D + speed[0] * 0.35D);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4F, pitch);
                    }
                }
                if (!allStopped) {
                    return;
                }
                cancel();
                finish(player, automate, holder, inventory, grid, bet);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void renderColumn(Inventory inventory, SlotSymbol[] reel, int col, double pos, boolean blur) {
        int base = (int) Math.floor(pos);
        for (int row = 0; row < SIZE; row++) {
            SlotSymbol symbol = reel[floorMod(base - row, REEL_LENGTH)];
            inventory.setItem(gridSlot(row, col), tagged(symbol.icon(blur, false)));
        }
    }

    private static int floorMod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    private void finish(Player player, Automate automate, AutomateHolder holder,
                        Inventory inventory, SlotSymbol[][] grid, long bet) {
        UUID uuid = player.getUniqueId();
        plugin.getActiveBets().release(uuid);
        holder.setSpinning(false);
        automate.setBusy(false);

        Result result = evaluate(grid);
        double multiplier = Math.min(result.multiplier, plugin.getAutomateSettings().getMaxMultiplier());
        long win = multiplier > 0.0D ? Math.max(1L, Math.round(bet * multiplier)) : 0L;

        if (!player.isOnline()) {
            plugin.getDataManager().savePending(uuid, win, bet, result.label);
            return;
        }
        if (win > 0L) {
            plugin.getEconomy().depositPlayer(player, win);
            plugin.send(player, "automate-win",
                    "%player%", player.getName(),
                    "%amount%", String.valueOf(win),
                    "%combo%", result.label,
                    "%bet%", String.valueOf(bet));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.3F);
        } else {
            plugin.send(player, "automate-lose", "%bet%", String.valueOf(bet));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9F, 1.0F);
        }
        if (!inventory.getViewers().isEmpty()) {
            showResult(inventory, grid, result, win, multiplier);
            inventory.setItem(PLAY_SLOT, playItem(bet));
        }
    }

    private SlotSymbol[][] createGrid(ThreadLocalRandom rng) {
        SlotSymbol[][] grid = emptyGrid();

        int roll = rng.nextInt(1000);
        SlotSymbol symbol;
        int lines;
        if (roll < 110) {
            symbol = symbolAt(0);
            lines = 1;
        } else if (roll < 125) {
            symbol = symbolAt(0);
            lines = 2;
        } else if (roll < 190) {
            symbol = symbolAt(1);
            lines = 1;
        } else if (roll < 235) {
            symbol = symbolAt(2);
            lines = 1;
        } else if (roll < 265) {
            symbol = symbolAt(3);
            lines = 1;
        } else if (roll < 283) {
            symbol = symbolAt(4);
            lines = 1;
        } else if (roll < 294) {
            symbol = symbolAt(5);
            lines = 1;
        } else if (roll < 301) {
            symbol = symbolAt(6);
            lines = 1;
        } else if (roll < 305) {
            symbol = symbolAt(7);
            lines = 1;
        } else if (roll < 308) {
            symbol = symbolAt(8);
            lines = 1;
        } else if (roll < 310) {
            symbol = symbolAt(9);
            lines = 1;
        } else {
            return grid;
        }

        boolean[] usedLines = new boolean[12];
        boolean[][] protectedCells = new boolean[SIZE][SIZE];
        int planted = 0;
        while (planted < lines) {
            int pick = rng.nextInt(12);
            if (usedLines[pick]) {
                continue;
            }
            usedLines[pick] = true;
            paintLine(grid, symbol, pick, protectedCells);
            planted++;
        }
        sanitizeAccidentalLines(grid, usedLines, protectedCells);
        return grid;
    }

    private SlotSymbol[][] emptyGrid() {
        SlotSymbol[][] grid = new SlotSymbol[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                grid[row][col] = randomSymbol();
            }
        }
        sanitizeAccidentalLines(grid, new boolean[12], new boolean[SIZE][SIZE]);
        return grid;
    }

    private void sanitizeAccidentalLines(SlotSymbol[][] grid, boolean[] allowedLines, boolean[][] protectedCells) {
        for (int guard = 0; guard < 60; guard++) {
            boolean fixed = false;
            for (int lineId = 0; lineId < 12; lineId++) {
                if (allowedLines[lineId]) {
                    continue;
                }
                int[] coords = lineCellCoords(lineId);
                if (isSameSymbolLine(grid, coords)) {
                    for (int i = 0; i < SIZE; i++) {
                        int r = coords[i * 2];
                        int c = coords[i * 2 + 1];
                        if (!protectedCells[r][c]) {
                            grid[r][c] = otherThan(grid[r][c]);
                            fixed = true;
                            break;
                        }
                    }
                }
            }
            if (!fixed) {
                break;
            }
        }
    }

    private boolean isSameSymbolLine(SlotSymbol[][] grid, int[] coords) {
        String id = grid[coords[0]][coords[1]].getId();
        for (int i = 1; i < SIZE; i++) {
            if (!grid[coords[i * 2]][coords[i * 2 + 1]].getId().equals(id)) {
                return false;
            }
        }
        return true;
    }

    private int[] lineCellCoords(int id) {
        int[] out = new int[SIZE * 2];
        if (id < 5) {
            for (int col = 0; col < SIZE; col++) {
                out[col * 2] = id;
                out[col * 2 + 1] = col;
            }
            return out;
        }
        if (id < 10) {
            int col = id - 5;
            for (int row = 0; row < SIZE; row++) {
                out[row * 2] = row;
                out[row * 2 + 1] = col;
            }
            return out;
        }
        if (id == 10) {
            for (int i = 0; i < SIZE; i++) {
                out[i * 2] = i;
                out[i * 2 + 1] = i;
            }
            return out;
        }
        for (int i = 0; i < SIZE; i++) {
            out[i * 2] = i;
            out[i * 2 + 1] = SIZE - 1 - i;
        }
        return out;
    }

    private SlotSymbol otherThan(SlotSymbol current) {
        List<SlotSymbol> symbols = plugin.getAutomateSettings().getSymbols();
        for (SlotSymbol symbol : symbols) {
            if (!symbol.getId().equals(current.getId())) {
                return symbol;
            }
        }
        return current;
    }

    private void paintLine(SlotSymbol[][] grid, SlotSymbol symbol, int id, boolean[][] protectedCells) {
        int[] coords = lineCellCoords(id);
        for (int i = 0; i < SIZE; i++) {
            int r = coords[i * 2];
            int c = coords[i * 2 + 1];
            grid[r][c] = symbol;
            protectedCells[r][c] = true;
        }
    }

    private Result evaluate(SlotSymbol[][] grid) {
        Result result = new Result();
        int count = 0;
        for (int row = 0; row < SIZE; row++) {
            if (sameLine(grid, row, 0, 0, 1)) {
                addLine(result, grid[row][0], "ряд", row, 0, 0, 1);
                count++;
            }
        }
        for (int col = 0; col < SIZE; col++) {
            if (sameLine(grid, 0, col, 1, 0)) {
                addLine(result, grid[0][col], "столбец", 0, col, 1, 0);
                count++;
            }
        }
        if (sameLine(grid, 0, 0, 1, 1)) {
            addLine(result, grid[0][0], "крест", 0, 0, 1, 1);
            count++;
        }
        if (sameLine(grid, 0, SIZE - 1, 1, -1)) {
            addLine(result, grid[0][SIZE - 1], "крест", 0, SIZE - 1, 1, -1);
            count++;
        }
        if (count > 1) {
            result.label = result.label + " &8| &#F8BEFB" + count + " линии";
        }
        return result;
    }

    private boolean sameLine(SlotSymbol[][] grid, int row, int col, int dRow, int dCol) {
        String id = grid[row][col].getId();
        for (int i = 1; i < SIZE; i++) {
            if (!grid[row + dRow * i][col + dCol * i].getId().equals(id)) {
                return false;
            }
        }
        return true;
    }

    private void addLine(Result result, SlotSymbol symbol, String name, int row, int col, int dRow, int dCol) {
        double pay = symbol.pay(5);
        result.multiplier += pay;
        if (result.label.isEmpty()) {
            result.label = symbol.getName() + " &8| &#F8BEFB" + name + " x" + Text.formatMultiplier(pay);
        } else {
            result.label += " &8+ &#F8BEFB" + name;
        }
        for (int i = 0; i < SIZE; i++) {
            result.mask[row + dRow * i][col + dCol * i] = true;
        }
    }

    private void showResult(Inventory inventory, SlotSymbol[][] grid, Result result, long win, double multiplier) {
        drawBoard(inventory, grid, result, win, multiplier, false);
        if (win <= 0L) {
            return;
        }
        new BukkitRunnable() {
            private int frame = 0;

            @Override
            public void run() {
                if (inventory.getViewers().isEmpty()) {
                    cancel();
                    return;
                }
                frame++;
                drawBoard(inventory, grid, result, win, multiplier, frame % 2 == 1);
                if (frame >= 10) {
                    drawBoard(inventory, grid, result, win, multiplier, false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 6L, 6L);
    }

    private void drawBoard(Inventory inventory, SlotSymbol[][] grid, Result result,
                           long win, double multiplier, boolean flash) {
        GuiConfig gui = plugin.getAutomateGuiConfig();
        Material glassMat = gui.getMaterial("win-glass", Material.LIME_STAINED_GLASS_PANE);
        ItemStack glass = named(glassMat, " ");
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (win > 0L && result.mask[row][col] && flash) {
                    inventory.setItem(gridSlot(row, col), glass);
                } else if (win > 0L && result.mask[row][col]) {
                    inventory.setItem(gridSlot(row, col), winIcon(grid[row][col], win, multiplier, result.label));
                } else {
                    inventory.setItem(gridSlot(row, col), tagged(grid[row][col].icon()));
                }
            }
        }
    }

    private ItemStack winIcon(SlotSymbol symbol, long win, double multiplier, String label) {
        ItemStack item = tagged(symbol.icon(false, true));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&#F8BEFB&l┃ &fВыигрыш: &a" + win + " монет"));
            lore.add(Text.color("&#F8BEFB&l┃ &fМножитель: &#F8BEFBx" + Text.formatMultiplier(multiplier)));
            lore.add(Text.color("&#F8BEFB&l┃ "));
            lore.add(Text.color("&7● &f" + label));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private static final class Result {
        private double multiplier;
        private String label = "";
        private final boolean[][] mask = new boolean[SIZE][SIZE];
    }
}
