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
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonLootEntry;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.DungeonTemplate;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonMenuService {

    private final ElytrixDungeonsPlugin plugin;

    public DungeonMenuService(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openPlayerOverview(Player player) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.PLAYER_OVERVIEW, 1, true);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&lДанжи мира"));
        holder.setInventory(inv);
        fillBorder(inv);

        long nearestSec = Long.MAX_VALUE;
        int readyCount = 0;
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            nearestSec = Math.min(nearestSec, d.secondsUntilReset());
            if (d.getStatus() == DungeonStatus.READY) readyCount++;
        }
        String nearestTimer = nearestSec == Long.MAX_VALUE ? "12ч 00м 00с" : TextUtil.formatDuration(nearestSec);

        inv.setItem(13, icon(Material.TRIAL_KEY, "&7« &#F8BEFBСистема Данжей &7»", List.of(
                "&#F8BEFB&l┃",
                "&#F8BEFB&l┃ &fВсего в мире: &#F8BEFB" + plugin.getDungeonManager().getAllDungeons().size() + " данжей",
                "&#F8BEFB&l┃ &fСвободно сейчас: &#96FF91" + readyCount,
                "&#F8BEFB&l┃ &fБлижайшее обновление: &#F8BEFB" + nearestTimer,
                "&#F8BEFB&l┃ &fЦикл возобновления: &#F8BEFBКаждые 12 часов",
                "&#F8BEFB&l┃",
                "&7● &fНаходите данжи в мире 3000×3000,",
                "&7● &fпроходите испытания у Алтаря и забирайте ключи!"
        )));

        inv.setItem(20, categorySummaryIcon(DungeonCategory.SURFACE, "1★ – 3★",
                "Ржавый, Знаний, Королевский",
                "Расположены на суше по всему миру"));
        inv.setItem(22, categorySummaryIcon(DungeonCategory.UNDERGROUND, "3★ – 4★",
                "Знаний, Королевский, Пустотный",
                "Спуск через разрушенную шахту на поверхности"));
        inv.setItem(24, categorySummaryIcon(DungeonCategory.SKY, "4★ – 5★",
                "Королевский, Пустотный, Уникальный",
                "Парящие острова на высоте 150–185 блоков"));

        boolean showCoords = plugin.getConfig().getBoolean("player-menu.show-coordinates", false);
        if (showCoords) {
            int slot = 29;
            for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                if (slot > 33) break;
                BlockPosKey a = d.getAltarPos();
                inv.setItem(slot++, icon(d.getCategory().getIcon(),
                        "&7« " + d.getCategory().getColorHex() + d.getDisplayName() + " &7»",
                        List.of(
                                "&#F8BEFB&l┃ &fКоординаты: &#F8BEFB" + a.x() + ", " + a.y() + ", " + a.z(),
                                "&#F8BEFB&l┃ &fСтатус: &f" + d.getStatus().getTitle(),
                                "&#F8BEFB&l┃ &fОбновление: &#F8BEFB" + TextUtil.formatDuration(d.secondsUntilReset())
                        )));
            }
        }

        if (player.hasPermission("elytrixdungeons.admin")) {
            inv.setItem(48, icon(Material.COMPARATOR, "&7« &#F8BEFBПанель администратора &7»", List.of(
                    "&#F8BEFB&l┃",
                    "&#F8BEFB&l┃ &fТелепорт по всем 15 данжам,",
                    "&#F8BEFB&l┃ &fручной ресет и редактор лута сундуков",
                    "&#F8BEFB&l┃",
                    "&7● &fНажмите, чтобы открыть"
            )));
        }
        inv.setItem(49, closeButton());
        player.openInventory(inv);
    }

    public void openAdminBoard(Player player) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.ADMIN_BOARD, 1, true);
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color("&#F8BEFB&lУправление Данжами (15)"));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(10, icon(Material.MOSSY_STONE_BRICKS, "&#96FF91Наземные данжи &7(5)",
                List.of("&7Ряд справа — наземные точки")));
        inv.setItem(19, icon(Material.DEEPSLATE_TILES, "&#89A0FFПодземные данжи &7(5)",
                List.of("&7Ряд справа — подземные склепы")));
        inv.setItem(28, icon(Material.AMETHYST_BLOCK, "&#F8BEFBВоздушные данжи &7(5)",
                List.of("&7Ряд справа — небесные острова")));

        placeCategoryRow(inv, DungeonCategory.SURFACE, 11);
        placeCategoryRow(inv, DungeonCategory.UNDERGROUND, 20);
        placeCategoryRow(inv, DungeonCategory.SKY, 29);

        boolean relocate = plugin.getConfig().getBoolean("regeneration.relocate-on-reset", false);
        inv.setItem(46, icon(Material.CHEST, "&7« &#F8BEFBРедактор лута сундуков &7»", List.of(
                "&#F8BEFB&l┃ &fНастройка наград и шансов",
                "&#F8BEFB&l┃ &fдля уровней сложности &#F8BEFB1★ – 5★",
                "&#F8BEFB&l┃",
                "&7● &fЛКМ — открыть редактор"
        )));
        inv.setItem(47, icon(Material.STRUCTURE_BLOCK, "&7« &#F8BEFBСоздать данж здесь &7»", List.of(
                "&#F8BEFB&l┃ &fПоставить любую постройку",
                "&#F8BEFB&l┃ &fпрямо на вашей позиции",
                "&#F8BEFB&l┃",
                "&7● &fЛКМ — выбрать шаблон"
        )));
        inv.setItem(49, closeButton());
        inv.setItem(51, icon(Material.ENDER_PEARL, "&7« &#F8BEFBРежим обновления &7»", List.of(
                "&#F8BEFB&l┃ &fСейчас: " + (relocate ? "&#96FF91Новые координаты каждые 12ч" : "&#F8BEFBНа своих местах (защита от привата)"),
                "&#F8BEFB&l┃",
                "&7● &fЛКМ — переключить режим"
        )));
        inv.setItem(52, icon(Material.RECOVERY_COMPASS, "&7« &#F8BEFBОбновить все 15 данжей &7»", List.of(
                "&#F8BEFB&l┃ &fВосстановить блоки, сундуки",
                "&#F8BEFB&l┃ &fи таймеры всех данжей прямо сейчас",
                "&#F8BEFB&l┃",
                "&7● &fЛКМ — запустить обновление"
        )));
        inv.setItem(53, icon(Material.EMERALD, "&7« &aДоспавнить недостающие &7»", List.of(
                "&#F8BEFB&l┃ &fДогонит количество до 5 / 5 / 5",
                "&7● &fЛКМ — проверить и создать"
        )));

        player.openInventory(inv);
    }

    private void placeCategoryRow(Inventory inv, DungeonCategory cat, int startSlot) {
        int offset = 0;
        for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
            if (d.getCategory() != cat || offset >= 6) continue;
            BlockPosKey alt = d.getAltarPos();
            Material mat = switch (d.getStatus()) {
                case READY -> cat.getIcon();
                case ACTIVE_TRIAL -> Material.TRIAL_SPAWNER;
                case CLEARED -> Material.ENDER_CHEST;
                case REGENERATING -> Material.CLOCK;
            };
            ItemStack item = icon(mat,
                    "&7« " + cat.getColorHex() + d.getDisplayName() + " &8#" + d.getId() + " &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fСложность: " + TextUtil.stars(d.getTier()),
                            "&#F8BEFB&l┃ &fИспытание: &#F8BEFB" + d.getTrialType().getTitle(),
                            "&#F8BEFB&l┃ &fСтатус: &f" + d.getStatus().getTitle(),
                            "&#F8BEFB&l┃ &fАлтарь: &#F8BEFB" + alt.x() + ", " + alt.y() + ", " + alt.z(),
                            "&#F8BEFB&l┃ &fВозобновление: &#F8BEFB" + TextUtil.formatDuration(d.secondsUntilReset()),
                            "&#F8BEFB&l┃",
                            "&7● &fЛКМ — телепортироваться внутрь",
                            "&7● &fПКМ — обновить этот данж сейчас",
                            "&7● &fShift+ПКМ — перенести в новое место"
                    ));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        plugin.getGuiTargetPdc(),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        d.getId()
                );
                item.setItemMeta(meta);
            }
            inv.setItem(startSlot + (offset++), item);
        }
    }

    public void openLootTierSelector(Player admin) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.LOOT_TIER_SELECT, 1, true);
        Inventory inv = Bukkit.createInventory(holder, 45, TextUtil.color("&#F8BEFB&lЛут Данжей — Уровни"));
        holder.setInventory(inv);
        fillBorder(inv);

        for (int tier = 1; tier <= 5; tier++) {
            inv.setItem(10 + tier, icon(tier >= 4 ? Material.OMINOUS_TRIAL_KEY : Material.TRIAL_KEY,
                    "&7« &#F8BEFBНаградные сундуки " + tier + "★ &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fОткрываются после испытания",
                            "&#F8BEFB&l┃ &fПредметов в пуле: &#F8BEFB" + plugin.getLootRegistry().getPool(tier, true).size(),
                            "&#F8BEFB&l┃",
                            "&7● &fНажмите для редактирования"
                    )));
            inv.setItem(19 + tier, icon(Material.BARREL,
                    "&7« &fОбычные бочки зала " + tier + "★ &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fДоступны сразу внутри данжа",
                            "&#F8BEFB&l┃ &fПредметов в пуле: &#F8BEFB" + plugin.getLootRegistry().getPool(tier, false).size(),
                            "&#F8BEFB&l┃",
                            "&7● &fНажмите для редактирования"
                    )));
        }
        inv.setItem(40, backButton());
        admin.openInventory(inv);
    }

    public void openLootPoolEditor(Player admin, int tier, boolean rewardPool) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.LOOT_POOL_EDITOR, tier, rewardPool);
        String title = (rewardPool ? "&#F8BEFB&lНаграды " : "&#F8BEFB&lБочки ") + tier + "★ &8(Клик по предмету)";
        Inventory inv = Bukkit.createInventory(holder, 54, TextUtil.color(title));
        holder.setInventory(inv);

        List<DungeonLootEntry> pool = plugin.getLootRegistry().getPool(tier, rewardPool);
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
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fКол-во: &#F8BEFB" + e.getMinAmount() + "–" + e.getMaxAmount()));
                lore.add(TextUtil.color("&7● &fЛКМ / ПКМ: &a+5% &7/ &c-5%"));
                lore.add(TextUtil.color("&7● &fShift+ЛКМ: переключить Гарант"));
                lore.add(TextUtil.color("&7● &fСКМ (колёсико): +1 к макс. кол-ву"));
                lore.add(TextUtil.color("&7● &fКлавиша Q: &cудалить из пула"));
                meta.setLore(lore);
                disp.setItemMeta(meta);
            }
            inv.setItem(i, disp);
        }

        for (int s = 45; s < 54; s++) {
            inv.setItem(s, filler());
        }
        inv.setItem(48, backButton());
        inv.setItem(49, closeButton());
        inv.setItem(50, icon(Material.HOPPER, "&7« &aДобавление предметов &7»", List.of(
                "&#F8BEFB&l┃ &fПросто кликните по любому предмету",
                "&#F8BEFB&l┃ &fв своём нижнем инвентаре — он",
                "&#F8BEFB&l┃ &fсразу добавится в этот пул лута!"
        )));
        admin.openInventory(inv);
    }

    public void openTemplateSpawner(Player admin) {
        DungeonGuiHolder holder = new DungeonGuiHolder(DungeonGuiHolder.Screen.TEMPLATE_SPAWNER, 1, true);
        Inventory inv = Bukkit.createInventory(holder, 45, TextUtil.color("&#F8BEFB&lВыбор шаблона данжа"));
        holder.setInventory(inv);
        fillBorder(inv);

        int slot = 10;
        for (DungeonTemplate tpl : plugin.getDungeonManager().getTemplates().values()) {
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot >= 35) break;
            ItemStack it = icon(tpl.getCategory().getIcon(),
                    "&7« " + tpl.getCategory().getColorHex() + tpl.getDisplayName() + " &7»",
                    List.of(
                            "&#F8BEFB&l┃ &fID: &7" + tpl.getId(),
                            "&#F8BEFB&l┃ &fТип: " + tpl.getCategory().getColorHex() + tpl.getCategory().getTitle(),
                            "&#F8BEFB&l┃ &fСложность: " + TextUtil.stars(tpl.getTier()),
                            "&#F8BEFB&l┃ &fРазмер: &#F8BEFB" + tpl.getSizeX() + "×" + tpl.getSizeY() + "×" + tpl.getSizeZ(),
                            "&#F8BEFB&l┃",
                            "&7● &fНажмите, чтобы заспавнить здесь"
                    ));
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(plugin.getGuiTargetPdc(),
                        org.bukkit.persistence.PersistentDataType.STRING, tpl.getId());
                it.setItemMeta(meta);
            }
            inv.setItem(slot++, it);
        }
        inv.setItem(40, backButton());
        admin.openInventory(inv);
    }

    private ItemStack categorySummaryIcon(DungeonCategory cat, String tiers, String keys, String hint) {
        return icon(cat.getIcon(),
                "&7« " + cat.getColorHex() + cat.getTitle() + " данж &8(" + plugin.getDungeonManager().countByCategory(cat) + " шт.) &7»",
                List.of(
                        "&#F8BEFB&l┃",
                        "&#F8BEFB&l┃ &fСложность: &#F8BEFB" + tiers,
                        "&#F8BEFB&l┃ &fКлючи Хранилища: &#F8BEFB" + keys,
                        "&#F8BEFB&l┃ &fОсобенность: &7" + hint,
                        "&#F8BEFB&l┃"
                ));
    }

    private static void fillBorder(Inventory inv) {
        ItemStack glass = filler();
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == (size / 9) - 1 || col == 0 || col == 8) {
                inv.setItem(i, glass);
            }
        }
    }

    private static ItemStack filler() {
        return icon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
    }

    private static ItemStack closeButton() {
        return icon(Material.RED_DYE, "&7« &cЗакрыть меню &7»", List.of());
    }

    private static ItemStack backButton() {
        return icon(Material.BLACK_DYE, "&7« &fНазад &7»", List.of());
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack st = new ItemStack(mat);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            if (!lore.isEmpty()) {
                meta.setLore(TextUtil.colorList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            st.setItemMeta(meta);
        }
        return st;
    }
}
