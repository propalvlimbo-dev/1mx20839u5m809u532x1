package ru.rooyzee.elytrixcasino.gui;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.automate.Automate;
import ru.rooyzee.elytrixcasino.automate.AutomateSettings;
import ru.rooyzee.elytrixcasino.automate.SlotSymbol;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class AutomateGui implements Listener {

    private static final int SIZE = 5;
    private static final int REEL_LENGTH = 80;
    private static final int PLAY_SLOT = 49;
    private static final int CLOSE_LEFT = 45;
    private static final int CLOSE_RIGHT = 53;

    private final Main plugin;
    private final Map<UUID, Heat> heat = new HashMap<>();

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
                Text.color(gui.getString("title", "&#F8BEFBАвтомат")));
        holder.setInventory(inventory);
        holder.setPlaySlot(PLAY_SLOT);

        ItemStack filler = named(gui.getMaterial("filler", Material.BLACK_STAINED_GLASS_PANE), " ");
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                inventory.setItem(gridSlot(row, col), tagged(randomSymbol().icon()));
            }
        }
        fillCombos(inventory, settings);
        inventory.setItem(PLAY_SLOT, playItem(automate.getBet()));
        ItemStack close = closeItem();
        inventory.setItem(CLOSE_LEFT, close);
        inventory.setItem(CLOSE_RIGHT, close.clone());
        player.openInventory(inventory);
    }

    private void fillCombos(Inventory inventory, AutomateSettings settings) {
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
                item.setItemMeta(meta);
            }
            inventory.setItem(slots[index], GuiGuard.mark(plugin, item));
            index++;
        }
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
        event.setResult(org.bukkit.event.Event.Result.DENY);
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
            player.closeInventory();
            return;
        }
        if (slot != holder.getPlaySlot()) {
            return;
        }
        Automate automate = plugin.getAutomateManager().get(holder.getAutomateId());
        if (automate == null) {
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
            event.setResult(org.bukkit.event.Event.Result.DENY);
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
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        GuiGuard.strip(plugin, player);
    }

    private void startSpin(Player player, Automate automate, AutomateHolder holder, Inventory inventory) {
        if (automate.isBusy()) {
            plugin.send(player, "automate-busy");
            return;
        }
        long bet = automate.getBet();
        Economy economy = plugin.getEconomy();
        if (!economy.has(player, bet)) {
            plugin.send(player, "no-money");
            return;
        }
        EconomyResponse response = economy.withdrawPlayer(player, bet);
        if (!response.transactionSuccess()) {
            plugin.send(player, "no-money");
            return;
        }

        automate.setBusy(true);
        holder.setSpinning(true);
        plugin.send(player, "bet-placed", "%bet%", String.valueOf(bet));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.55F);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        SlotSymbol[][] grid = createGrid(rng, player, bet);
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
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.85F, 0.75F + col * 0.16F);
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
        Result result = evaluate(grid);
        double multiplier = Math.min(result.multiplier, plugin.getAutomateSettings().getMaxMultiplier());
        long win = multiplier > 0.0D ? Math.max(1L, Math.round(bet * multiplier)) : 0L;
        remember(player, bet, win);
        holder.setSpinning(false);
        automate.setBusy(false);
        if (!player.isOnline()) {
            plugin.getDataManager().savePending(player.getUniqueId(), win, bet, result.label);
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
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.5F);
        }
        if (!inventory.getViewers().isEmpty()) {
            showResult(inventory, grid, result, win, multiplier);
            inventory.setItem(PLAY_SLOT, playItem(bet));
        }
    }

    private SlotSymbol[][] createGrid(ThreadLocalRandom rng, Player player, long bet) {
        SlotSymbol[][] grid = emptyGrid();
        Heat session = heat.computeIfAbsent(player.getUniqueId(), id -> new Heat());
        SlotSymbol flesh = byId("flesh", plugin.getAutomateSettings().getSymbols().get(0));
        long target = Math.round(session.wagered * 0.78D);
        if (session.spins > 0 && session.paid > target + bet * 3L && rng.nextInt(100) < 72) {
            return grid;
        }
        if (session.spins > 0 && session.paid + bet * 2L < target && rng.nextInt(100) < 74) {
            paintLine(grid, pickCommon(rng, flesh), rng.nextInt(12));
            return grid;
        }
        if (session.spins < 4 && rng.nextInt(100) < 58) {
            paintLine(grid, pickCommon(rng, flesh), rng.nextInt(12));
            return grid;
        }
        int roll = rng.nextInt(1000);
        SlotSymbol symbol;
        int lines;
        if (roll < 140) {
            symbol = flesh;
            lines = 1;
        } else if (roll < 175) {
            symbol = flesh;
            lines = 2;
        } else if (roll < 182) {
            symbol = flesh;
            lines = 3;
        } else if (roll < 242) {
            symbol = byId("potato", flesh);
            lines = 1;
        } else if (roll < 292) {
            symbol = byId("eye", flesh);
            lines = 1;
        } else if (roll < 332) {
            symbol = byId("bread", flesh);
            lines = 1;
        } else if (roll < 357) {
            symbol = byId("beef", flesh);
            lines = 1;
        } else if (roll < 375) {
            symbol = byId("carrot", flesh);
            lines = 1;
        } else if (roll < 388) {
            symbol = byId("apple", flesh);
            lines = 1;
        } else if (roll < 396) {
            symbol = byId("gapple", flesh);
            lines = 1;
        } else if (roll < 402) {
            symbol = byId("pie", flesh);
            lines = 1;
        } else if (roll < 405) {
            symbol = byId("cake", flesh);
            lines = 1;
        } else {
            return grid;
        }
        boolean[] used = new boolean[12];
        int planted = 0;
        while (planted < lines) {
            int pick = rng.nextInt(12);
            if (used[pick]) {
                continue;
            }
            used[pick] = true;
            paintLine(grid, symbol, pick);
            planted++;
        }
        return grid;
    }

    private SlotSymbol[][] emptyGrid() {
        SlotSymbol[][] grid = new SlotSymbol[SIZE][SIZE];
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                grid[row][col] = randomSymbol();
            }
        }
        for (int guard = 0; guard < 40; guard++) {
            boolean hit = false;
            for (int row = 0; row < SIZE; row++) {
                if (sameLine(grid, row, 0, 0, 1)) {
                    grid[row][2] = otherThan(grid[row][2]);
                    hit = true;
                }
            }
            for (int col = 0; col < SIZE; col++) {
                if (sameLine(grid, 0, col, 1, 0)) {
                    grid[2][col] = otherThan(grid[2][col]);
                    hit = true;
                }
            }
            if (sameLine(grid, 0, 0, 1, 1)) {
                grid[2][2] = otherThan(grid[2][2]);
                hit = true;
            }
            if (sameLine(grid, 0, SIZE - 1, 1, -1)) {
                grid[2][2] = otherThan(grid[2][2]);
                hit = true;
            }
            if (!hit) {
                break;
            }
        }
        return grid;
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

    private SlotSymbol pickCommon(ThreadLocalRandom rng, SlotSymbol flesh) {
        int pick = rng.nextInt(100);
        if (pick < 40) {
            return flesh;
        }
        if (pick < 62) {
            return byId("potato", flesh);
        }
        if (pick < 78) {
            return byId("eye", flesh);
        }
        if (pick < 90) {
            return byId("bread", flesh);
        }
        return byId("beef", flesh);
    }

    private SlotSymbol byId(String id, SlotSymbol fallback) {
        for (SlotSymbol symbol : plugin.getAutomateSettings().getSymbols()) {
            if (symbol.getId().equals(id)) {
                return symbol;
            }
        }
        return fallback;
    }

    private void paintLine(SlotSymbol[][] grid, SlotSymbol symbol, int id) {
        if (id < 5) {
            for (int col = 0; col < SIZE; col++) {
                grid[id][col] = symbol;
            }
            return;
        }
        if (id < 10) {
            int col = id - 5;
            for (int row = 0; row < SIZE; row++) {
                grid[row][col] = symbol;
            }
            return;
        }
        if (id == 10) {
            for (int i = 0; i < SIZE; i++) {
                grid[i][i] = symbol;
            }
            return;
        }
        for (int i = 0; i < SIZE; i++) {
            grid[i][SIZE - 1 - i] = symbol;
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
        ItemStack glass = named(Material.PINK_STAINED_GLASS_PANE, " ");
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

    private void remember(Player player, long bet, long win) {
        Heat session = heat.computeIfAbsent(player.getUniqueId(), id -> new Heat());
        session.spins++;
        session.wagered += bet;
        session.paid += win;
        if (win > 0L) {
            session.winsInRow++;
        } else {
            session.winsInRow = 0;
        }
    }

    private static final class Heat {
        private int spins;
        private int winsInRow;
        private long wagered;
        private long paid;
    }

    private static final class Result {
        private double multiplier;
        private String label = "";
        private final boolean[][] mask = new boolean[SIZE][SIZE];
    }
}
