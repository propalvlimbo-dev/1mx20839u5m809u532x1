package ru.rooyzee.elytrixcasino.gui;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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
    private static final int CLOSE_SLOT = 45;
    private static final int PLAY_SLOT = 49;
    private static final int PAYTABLE_SLOT = 53;

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
                    "%amount%", Text.formatMoney(pending.win),
                    "%combo%", pending.combo,
                    "%bet%", Text.formatMoney(pending.bet));
        } else {
            plugin.send(player, "automate-lose", "%bet%", Text.formatMoney(pending.bet));
        }
    }

    public void open(Player player, Automate automate) {
        GuiConfig gui = plugin.getAutomateGuiConfig();
        AutomateSettings settings = plugin.getAutomateSettings();
        AutomateHolder holder = new AutomateHolder(automate.getId());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.color(gui.getString("title", "&#F8BEFB&lАВТОМАТ &8┃ &f%bet% ⛂")
                        .replace("%bet%", Text.formatMoney(automate.getBet()))));
        holder.setInventory(inventory);
        holder.setPlaySlot(PLAY_SLOT);

        Map<Integer, ItemStack> controls = new LinkedHashMap<>();
        controls.put(PLAY_SLOT, playItem(automate.getBet()));
        controls.put(PAYTABLE_SLOT, paytableItem(settings, automate.getBet()));

        plugin.getAnimator().openAutomateMenu(player, inventory, closeItem(), controls, () -> {
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    inventory.setItem(gridSlot(row, col), tagged(randomSymbol().icon()));
                }
            }
        });
    }

    private ItemStack paytableItem(AutomateSettings settings, long bet) {
        ItemStack item = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&#F8BEFB&lТаблица множителей"));
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&#F8BEFB&l┃ &fСтавка за спин: &#F8BEFB" + Text.formatMoney(bet) + " ⛂"));
            lore.add(Text.color("&#F8BEFB&l┃ "));
            for (SlotSymbol symbol : settings.getSymbols()) {
                long payout = Math.round(bet * symbol.pay(5));
                lore.add(Text.color("&#F8BEFB&l┃ " + symbol.getName()
                        + " &8— &#F8BEFBx" + Text.formatMultiplier(symbol.pay(5))
                        + " &8(&f" + Text.formatMoney(payout) + " ⛂&8)"));
            }
            lore.add(Text.color("&#F8BEFB&l┃ "));
            lore.add(Text.color("&7● &fРяд, столбец или крест из 5 символов"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return GuiGuard.mark(plugin, item);
    }

    private ItemStack playItem(long bet) {
        GuiConfig gui = plugin.getAutomateGuiConfig();
        ItemStack item = new ItemStack(gui.getMaterial("play-item", Material.AMETHYST_SHARD), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String formattedBet = Text.formatMoney(bet);
            meta.setDisplayName(Text.color(gui.getString("play-name", "&#F8BEFB&lКРУТИТЬ БАРАБАН")));
            List<String> lore = new ArrayList<>();
            for (String line : gui.getStringList("play-lore")) {
                lore.add(Text.color(line.replace("%bet%", formattedBet)));
            }
            GuiGuard.applyGlow(meta);
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
            meta.setDisplayName(Text.color(gui.getString("close-name", "&cЗакрыть")));
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
        if (slot == CLOSE_SLOT) {
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
                    "%amount%", Text.formatMoney(existing.getAmount()));
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
                    "%amount%", Text.formatMoney(bet));
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
        plugin.send(player, "bet-placed", "%bet%", Text.formatMoney(bet));
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
                    "%amount%", Text.formatMoney(win),
                    "%combo%", result.label,
                    "%bet%", Text.formatMoney(bet));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.3F);
        } else {
            plugin.send(player, "automate-lose", "%bet%", Text.formatMoney(bet));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9F, 0.55F);
        }
        if (!inventory.getViewers().isEmpty()) {
            showResult(inventory, grid, result, win, multiplier);
            inventory.setItem(PLAY_SLOT, playItem(bet));
        }
    }

    /**
     * Честная независимая генерация сетки 5x5 без Heat/абуза твинков и смены ставок:
     * суммарный RTP = ~67.7% (стабильный доход казино +32.3%), частота выигрыша = 31.0%.
     */
    private SlotSymbol[][] createGrid(ThreadLocalRandom rng) {
        SlotSymbol[][] grid = emptyGrid();
        SlotSymbol fallback = plugin.getAutomateSettings().getSymbols().get(0);
        SlotSymbol flesh = byId("flesh", fallback);

        int roll = rng.nextInt(1000);
        SlotSymbol symbol;
        int lines;
        if (roll < 110) {
            symbol = flesh;
            lines = 1;
        } else if (roll < 125) {
            symbol = flesh;
            lines = 2;
        } else if (roll < 190) {
            symbol = byId("potato", flesh);
            lines = 1;
        } else if (roll < 235) {
            symbol = byId("eye", flesh);
            lines = 1;
        } else if (roll < 265) {
            symbol = byId("bread", flesh);
            lines = 1;
        } else if (roll < 283) {
            symbol = byId("beef", flesh);
            lines = 1;
        } else if (roll < 294) {
            symbol = byId("carrot", flesh);
            lines = 1;
        } else if (roll < 301) {
            symbol = byId("apple", flesh);
            lines = 1;
        } else if (roll < 305) {
            symbol = byId("gapple", flesh);
            lines = 1;
        } else if (roll < 308) {
            symbol = byId("pie", flesh);
            lines = 1;
        } else if (roll < 310) {
            symbol = byId("cake", flesh);
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

    private SlotSymbol byId(String id, SlotSymbol fallback) {
        for (SlotSymbol symbol : plugin.getAutomateSettings().getSymbols()) {
            if (symbol.getId().equals(id)) {
                return symbol;
            }
        }
        return fallback;
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
        ItemStack glass = named(Material.PURPLE_STAINED_GLASS_PANE, " ");
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
            lore.add(Text.color("&#F8BEFB&l┃ &fВыигрыш: &#55FF99+" + Text.formatMoney(win) + " ⛂"));
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
