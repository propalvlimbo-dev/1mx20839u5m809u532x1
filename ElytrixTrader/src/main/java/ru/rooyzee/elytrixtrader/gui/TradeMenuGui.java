package ru.rooyzee.elytrixtrader.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.config.GuiConfig;
import ru.rooyzee.elytrixtrader.model.IconConfig;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Numbers;
import ru.rooyzee.elytrixtrader.util.Sounds;
import ru.rooyzee.elytrixtrader.util.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TradeMenuGui {

    private final Main plugin;
    private final Map<UUID, MenuHolder> opened = new HashMap<>();
    private final Map<UUID, TraderInstance> active = new HashMap<>();
    private final Map<UUID, BukkitTask> animationTasks = new HashMap<>();

    public TradeMenuGui(Main plugin) {
        this.plugin = plugin;
    }

    public void interact(Player player, TraderInstance instance) {
        if (player == null || instance == null || !instance.alive()) {
            return;
        }
        if (!player.hasPermission("elytrixtrader.trade")) {
            plugin.messages().send(player, "general.no-permission", player, null);
            return;
        }
        if (!instance.inRange(player)) {
            Map<String, String> map = new HashMap<>();
            map.put("distance", Numbers.amount((long) Math.ceil(instance.distance(player))));
            map.put("radius", String.valueOf((int) Math.ceil(plugin.config().interactionRadius())));
            plugin.messages().send(player, "interaction.too-far", player, map);
            return;
        }
        open(player, instance, 0);
    }

    public void open(Player player, TraderInstance instance, int page) {
        if (player == null || instance == null || !instance.alive()) return;
        UUID playerId = player.getUniqueId();
        cancelAnimation(playerId);
        GuiConfig gui = plugin.gui();
        List<TradeConfig> trades = available(player, instance);
        active.put(playerId, instance);
        // Если BMenu установлен и интеграция включена — используем его; иначе штатное меню.
        if (plugin.bmenu() != null && plugin.bmenu().available()
                && plugin.bmenu().open(player, instance, trades)) {
            return;
        }
        int capacity = Math.max(1, gui.pageSize());
        int pages = Math.max(1, (int) Math.ceil(trades.size() / (double) capacity));
        int safePage = Math.max(0, Math.min(pages - 1, page));
        MenuHolder holder = new MenuHolder(instance, safePage, pages);
        Map<String, String> base = basePlaceholders(player, instance, safePage, pages);
        Inventory inventory = Bukkit.createInventory(holder, gui.size(),
                ColorUtil.translate(Text.apply(gui.title(), player, base)));
        holder.setInventory(inventory);
        opened.put(playerId, holder);
        prepareInventory(inventory, player, base);
        player.openInventory(inventory);
        if (gui.animationEnabled()) {
            animateOpen(player, holder, trades, base);
        } else {
            Sounds.play(player, "BLOCK_ENCHANTMENT_TABLE_USE", 0.28F, 1.4F);
            fill(inventory, holder, player, trades);
        }
    }

    private void prepareInventory(Inventory inventory, Player player, Map<String, String> base) {
        inventory.clear();
        GuiConfig gui = plugin.gui();
        if (gui.filler().material() != null && !gui.filler().material().isAir()) {
            ItemStack filler = IconFactory.build(gui.filler(), player, base, 1);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler == null ? null : filler.clone());
            }
        }
    }

    private Map<Integer, ItemStack> buildDecorations(Inventory inventory, Player player, Map<String, String> base) {
        Map<Integer, ItemStack> decorations = new LinkedHashMap<>();
        for (GuiConfig.Decoration decoration : plugin.gui().decorations()) {
            ItemStack item = IconFactory.build(decoration.icon(), player, base, 1);
            if (item == null) continue;
            for (int slot : decoration.slots()) {
                if (slot >= 0 && slot < inventory.getSize()) decorations.put(slot, item.clone());
            }
        }
        return decorations;
    }

    private static final class TradeSlotItem {
        private final int slot;
        private final TradeConfig trade;
        private final ItemStack icon;

        private TradeSlotItem(int slot, TradeConfig trade, ItemStack icon) {
            this.slot = slot;
            this.trade = trade;
            this.icon = icon;
        }
    }

    private List<TradeSlotItem> visibleTradeItems(Inventory inventory, MenuHolder holder, Player player,
                                                   List<TradeConfig> trades, Map<String, String> base) {
        GuiConfig gui = plugin.gui();
        List<TradeSlotItem> visible = new ArrayList<>();
        int index = holder.page() * gui.pageSize();
        List<Integer> slots = gui.tradeSlots();
        if (!slots.isEmpty()) {
            for (int slot : slots) {
                if (index >= trades.size()) break;
                TradeConfig trade = trades.get(index++);
                visible.add(new TradeSlotItem(slot, trade, tradeIcon(player, holder.instance(), trade, base)));
            }
        } else {
            int end = Math.min(gui.endSlot(), inventory.getSize() - 1);
            for (int slot = gui.startSlot(); slot <= end && index < trades.size(); slot++) {
                TradeConfig trade = trades.get(index++);
                visible.add(new TradeSlotItem(slot, trade, tradeIcon(player, holder.instance(), trade, base)));
            }
        }
        return visible;
    }

    private void fill(Inventory inventory, MenuHolder holder, Player player, List<TradeConfig> trades) {
        GuiConfig gui = plugin.gui();
        Map<String, String> base = basePlaceholders(player, holder.instance(), holder.page(), holder.pages());
        holder.clearMappings();
        prepareInventory(inventory, player, base);
        for (Map.Entry<Integer, ItemStack> entry : buildDecorations(inventory, player, base).entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
        List<TradeSlotItem> visible = visibleTradeItems(inventory, holder, player, trades, base);
        if (visible.isEmpty()) {
            inventory.setItem(emptyOfferSlot(gui, inventory), emptyOfferIcon(holder.instance()));
        } else {
            for (TradeSlotItem entry : visible) {
                inventory.setItem(entry.slot, entry.icon);
                holder.mapTrade(entry.slot, entry.trade);
            }
        }
        addControls(inventory, holder, player, base);
    }

    private void animateOpen(Player player, MenuHolder holder, List<TradeConfig> trades, Map<String, String> base) {
        UUID playerId = player.getUniqueId();
        Inventory inventory = holder.getInventory();
        GuiConfig gui = plugin.gui();
        holder.clearMappings();
        prepareInventory(inventory, player, base);
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> Sounds.play(player, "BLOCK_ENCHANTMENT_TABLE_USE", 0.28F, 1.4F));

        int paneCount = 0;
        for (Map.Entry<Integer, ItemStack> entry : buildDecorations(inventory, player, base).entrySet()) {
            final int slot = entry.getKey();
            final ItemStack item = entry.getValue();
            steps.add(() -> inventory.setItem(slot, item));
            paneCount++;
            if (paneCount % 5 == 0) {
                final float pitch = 0.88F + Math.min(0.55F, paneCount * 0.018F);
                steps.add(() -> Sounds.play(player, "BLOCK_NOTE_BLOCK_CHIME", 0.20F, pitch));
            }
        }

        List<TradeSlotItem> visible = visibleTradeItems(inventory, holder, player, trades, base);
        if (visible.isEmpty()) {
            int slot = emptyOfferSlot(gui, inventory);
            ItemStack item = emptyOfferIcon(holder.instance());
            steps.add(() -> inventory.setItem(slot, item));
        } else {
            for (TradeSlotItem entry : visible) {
                steps.add(() -> {
                    inventory.setItem(entry.slot, entry.icon);
                    holder.mapTrade(entry.slot, entry.trade);
                    Sounds.play(player, "BLOCK_NOTE_BLOCK_PLING", 0.22F, 1.0F + Math.min(0.45F, entry.slot * 0.008F));
                });
            }
        }
        steps.add(() -> addControls(inventory, holder, player, base));
        steps.add(() -> Sounds.play(player, "BLOCK_AMETHYST_BLOCK_CHIME", 0.36F, 1.35F));

        final int[] cursor = {0};
        BukkitRunnable animator = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || opened.get(playerId) != holder
                        || player.getOpenInventory().getTopInventory() != inventory) {
                    cancelAnimation(playerId);
                    return;
                }
                if (cursor[0] >= steps.size()) {
                    cancelAnimation(playerId);
                    return;
                }
                try {
                    steps.get(cursor[0]++).run();
                } catch (Throwable ignored) {
                    // A cosmetic frame must never prevent the trade menu from remaining usable.
                }
            }
        };
        animationTasks.put(playerId, animator.runTaskTimer(plugin, 0L, gui.animationDelayTicks()));
    }

    private int emptyOfferSlot(GuiConfig gui, Inventory inventory) {
        if (!gui.tradeSlots().isEmpty()) {
            return gui.tradeSlots().get(gui.tradeSlots().size() / 2);
        }
        return Math.max(0, Math.min(inventory.getSize() - 1, (gui.startSlot() + gui.endSlot()) / 2));
    }

    private ItemStack emptyOfferIcon(TraderInstance instance) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String title = "&7« &#F8BEFBПредложений пока нет &7»";
            String detail = instance != null && "ghost".equalsIgnoreCase(instance.config().id())
                    ? "Товары и цены ещё не настроены."
                    : "Сейчас торговец не предлагает сделки.";
            meta.setDisplayName(ColorUtil.translate(title));
            meta.setLore(Arrays.asList(
                    ColorUtil.translate("&#F8BEFB&l┃ "),
                    ColorUtil.translate("&#F8BEFB&l┃ &f" + detail),
                    ColorUtil.translate("&#F8BEFB&l┃ &7Загляни позже.")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addControls(Inventory inventory, MenuHolder holder, Player player, Map<String, String> base) {
        if (inventory.getSize() < 9) return;
        int footer = inventory.getSize() - 9;
        if (holder.page() > 0) {
            setControl(inventory, holder, player, base, footer + 1, "previous", Material.ARROW,
                    "&7« &#F8BEFBПредыдущая страница &7»",
                    Arrays.asList("&#F8BEFB&l┃ &fСтраница: &#F8BEFB{previous} / {pages}", "&7● &fНажмите для перехода"));
        }
        setControl(inventory, holder, player, base, footer + 4, "info", Material.PAPER,
                "&7« &#F8BEFB{trader_colored} &7»",
                Arrays.asList("&#F8BEFB&l┃ ", "&#F8BEFB&l┃ &fМир: &#F8BEFB{world}",
                        "&#F8BEFB&l┃ &fКоординаты: &#F8BEFBX {x}, Y {y}, Z {z}",
                        "&#F8BEFB&l┃ &fДо исчезновения: &#F8BEFB{left}",
                        "&#F8BEFB&l┃ &fСтраница: &#F8BEFB{page} / {pages}",
                        "&#F8BEFB&l┃ ", "&7● &fТовары открываются нажатием"));
        setControl(inventory, holder, player, base, footer + 5, "close", Material.RED_DYE,
                "&7« &cЗакрыть меню &7»", Arrays.asList("&#F8BEFB&l┃ &fВернуться в мир."));
        if (holder.page() + 1 < holder.pages()) {
            setControl(inventory, holder, player, base, footer + 8, "next", Material.ARROW,
                    "&7« &#F8BEFBСледующая страница &7»",
                    Arrays.asList("&#F8BEFB&l┃ &fСтраница: &#F8BEFB{next} / {pages}", "&7● &fНажмите для перехода"));
        }
    }

    private void setControl(Inventory inventory, MenuHolder holder, Player player, Map<String, String> base,
                            int slot, String key, Material material, String name, List<String> lore) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        IconConfig icon = new IconConfig(material, 1, false, 0, name, lore, null, null, slot);
        inventory.setItem(slot, IconFactory.build(icon, player, base, 1));
        holder.mapButton(slot, key);
    }

    private void cancelAnimation(UUID playerId) {
        BukkitTask task = animationTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    private void icon(Inventory inventory, MenuHolder holder, IconConfig icon, Player player, Map<String, String> map, String key) {
        if (icon == null) {
            return;
        }
        int slot = icon.slot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, IconFactory.build(icon, player, map, 1));
        holder.mapButton(slot, key);
    }

    private void button(Inventory inventory, MenuHolder holder, GuiConfig gui, Player player, Map<String, String> base,
                        String key, IconConfig override) {
        IconConfig icon = gui.button(key);
        IconConfig info = override;
        if (info != null && (info.displayName() != null || !info.lore().isEmpty())) {
            int slot = icon != null && icon.slot() >= 0 ? icon.slot() : info.slot();
            icon = new IconConfig(info.material() == null ? (icon == null ? org.bukkit.Material.PAPER : icon.material()) : info.material(),
                    info.amount(), info.glow(), info.customModelData(),
                    info.displayName() == null && icon != null ? icon.displayName() : info.displayName(),
                    info.lore().isEmpty() && icon != null ? icon.lore() : info.lore(),
                    info.skin(), info.sound(), slot);
        }
        if (icon == null) {
            return;
        }
        Map<String, String> map = new HashMap<>(base);
        icon(inventory, holder, icon, player, map, key);
    }

    private ItemStack tradeIcon(Player player, TraderInstance instance, TradeConfig trade, Map<String, String> base) {
        GuiConfig gui = plugin.gui();

        // Если stock == 0 — РАСКУПИЛИ
        int stockVal = instance.stock(trade.id());
        if (stockVal == 0) {
            org.bukkit.inventory.ItemStack barrier = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta bm = barrier.getItemMeta();
            bm.setDisplayName(ColorUtil.translate("&c&lРАСКУПИЛИ"));
            String plainName = ColorUtil.plain(trade.displayName())
                    .replaceAll("^[«»\\s]+", "").replaceAll("[«»\\s]+$", "").trim();
            bm.setLore(java.util.Arrays.asList(
                    ColorUtil.translate("&7" + plainName),
                    ColorUtil.translate("&7Этот товар закончился")
            ));
            barrier.setItemMeta(bm);
            return barrier;
        }

        Map<String, String> placeholders = new HashMap<>(base);
        String plainName = ColorUtil.plain(trade.displayName())
                .replaceAll("^[«»\\s]+", "").replaceAll("[«»\\s]+$", "").trim();
        placeholders.put("trade", plainName);
        placeholders.put("trade_colored", ColorUtil.translate(trade.displayName()));
        placeholders.put("item", plainName);
        placeholders.put("price", plugin.purchases().formatAll(trade, 1, player));
        placeholders.put("currency", plugin.purchases().currencyLabel(trade));
        placeholders.put("balance", plugin.purchases().balanceLabel(trade, player));
        placeholders.put("stock", instance.stockText(trade.id()));
        IconConfig source = trade.icon();
        String name = gui.overrideTradeLore() && gui.tradeDisplayName() != null ? gui.tradeDisplayName() : trade.displayName();
        List<String> lore = gui.overrideTradeLore() && !gui.tradeLore().isEmpty() ? gui.tradeLore() : trade.lore();

        // Если есть rawItem (зелье/enchanted/custom NBT) — строим иконку на его основе
        org.bukkit.inventory.ItemStack rawItem = trade.firstRawItem();
        if (rawItem != null) {
            org.bukkit.inventory.ItemStack icon = rawItem.clone();
            icon.setAmount(Math.max(1, source.amount()));
            org.bukkit.inventory.meta.ItemMeta im = icon.getItemMeta();
            if (im != null) {
                // Название в витрине копируется прямо с предмета: собственное имя
                // ItemStack («Зелье огнестойкости») не перетирается никогда.
                List<String> resolvedLore = new ArrayList<>();
                for (String line : lore) {
                    String l = line;
                    for (java.util.Map.Entry<String, String> e : placeholders.entrySet()) {
                        l = l.replace("{" + e.getKey() + "}", e.getValue());
                    }
                    resolvedLore.add(ColorUtil.translate(l));
                }
                // Пустым лором не затираем собственный лор предмета
                if (!resolvedLore.isEmpty()) {
                    im.setLore(resolvedLore);
                }
                icon.setItemMeta(im);
            }
            return icon;
        }

        IconConfig icon = new IconConfig(source.material(), Math.max(1, source.amount()), source.glow() || gui.tradeGlow(),
                source.customModelData(), name, lore, source.skin(), source.sound(), -1);
        return IconFactory.build(icon, player, placeholders, Math.max(1, source.amount()));
    }

    private List<TradeConfig> available(Player player, TraderInstance instance) {
        // СЛУЧАЙНАЯ ВЫБОРКА этого торговца (offeredTrades): при спавне каждый
        // торговец берёт из пула /etr edit случайные предметы (random-trades).
        // Раньше меню показывало ВЕСЬ пул редактора — выборка игнорировалась.
        List<TradeConfig> result = new ArrayList<>();
        for (TradeConfig trade : instance.offeredTrades()) {
            if (trade.requiredPermission() != null && !trade.requiredPermission().trim().isEmpty()
                    && !player.hasPermission(trade.requiredPermission())) {
                continue;
            }
            result.add(trade);
        }
        return result;
    }

    private Map<String, String> basePlaceholders(Player player, TraderInstance instance, int page, int pages) {
        Map<String, String> map = new HashMap<>(instance.placeholders());
        map.put("page", String.valueOf(page + 1));
        map.put("pages", String.valueOf(pages));
        map.put("previous", String.valueOf(Math.max(1, page)));
        map.put("next", String.valueOf(Math.min(pages, page + 2)));
        if (player != null) {
            map.put("player", player.getName());
            map.put("levels", String.valueOf(player.getLevel()));
            map.put("exp", String.valueOf(plugin.currency().totalExperience(player)));
        }
        return map;
    }

    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }
        MenuHolder holder = (MenuHolder) top.getHolder();
        int slot = event.getRawSlot();
        String button = holder.button(slot);
        if (button != null) {
            handleButton(player, holder, button);
            return;
        }
        // Свежий TradeConfig из resolveTrades (не кэш offeredTrades)
        String tradeId = holder.tradeId(slot);
        if (tradeId == null) return;
        TradeConfig trade = null;
        for (TradeConfig tc : plugin.resolveTrades(holder.instance().config())) {
            if (tc.id().equalsIgnoreCase(tradeId)) { trade = tc; break; }
        }
        if (trade == null) trade = holder.trade(slot);
        if (trade == null) return;
        // Покупка: сразу закрываем меню, анимация покупки видна без GUI
        ru.rooyzee.elytrixtrader.currency.PurchaseService.Result result =
                plugin.purchases().buy(player, holder.instance(), trade, 1);
        if (result == ru.rooyzee.elytrixtrader.currency.PurchaseService.Result.SUCCESS) {
            player.closeInventory();
        } else {
            refresh(player, holder);
        }
    }

    private void handleButton(Player player, MenuHolder holder, String key) {
        switch (key) {
            case "close":
                player.closeInventory();
                break;
            case "previous":
                open(player, holder.instance(), holder.page() - 1);
                break;
            case "next":
                open(player, holder.instance(), holder.page() + 1);
                break;
            case "info":
            default:
                Sounds.play(player, "BLOCK_NOTE_BLOCK_CHIME", 0.3F, 1.2F);
                break;
        }
    }

    private void refresh(Player player, MenuHolder holder) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        cancelAnimation(player.getUniqueId());
        fill(inventory, holder, player, available(player, holder.instance()));
    }

    /** Called for any inventory close (quit/teleport); clears current menu state. */
    public void onClose(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        cancelAnimation(playerId);
        opened.remove(playerId);
        active.remove(playerId);
    }

    /** Ignore the close event of a previous page when a new Trader page is already open. */
    public void onInventoryClose(Player player, Inventory closedInventory) {
        if (player == null || closedInventory == null) return;
        MenuHolder current = opened.get(player.getUniqueId());
        if (current == null || current.getInventory() != closedInventory) return;
        onClose(player);
    }

    public void cancelAnimations() {
        for (BukkitTask task : new ArrayList<>(animationTasks.values())) {
            if (task != null) task.cancel();
        }
        animationTasks.clear();
    }

    /**
     * Покупка из BMenu: консольная команда elytrixtrader buy <player> <tradeId>
     * вызывает этот метод, логика оплаты/кулдауна остаётся общей.
     */
    public void buyFromMenu(Player player, String tradeId) {
        if (player == null || tradeId == null) {
            return;
        }
        TraderInstance instance = active.get(player.getUniqueId());
        if (instance == null || !instance.alive()) {
            instance = plugin.traders().nearest(player, null);
        }
        if (instance == null) {
            return;
        }
        // Свежий TradeConfig из resolveTrades
        TradeConfig trade = null;
        for (TradeConfig tc : plugin.resolveTrades(instance.config())) {
            if (tc.id().equalsIgnoreCase(tradeId)) { trade = tc; break; }
        }
        if (trade == null) return;
        plugin.purchases().buy(player, instance, trade, 1);
    }

    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    public void openNearest(Player player, String traderId) {
        TraderInstance instance = plugin.traders().nearest(player, traderId);
        if (instance == null) {
            plugin.messages().send(player, "interaction.not-found", player, null);
            return;
        }
        interact(player, instance);
    }
}
