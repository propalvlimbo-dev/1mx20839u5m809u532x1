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

    public void openPlayerOverview(Player player) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.PLAYER_OVERVIEW, null);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&lДанжи мира"));
        holder.setInventory(inv);
        fillBorder(inv);

        long nearestSec = Long.MAX_VALUE;
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            nearestSec = Math.min(nearestSec, d.secondsUntilReset());
        }
        String nearestTimer = nearestSec == Long.MAX_VALUE ? "12ч 00м 00с" : TextUtil.formatDuration(nearestSec);

        inv.setItem(13, icon(Material.TRIAL_KEY, "&7« &#F8BEFBДанжи мира 3000×3000 &7»", List.of(
                "&#F8BEFB&l┃",
                "&#F8BEFB&l┃ &fВсего данжей в мире: &#F8BEFB" + plugin.getDungeonManager().getAllDungeons().size(),
                "&#F8BEFB&l┃ &fБлижайшее обновление: &#F8BEFB" + nearestTimer,
                "&#F8BEFB&l┃ &fЦикл возобновления: &#F8BEFBКаждые 12 часов",
                "&#F8BEFB&l┃ &fЛут: &fОбщий — кто первый успел, того и добыча",
                "&#F8BEFB&l┃"
        )));

        inv.setItem(20, icon(Material.MOSSY_STONE_BRICKS,
                "&7« &#96FF91Наземные данжи &8(" + plugin.getDungeonManager().countByCategory(DungeonCategory.SURFACE) + " шт.) &7»",
                List.of(
                        "&#F8BEFB&l┃ &fНаграды внутри:",
                        "&#F8BEFB&l┃ &7● &fОбычные сундуки и бочки с ресурсами",
                        "&#F8BEFB&l┃ &7● &f3 Уникальных шалкера с топ-лутом",
                        "&#F8BEFB&l┃",
                        "&#F8BEFB&l┃ &fУсловие: &#F8BEFB1 Уникальный ключ = 1 шалкер"
                )));
        inv.setItem(22, icon(Material.DEEPSLATE_TILES,
                "&7« &#89A0FFПодземные данжи &8(" + plugin.getDungeonManager().countByCategory(DungeonCategory.UNDERGROUND) + " шт.) &7»",
                List.of(
                        "&#F8BEFB&l┃ &fМеханика:",
                        "&#F8BEFB&l┃ &7● &fПКМ по шалкеру спавнит сильных мобов",
                        "&#F8BEFB&l┃ &7● &fУбейте всех стражей — шалкер откроется,",
                        "&#F8BEFB&l┃ &7  &fа данж уйдёт на перезарядку"
                )));
        inv.setItem(24, icon(Material.AMETHYST_CLUSTER,
                "&7« &#F8BEFBВоздушные данжи &8(" + plugin.getDungeonManager().countByCategory(DungeonCategory.SKY) + " шт.) &7»",
                List.of(
                        "&#F8BEFB&l┃ &fАнгельские осколки:",
                        "&#F8BEFB&l┃ &7● &f20% шанс: &#96FF91Лут / Монеты / Опыт",
                        "&#F8BEFB&l┃ &7● &f30% шанс: &#FF5555Взрыв осколка",
                        "&#F8BEFB&l┃ &7● &fПосле &#F8BEFB5 взрывов &fосколок исчезает"
                )));

        if (player.hasPermission("elytrixdungeons.admin")) {
            inv.setItem(48, icon(Material.COMPARATOR, "&7« &#F8BEFBПанель администратора &7»", List.of(
                    "&#F8BEFB&l┃ &fТелепорт по всем 15 данжам,",
                    "&#F8BEFB&l┃ &fнастройка лута и перезагрузка .schem",
                    "&7● &fНажмите, чтобы открыть"
            )));
        }
        inv.setItem(49, closeButton());
        player.openInventory(inv);
    }

    public void openAdminBoard(Player player) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.ADMIN_BOARD, null);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&lУправление Данжами (15)"));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(10, icon(Material.MOSSY_STONE_BRICKS, "&#96FF91Наземные &7(5)",
                List.of("&7Папка схем: &fschematics/surface/")));
        inv.setItem(19, icon(Material.DEEPSLATE_TILES, "&#89A0FFПодземные &7(5)",
                List.of("&7Папка схем: &fschematics/underground/")));
        inv.setItem(28, icon(Material.AMETHYST_CLUSTER, "&#F8BEFBВоздушные &7(5)",
                List.of("&7Папка схем: &fschematics/sky/")));

        placeCategoryRow(inv, DungeonCategory.SURFACE, 11);
        placeCategoryRow(inv, DungeonCategory.UNDERGROUND, 20);
        placeCategoryRow(inv, DungeonCategory.SKY, 29);

        boolean relocate = plugin.getConfig().getBoolean("regeneration.relocate-on-reset", false);
        inv.setItem(46, icon(Material.CHEST, "&7« &#F8BEFBНастройка лута (GUI) &7»", List.of(
                "&#F8BEFB&l┃ &fСундуки, бочки, Уникальные шалкеры,",
                "&#F8BEFB&l┃ &fПодземный шалкер и Ангельские осколки",
                "&7● &fНажмите для настройки"
        )));
        inv.setItem(47, icon(Material.STRUCTURE_BLOCK, "&7« &#F8BEFBПоставить .schem здесь &7»", List.of(
                "&#F8BEFB&l┃ &fРучной спавн любой схемы на вашей точке",
                "&7● &fНажмите для выбора"
        )));
        inv.setItem(49, closeButton());
        inv.setItem(51, icon(Material.ENDER_PEARL, "&7« &#F8BEFBРежим обновления 12ч &7»", List.of(
                "&#F8BEFB&l┃ &fСейчас: " + (relocate ? "&#96FF91Новые места каждые 12ч" : "&#F8BEFBНа своих местах (защита от привата)"),
                "&7● &fЛКМ — переключить"
        )));
        inv.setItem(52, icon(Material.RECOVERY_COMPASS, "&7« &#F8BEFBОбновить все 15 данжей &7»", List.of(
                "&#F8BEFB&l┃ &fВосстановить постройки, шалкеры и осколки сейчас",
                "&7● &fЛКМ — обновить на местах",
                "&7● &fShift+ПКМ — пересоздать с нуля из .schem"
        )));

        player.openInventory(inv);
    }

    private void placeCategoryRow(Inventory inv, DungeonCategory cat, int startSlot) {
        int offset = 0;
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            if (d.getCategory() != cat || offset >= 6) continue;
            BlockPosKey anchor = d.getHologramAnchor();
            String extra = switch (cat) {
                case SURFACE -> "Закрыто шалкеров: &#F8BEFB" + d.countLockedShulkers() + "/" + d.getShulkerUnlocked().size();
                case UNDERGROUND -> "Статус шалкера: &#F8BEFB" + d.getStatus().getTitle();
                case SKY -> "Целых осколков: &#F8BEFB" + d.getShardExplosions().size() + "/" + d.getInitialShardCount();
            };
            ItemStack item = icon(cat.getIcon(),
                    "&7« " + cat.getColorHex() + d.getDisplayName() + " &8#" + d.getId() + " &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fСхема: &7" + d.getTemplateId(),
                            "&#F8BEFB&l┃ &f" + extra,
                            "&#F8BEFB&l┃ &fКоординаты: &#F8BEFB" + anchor.x() + ", " + anchor.y() + ", " + anchor.z(),
                            "&#F8BEFB&l┃ &fВозобновление: &#F8BEFB" + TextUtil.formatDuration(d.secondsUntilReset()),
                            "&#F8BEFB&l┃",
                            "&7● &fЛКМ — телепортироваться внутрь",
                            "&7● &fПКМ — мгновенно обновить данж",
                            "&7● &fShift+ПКМ — перенести на новое место"
                    ));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(plugin.getGuiTargetPdc(), PersistentDataType.STRING, d.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(startSlot + (offset++), item);
        }
    }

    public void openLootPoolSelector(Player admin) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.LOOT_POOL_SELECT, null);
        Inventory inv = Bukkit.createInventory(holder, 45, TextUtil.color("&#F8BEFB&lВыберите пул лута"));
        holder.setInventory(inv);
        fillBorder(inv);

        int[] slots = {11, 12, 13, 20, 21, 23};
        LootPoolType[] types = LootPoolType.values();
        for (int i = 0; i < types.length; i++) {
            LootPoolType pt = types[i];
            ItemStack it = icon(pt.getIcon(),
                    "&7« " + pt.getColorHex() + pt.getTitle() + " &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fПредметов в пуле: &#F8BEFB" + plugin.getLootRegistry().getPool(pt).size(),
                            "&#F8BEFB&l┃",
                            "&7● &fНажмите для редактирования предметов и шансов"
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
                    "&7« " + tpl.getCategory().getColorHex() + tpl.getId() + " &7»",
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
