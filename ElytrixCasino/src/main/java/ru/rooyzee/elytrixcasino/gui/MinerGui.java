package ru.rooyzee.elytrixcasino.gui;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
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
import ru.rooyzee.elytrixcasino.bet.GameType;
import ru.rooyzee.elytrixcasino.miner.Miner;
import ru.rooyzee.elytrixcasino.miner.MinerSession;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinerGui implements Listener {

    private static final int CLOSE_SLOT = 45;
    private static final int CHANGE_BET_SLOT = 47;
    private static final int ACTION_SLOT = 49;

    private final Main plugin;
    private final GuiConfig config;
    private final Map<UUID, MinerSession> sessions = new ConcurrentHashMap<>();

    public MinerGui(Main plugin) {
        this.plugin = plugin;
        this.config = new GuiConfig(plugin, "miner.yml");
    }

    public void reload() {
        config.reload();
    }

    public boolean hasActiveSession(UUID playerId) {
        MinerSession session = sessions.get(playerId);
        return session != null && !session.isFinished();
    }

    public void open(Player player, Miner miner) {
        UUID uuid = player.getUniqueId();
        MinerSession active = sessions.get(uuid);
        if (active != null && !active.isFinished()) {
            resumeBoard(player, active);
            return;
        }
        openBetMenu(player, miner.getId());
    }

    public void openBetMenu(Player player, String minerId) {
        int size = 54;
        MinerBetHolder holder = new MinerBetHolder(minerId);
        String title = Text.color(config.getString("title", "&#F8BEFB&lМинёр"));
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        ItemStack closeButton = buildCloseButton();
        Map<Integer, ItemStack> buttons = new LinkedHashMap<>();

        ConfigurationSection betsSection = config.raw().getConfigurationSection("bets");
        if (betsSection != null) {
            for (String key : betsSection.getKeys(false)) {
                String base = "bets." + key;
                int slot = config.getInt(base + ".slot", -1);
                long amount = config.raw().getLong(base + ".amount", 0L);
                if (slot < 0 || slot >= size || amount <= 0L) {
                    continue;
                }
                Material mat = config.getMaterial(base + ".material", Material.GOLD_INGOT);
                boolean glow = config.getBoolean(base + ".glow", false);
                String formatted = String.valueOf(amount);
                String name = config.getString(base + ".name", "&7« &#F8BEFBСтавка %amount% &7»")
                        .replace("%amount%", formatted);
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList(base + ".lore")) {
                    lore.add(line.replace("%amount%", formatted)
                            .replace("%mines%", String.valueOf(plugin.getMinerSettings().getMinesCount())));
                }
                buttons.put(slot, buildItem(mat, name, lore, glow));
                holder.getBets().put(slot, amount);
            }
        }

        plugin.getAnimator().openStandardMenu(player, inv, closeButton, null, buttons);
    }

    private void startNewGame(Player player, String minerId, long bet) {
        UUID uuid = player.getUniqueId();
        ActiveBet existing = plugin.getActiveBets().getActiveBet(uuid);
        if (existing != null) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", existing.getGameType().getDisplayName(),
                    "%amount%", String.valueOf(existing.getAmount()));
            playSound(player, Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        Economy economy = plugin.getEconomy();
        if (!economy.has(player, bet)) {
            plugin.send(player, "no-money");
            playSound(player, Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        if (!plugin.getActiveBets().tryAcquire(uuid, GameType.MINER, minerId, bet)) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", GameType.MINER.getDisplayName(),
                    "%amount%", String.valueOf(bet));
            playSound(player, Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        EconomyResponse withdraw = economy.withdrawPlayer(player, bet);
        if (!withdraw.transactionSuccess()) {
            plugin.getActiveBets().release(uuid);
            plugin.send(player, "no-money");
            playSound(player, Sound.ENTITY_VILLAGER_NO, 0.85F, 1.0F);
            return;
        }

        MinerSession session = MinerSession.generate(uuid, minerId, bet, plugin.getMinerSettings());
        sessions.put(uuid, session);

        plugin.send(player, "miner-started",
                "%amount%", String.valueOf(bet),
                "%mines%", String.valueOf(session.getTotalMines()));

        MinerGameHolder holder = new MinerGameHolder(session);
        String title = Text.color(config.getString("game.title", "&#F8BEFB&lМинёр")
                .replace("%amount%", String.valueOf(bet)));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack hiddenCell = buildHiddenCell(bet);
        Map<Integer, ItemStack> bottomControls = buildBottomControls(session);
        plugin.getAnimator().openMinerBoard(player, inv, hiddenCell, bottomControls);
    }

    private void resumeBoard(Player player, MinerSession session) {
        MinerGameHolder holder = new MinerGameHolder(session);
        String title = Text.color(config.getString("game.title", "&#F8BEFB&lМинёр")
                .replace("%amount%", String.valueOf(session.getBet())));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack hidden = buildHiddenCell(session.getBet());
        for (int slot = 0; slot < session.size(); slot++) {
            if (session.isRevealed(slot)) {
                inv.setItem(slot, buildRevealedCellItem(session.getCell(slot), false));
            } else {
                inv.setItem(slot, hidden.clone());
            }
        }

        ItemStack purple = GuiGuard.pane(plugin, Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 46; slot <= 53; slot++) {
            inv.setItem(slot, purple);
        }
        Map<Integer, ItemStack> controls = buildBottomControls(session);
        for (Map.Entry<Integer, ItemStack> entry : controls.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue());
        }

        plugin.getAnimator().stop(player.getUniqueId());
        player.openInventory(inv);
        playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.35F);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MinerBetHolder) {
            handleBetMenuClick(event, (MinerBetHolder) top.getHolder(), top);
            return;
        }
        if (top.getHolder() instanceof MinerGameHolder) {
            handleGameBoardClick(event, (MinerGameHolder) top.getHolder(), top);
        }
    }

    private void handleBetMenuClick(InventoryClickEvent event, MinerBetHolder holder, Inventory top) {
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        Player player = (Player) who;
        if (event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            plugin.getAnimator().stop(player.getUniqueId());
            player.closeInventory();
            return;
        }

        Long bet = holder.getBets().get(slot);
        if (bet == null) {
            return;
        }
        plugin.getAnimator().stop(player.getUniqueId());
        startNewGame(player, holder.getMinerId(), bet);
    }

    private void handleGameBoardClick(InventoryClickEvent event, MinerGameHolder holder, Inventory top) {
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        Player player = (Player) who;
        if (event.getClickedInventory() != top) {
            return;
        }

        MinerSession session = holder.getSession();
        int slot = event.getRawSlot();

        if (session.isFinished()) {
            if (slot == CLOSE_SLOT) {
                player.closeInventory();
            } else if (slot == ACTION_SLOT) {
                startNewGame(player, session.getMinerId(), session.getBet());
            } else if (slot == CHANGE_BET_SLOT) {
                openBetMenu(player, session.getMinerId());
            }
            return;
        }

        if (slot == CLOSE_SLOT) {
            if (session.getCollectedCoins() > 0L) {
                completeCashout(player, session, top, true);
            } else {
                plugin.getAnimator().stop(player.getUniqueId());
                player.closeInventory();
                plugin.send(player, "miner-paused");
            }
            return;
        }

        if (slot == ACTION_SLOT) {
            if (session.getCollectedCoins() <= 0L) {
                plugin.send(player, "miner-nothing-to-take");
                playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.85F);
                return;
            }
            completeCashout(player, session, top, false);
            return;
        }

        if (slot < 0 || slot >= session.size() || session.isRevealed(slot)) {
            return;
        }

        MinerSession.Cell cell = session.reveal(slot);
        if (cell == null) {
            return;
        }

        if (cell.getType() == MinerSession.CellType.MINE) {
            sessions.remove(session.getPlayerId());
            plugin.getActiveBets().release(session.getPlayerId());

            revealEntireBoard(top, session, slot);
            refreshFinishedControls(top, session, false, 0L);

            plugin.send(player, "miner-exploded",
                    "%amount%", String.valueOf(session.getBet()));
            playSound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.75F, 1.05F);
            return;
        }

        top.setItem(slot, buildRevealedCellItem(cell, false));
        updateLiveControls(top, session);

        if (cell.getType() == MinerSession.CellType.COINS) {
            playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75F, 1.35F);
            if (session.allCoinCellsFound()) {
                completeCashout(player, session, top, false);
            }
        } else {
            playSound(player, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.85F, 1.35F);
        }
    }

    private void completeCashout(Player player, MinerSession session, Inventory top, boolean closeAfter) {
        if (!session.markFinished()) {
            return;
        }
        long payout = session.getCollectedCoins();
        sessions.remove(session.getPlayerId());
        plugin.getActiveBets().release(session.getPlayerId());

        if (payout > 0L) {
            plugin.getEconomy().depositPlayer(player, payout);
        }
        plugin.send(player, "miner-win",
                "%amount%", String.valueOf(payout),
                "%bet%", String.valueOf(session.getBet()));
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.85F, 1.25F);

        if (closeAfter) {
            player.closeInventory();
            return;
        }
        revealEntireBoard(top, session, -1);
        refreshFinishedControls(top, session, true, payout);
    }

    private void revealEntireBoard(Inventory inv, MinerSession session, int explodedSlot) {
        for (int i = 0; i < session.size(); i++) {
            MinerSession.Cell cell = session.getCell(i);
            if (i == explodedSlot) {
                inv.setItem(i, buildItem(Material.TNT, "&cМина", Collections.emptyList(), true));
            } else if (session.isRevealed(i)) {
                inv.setItem(i, buildRevealedCellItem(cell, false));
            } else {
                inv.setItem(i, buildRevealedCellItem(cell, true));
            }
        }
    }

    private void updateLiveControls(Inventory inv, MinerSession session) {
        Map<Integer, ItemStack> controls = buildBottomControls(session);
        for (Map.Entry<Integer, ItemStack> entry : controls.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue());
        }
    }

    private void refreshFinishedControls(Inventory inv, MinerSession session, boolean won, long payout) {
        inv.setItem(CLOSE_SLOT, buildCloseButton());

        List<String> changeBetLore = new ArrayList<>();
        changeBetLore.add("&#F8BEFB&l┃ &fСтавка: &#F8BEFB" + session.getBet() + " монет");
        changeBetLore.add("&#F8BEFB&l┃ ");
        changeBetLore.add("&7● &fНажмите для выбора ставки");
        inv.setItem(CHANGE_BET_SLOT, buildItem(
                Material.GOLD_INGOT,
                "&7« &#F8BEFBСменить ставку &7»",
                changeBetLore,
                false
        ));

        List<String> lore = new ArrayList<>();
        lore.add("&#F8BEFB&l┃ ");
        if (won) {
            lore.add("&#F8BEFB&l┃ &fВыигрыш: &a" + payout + " монет");
        } else {
            lore.add("&#F8BEFB&l┃ &fРезультат: &cмина");
        }
        lore.add("&#F8BEFB&l┃ &fСтавка: &#F8BEFB" + session.getBet() + " монет");
        lore.add("&#F8BEFB&l┃ ");
        lore.add("&7● &fНажмите, чтобы играть");
        inv.setItem(ACTION_SLOT, buildItem(
                won ? Material.PINK_DYE : Material.TNT,
                "&7« &#F8BEFBИграть ещё &7»",
                lore,
                true
        ));
    }

    private Map<Integer, ItemStack> buildBottomControls(MinerSession session) {
        Map<Integer, ItemStack> controls = new LinkedHashMap<>();
        long collected = session.getCollectedCoins();
        controls.put(CLOSE_SLOT, buildCloseButton());

        if (collected > 0L) {
            List<String> cashLore = new ArrayList<>();
            cashLore.add("&#F8BEFB&l┃ ");
            cashLore.add("&#F8BEFB&l┃ &fНаграда: &a" + collected + " монет");
            cashLore.add("&#F8BEFB&l┃ &fСтавка: &#F8BEFB" + session.getBet() + " монет");
            cashLore.add("&#F8BEFB&l┃ ");
            cashLore.add("&7● &fНажмите, чтобы забрать");
            controls.put(ACTION_SLOT, buildItem(
                    Material.LIME_DYE,
                    "&7« &aЗабрать " + collected + " монет &7»",
                    cashLore,
                    true
            ));
        } else {
            List<String> waitLore = new ArrayList<>();
            waitLore.add("&#F8BEFB&l┃ ");
            waitLore.add("&#F8BEFB&l┃ &fСтавка: &#F8BEFB" + session.getBet() + " монет");
            waitLore.add("&#F8BEFB&l┃ ");
            waitLore.add("&7● &fОткройте клетку с монетами");
            controls.put(ACTION_SLOT, buildItem(
                    Material.GRAY_DYE,
                    "&7« &fЗабрать &7»",
                    waitLore,
                    false
            ));
        }
        return controls;
    }

    private ItemStack buildHiddenCell(long bet) {
        String name = config.getString("game.hidden-cell.name", "&7Клетка");
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("game.hidden-cell.lore")) {
            lore.add(line.replace("%bet%", String.valueOf(bet))
                    .replace("%mines%", String.valueOf(plugin.getMinerSettings().getMinesCount())));
        }
        return buildItem(Material.GRAY_STAINED_GLASS_PANE, name, lore, false);
    }

    private ItemStack buildRevealedCellItem(MinerSession.Cell cell, boolean postGamePreview) {
        if (cell.getType() == MinerSession.CellType.MINE) {
            return buildItem(Material.TNT, "&cМина", Collections.emptyList(), false);
        }
        if (cell.getType() == MinerSession.CellType.COINS) {
            List<String> lore = new ArrayList<>();
            lore.add("&#F8BEFB&l┃ &fНаграда: &a" + cell.getReward() + " монет");
            return buildItem(cell.getIcon(), cell.getTitle(), lore, !postGamePreview);
        }
        return buildItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Пусто", Collections.emptyList(), false);
    }

    private ItemStack buildCloseButton() {
        Material mat = config.getMaterial("close.material", Material.RED_DYE);
        String name = config.getString("close.name", "&7« &cЗакрыть меню &7»");
        List<String> lore = config.getStringList("close.lore");
        return buildItem(mat, name, lore, false);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof MinerBetHolder || top.getHolder() instanceof MinerGameHolder) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof MinerBetHolder || inv.getHolder() instanceof MinerGameHolder) {
            plugin.getAnimator().stop(event.getPlayer().getUniqueId());
        }
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getAnimator().stop(uuid);
        MinerSession session = sessions.remove(uuid);
        if (session != null && session.markFinished()) {
            long earned = session.getCollectedCoins();
            if (earned > 0L) {
                plugin.getEconomy().depositPlayer(player, earned);
            }
            plugin.getActiveBets().release(uuid);
        }
    }

    public void shutdown() {
        Economy economy = plugin.getEconomy();
        for (Map.Entry<UUID, MinerSession> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            MinerSession session = entry.getValue();
            if (session != null && session.markFinished() && economy != null) {
                long refund = Math.max(session.getBet(), session.getCollectedCoins());
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                economy.depositPlayer(offline, refund);
            }
            plugin.getActiveBets().release(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                Inventory top = online.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof MinerBetHolder || top.getHolder() instanceof MinerGameHolder) {
                    online.closeInventory();
                }
            }
        }
        sessions.clear();
    }

    private ItemStack buildItem(Material material, String rawName, List<String> rawLore, boolean glow) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(rawName));
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> colored = new ArrayList<>(rawLore.size());
                for (String line : rawLore) {
                    colored.add(Text.color(line));
                }
                meta.setLore(colored);
            }
            if (glow) {
                GuiGuard.applyGlow(meta);
            }
            meta.addItemFlags(ItemFlag.values());
            GuiGuard.markMeta(plugin, meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
        }
    }
}
