package ru.elytrix.dungeons.gui;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonLootEntry;
import ru.elytrix.dungeons.model.DungeonTemplate;
import ru.elytrix.dungeons.model.LootPoolType;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonMenuService {

    private final ElytrixDungeonsPlugin plugin;

    public DungeonMenuService(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canTeleportFromPlayerMenu(Player player) {
        return player.hasPermission("elytrixdungeons.tp")
                || player.hasPermission("elytrixdungeons.admin")
                || plugin.getConfig().getBoolean("player-teleport.allow-all-players", false);
    }

    public void openPlayerOverview(Player player) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.PLAYER_OVERVIEW, null);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&lДанжи мира (14)"));
        holder.setInventory(inv);
        fillBorder(inv);

        long nearestSec = Long.MAX_VALUE;
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            nearestSec = Math.min(nearestSec, d.secondsUntilReset());
        }
        String nearestTimer = nearestSec == Long.MAX_VALUE ? "12ч 00м 00с" : TextUtil.formatDuration(nearestSec);

        inv.setItem(4, icon(Material.TRIAL_KEY, "&7« &#F8BEFBДанжи мира &7»", List.of(
                "&#F8BEFB&l┃ &fАктивно данжей: &#F8BEFB" + plugin.getDungeonManager().getAllDungeons().size() + " &8(7 Наземных + 7 Подземных)",
                "&#F8BEFB&l┃ &fБлижайшее обновление: &#F8BEFB" + nearestTimer,
                "&#F8BEFB&l┃",
                "&#F8BEFB&l┃ &#96FF91Наземные дома: &fсундуки + Призраки (100% осколки, 5% ключ)",
                "&#F8BEFB&l┃ &#89A0FFПодземные залы: &fбой со стражами за главный шалкер"
        )));

        boolean allowTp = canTeleportFromPlayerMenu(player);
        placeSevenRow(inv, DungeonCategory.SURFACE, 19, false, allowTp);
        placeSevenRow(inv, DungeonCategory.UNDERGROUND, 28, false, allowTp);

        if (player.hasPermission("elytrixdungeons.admin")) {
            inv.setItem(48, icon(Material.COMPARATOR, "&7« &#F8BEFBПанель администратора &7»", List.of(
                    "&#F8BEFB&l┃ &fУправление данжами, лутом, защитой и .schem",
                    "&7● &fНажмите, чтобы открыть"
            )));
        }
        inv.setItem(49, closeButton());
        player.openInventory(inv);
    }

    public void openAdminBoard(Player player) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.ADMIN_BOARD, null);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&lУправление Данжами (7 + 7)"));
        holder.setInventory(inv);
        fillBorder(inv);

        int dungeonCount = plugin.getDungeonManager().getAllDungeons().size();
        String scanLine = plugin.getDungeonManager().isScanning()
                ? "&#96FF91Идёт поиск мест: " + plugin.getDungeonManager().getPendingScanDone() + "/" + plugin.getDungeonManager().getPendingScanTotal()
                : plugin.getDungeonManager().isPlacementInProgress()
                        ? "&#F8BEFBУстанавливается данж, дождитесь завершения"
                        : dungeonCount == 0
                                ? "&#F8BEFBАвтоспавн отключён: создавайте по одному командой /elytrixdungeon create surface|underground <id-схемы>"
                                : "&#96FF91Создано: " + dungeonCount + "/14 (координаты сохранены в dungeons.yml)";

        inv.setItem(4, icon(Material.BEACON, "&7« &#F8BEFBСтатус Данжей &7»", List.of(
                "&#F8BEFB&l┃ &fСостояние: " + scanLine,
                "&#F8BEFB&l┃ &fНаземные (.schem): &fschematics/surface/ &8(surface_1, surface_2)",
                "&#F8BEFB&l┃ &fПодземные (.schem): &fschematics/underground/ &8(underground_1, underground_2)"
        )));

        // Row 2 (slots 19..25 = 7 inner slots!): all 7 Surface Houses
        // Row 3 (slots 28..34 = 7 inner slots!): all 7 Underground Vaults
        placeSevenRow(inv, DungeonCategory.SURFACE, 19, true, true);
        placeSevenRow(inv, DungeonCategory.UNDERGROUND, 28, true, true);

        boolean allowBreak = plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);
        boolean hidden = plugin.getDungeonManager().isHiddenFromWorld();

        inv.setItem(45, icon(Material.CHEST, "&7« &#F8BEFBНастройка лута (GUI) &7»", List.of(
                "&#F8BEFB&l┃ &fСундуки Дома, Главный шалкер и бочки Подземелья",
                "&7● &fНажмите для редактирования"
        )));
        inv.setItem(46, icon(Material.STRUCTURE_BLOCK, "&7« &#F8BEFBПоставить .schem здесь &7»", List.of(
                "&#F8BEFB&l┃ &fРучной спавн схемы на вашей точке",
                "&#F8BEFB&l┃ &fНе больше 7 данжей каждого типа",
                "&7● &fНажмите для выбора"
        )));
        inv.setItem(47, icon(Material.AMETHYST_SHARD, "&7« &#F8BEFBВзять Призрачный осколок &7»", List.of(
                "&#F8BEFB&l┃ &fВыдать себе 16 Призрачных осколков",
                "&7● &fНажмите для выдачи"
        )));
        inv.setItem(49, closeButton());
        inv.setItem(51, icon(Material.SHIELD, "&7« &#F8BEFBРежим раскопки данжей &7»", List.of(
                "&#F8BEFB&l┃ &fСейчас: " + (allowBreak
                        ? "&#96FF91Ломать можно &7(авто-восстановление +12 бл. вокруг)"
                        : "&#FF5555Защита включена &7(раскапывать нельзя)"),
                "&#F8BEFB&l┃ &fЗапрет привата рядом: &#F8BEFB" + plugin.getConfig().getInt("protection.claim-padding-blocks", 40) + " блоков",
                "&7● &fЛКМ — переключить режим"
        )));
        inv.setItem(52, icon(hidden ? Material.GRAY_DYE : Material.LIME_DYE,
                "&7« &#F8BEFBВидимость на карте &7»", List.of(
                        "&#F8BEFB&l┃ &fСейчас: " + (hidden ? "&cСкрыты (чистый рельеф)" : "&#96FF91Активны в мире"),
                        "&7● &fЛКМ — " + (hidden ? "вернуть на сохранённые места" : "скрыть и вернуть чистый рельеф")
                )));
        inv.setItem(53, icon(Material.RECOVERY_COMPASS, "&7« &#F8BEFBОбновление и поиск мест &7»", List.of(
                "&#F8BEFB&l┃ &fВосстановить блоки вокруг и внутри всех 14 данжей",
                "&7● &fЛКМ — восстановить все 14 на своих местах",
                "&7● &fShift+ПКМ — стереть и найти 14 идеальных мест заново"
        )));

        player.openInventory(inv);
    }

    private void placeSevenRow(Inventory inv, DungeonCategory cat, int startSlot, boolean adminControls, boolean allowTp) {
        int col = 0;
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            if (d.getCategory() != cat || col >= 7) continue;
            BlockPosKey anchor = d.getHologramAnchor();
            String stateInfo = cat == DungeonCategory.SURFACE
                    ? "Призраков внутри: &#F8BEFB" + plugin.getGhostController().countAliveGhosts(d.getId())
                    : "Шалкер: &#F8BEFB" + d.getStatus().getTitle();

            List<String> lore = new ArrayList<>();
            if (adminControls) {
                lore.add("&#F8BEFB&l┃ &fСхема: &7" + d.getTemplateId() + ".schem");
            }
            lore.add("&#F8BEFB&l┃ &f" + stateInfo);
            lore.add("&#F8BEFB&l┃ &fКоординаты: &#F8BEFB" + anchor.x() + ", " + anchor.y() + ", " + anchor.z());
            lore.add("&#F8BEFB&l┃ &fОбновление: &#F8BEFB" + TextUtil.formatDuration(d.secondsUntilReset()));

            if (adminControls) {
                lore.add("&#F8BEFB&l┃");
                lore.add("&7● &fЛКМ — телепортироваться внутрь");
                lore.add("&7● &fПКМ — восстановить данж и блоки вокруг");
                lore.add("&7● &fShift+ПКМ — перенести на новое место");
            } else if (allowTp) {
                lore.add("&#F8BEFB&l┃");
                lore.add("&7● &fНажмите для телепортации");
            }

            ItemStack item = icon(cat.getIcon(),
                    "&7« " + cat.getColorHex() + d.getDisplayName() + " &8#" + d.getId() + " &7»",
                    lore);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(plugin.getGuiTargetPdc(), PersistentDataType.STRING, d.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(startSlot + (col++), item);
        }
    }

    public void openLootPoolSelector(Player admin) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.LOOT_POOL_SELECT, null);
        Inventory inv = Bukkit.createInventory(holder, 45, TextUtil.color("&#F8BEFB&lВыберите пул лута"));
        holder.setInventory(inv);
        fillBorder(inv);

        int[] slots = {20, 22, 24};
        LootPoolType[] types = LootPoolType.values();
        for (int i = 0; i < types.length; i++) {
            LootPoolType pt = types[i];
            ItemStack it = icon(pt.getIcon(),
                    "&7« " + pt.getColorHex() + pt.getTitle() + " &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fПредметов в пуле: &#F8BEFB" + plugin.getLootRegistry().getPool(pt).size(),
                            "&#F8BEFB&l┃",
                            "&7● &fНажмите для настройки предметов и шансов"
                    ));
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(plugin.getGuiTargetPdc(), PersistentDataType.STRING, pt.name());
                it.setItemMeta(meta);
            }
            inv.setItem(slots[i], it);
        }
        inv.setItem(40, backButton());
        admin.openInventory(inv);
    }

    public void openLootPoolEditor(Player admin, LootPoolType poolType) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.LOOT_POOL_EDITOR, poolType);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&l" + poolType.getTitle()));
        holder.setInventory(inv);

        List<DungeonLootEntry> pool = plugin.getLootRegistry().getPool(poolType);
        int limit = Math.min(45, pool.size());
        for (int i = 0; i < limit; i++) {
            DungeonLootEntry e = pool.get(i);
            ItemStack disp = plugin.getLootRegistry().materializePreview(e).clone();
            ItemMeta meta = disp.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                        ? new ArrayList<>(meta.getLore())
                        : new ArrayList<>();
                lore.add(TextUtil.color("&8─────────────────"));
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fШанс: &#F8BEFB" + (e.isGuaranteed() ? "100% (Гарант)" : e.getChancePercent() + "%")));
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fКоличество: &#F8BEFB" + e.getMinAmount() + "–" + e.getMaxAmount()));
                lore.add(TextUtil.color("&7● &fЛКМ / ПКМ: &a+5% &7/ &c-5%"));
                lore.add(TextUtil.color("&7● &fShift+ЛКМ: переключить Гарант"));
                lore.add(TextUtil.color("&7● &fСКМ (колёсико): +1 к макс. кол-ву"));
                lore.add(TextUtil.color("&7● &fКлавиша Q: &cудалить предмет"));
                meta.setLore(lore);
                disp.setItemMeta(meta);
            }
            inv.setItem(i, disp);
        }

        for (int s = 45; s < 54; s++) inv.setItem(s, filler());
        inv.setItem(48, backButton());
        inv.setItem(49, closeButton());
        inv.setItem(50, icon(Material.HOPPER, "&7« &aДобавить свой предмет &7»", List.of(
                "&#F8BEFB&l┃ &fКликните по любому предмету в своём",
                "&#F8BEFB&l┃ &fинвентаре снизу — он сразу добавится в пул!"
        )));
        admin.openInventory(inv);
    }

    public void openTemplateSpawner(Player admin) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.TEMPLATE_SPAWNER, null);
        Inventory inv = Bukkit.createInventory(holder, 45, TextUtil.color("&#F8BEFB&lСхемы .schem"));
        holder.setInventory(inv);
        fillBorder(inv);

        int slot = 10;
        for (DungeonTemplate tpl : plugin.getDungeonManager().getTemplates().values()) {
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot >= 35) break;
            ItemStack it = icon(tpl.getCategory().getIcon(),
                    "&7« " + tpl.getCategory().getColorHex() + tpl.getId() + ".schem &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fКатегория: " + tpl.getCategory().getColorHex() + tpl.getCategory().getTitle(),
                            "&#F8BEFB&l┃ &fРазмер: &#F8BEFB" + tpl.getSizeX() + "×" + tpl.getSizeY() + "×" + tpl.getSizeZ(),
                            "&7● &fНажмите, чтобы поставить здесь"
                    ));
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(plugin.getGuiTargetPdc(), PersistentDataType.STRING, tpl.getId());
                it.setItemMeta(meta);
            }
            inv.setItem(slot++, it);
        }
        inv.setItem(40, backButton());
        admin.openInventory(inv);
    }

    private static void fillBorder(Inventory inv) {
        ItemStack glass = filler();
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == (size / 9) - 1 || c == 0 || c == 8) inv.setItem(i, glass);
        }
    }

    private static ItemStack filler() { return icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()); }
    private static ItemStack closeButton() { return icon(Material.RED_DYE, "&7« &cЗакрыть меню &7»", List.of()); }
    private static ItemStack backButton() { return icon(Material.BLACK_DYE, "&7« &fНазад &7»", List.of()); }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack st = new ItemStack(mat);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            if (!lore.isEmpty()) meta.setLore(TextUtil.colorList(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            st.setItemMeta(meta);
        }
        return st;
    }
}
