package ru.elytrix.vault.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.model.LootEntry;
import ru.elytrix.vault.model.Rarity;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.util.TextUtil;

public class KeyPoolEditorGui implements InventoryHolder {

    public static final int PAGE_SIZE = 36;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_MIN_ROLLS = 47;
    public static final int SLOT_MAX_ROLLS = 48;
    public static final int SLOT_GIVE_KEY = 49;
    public static final int SLOT_LEVEL = 51;
    public static final int SLOT_NEXT_PAGE = 52;
    public static final int SLOT_CLOSE = 53;

    private final ElytrixVaultPlugin plugin;
    private final VaultKeyType keyType;
    private final Inventory inventory;
    private int page;

    public KeyPoolEditorGui(ElytrixVaultPlugin plugin, VaultKeyType keyType, int page) {
        this.plugin = plugin;
        this.keyType = keyType;
        this.page = Math.max(0, page);
        this.inventory = Bukkit.createInventory(
                this,
                54,
                TextUtil.color("&#F8BEFB&lПул: " + keyType.getDisplayName())
        );
        refresh();
    }

    public VaultKeyType getKeyType() {
        return keyType;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
        refresh();
    }

    public int getEntryIndexForSlot(int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) {
            return -1;
        }
        int idx = page * PAGE_SIZE + slot;
        return idx < keyType.getEntries().size() ? idx : -1;
    }

    public boolean hasNextPage() {
        return (page + 1) * PAGE_SIZE < keyType.getEntries().size();
    }

    public void refresh() {
        int maxPage = Math.max(0, (keyType.getEntries().size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
        inventory.clear();

        Map<Rarity, Integer> weights = plugin.getKeyRegistry().getRarityWeights();
        int totalWeight = Math.max(1, keyType.getTotalWeight(weights));

        int start = page * PAGE_SIZE;
        int end = Math.min(keyType.getEntries().size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            LootEntry entry = keyType.getEntries().get(i);
            inventory.setItem(i - start, buildEntryIcon(entry, weights, totalWeight));
        }

        inventory.setItem(SLOT_BACK, buildControl(
                Material.BLACK_DYE,
                page > 0 ? "&7« &#F8BEFBПредыдущая страница &7»" : "&7« &#F8BEFBНазад &7»",
                List.of(
                        "&#F8BEFB&l┃ ",
                        page > 0
                                ? "&#F8BEFB&l┃ &fПерейти на страницу &#F8BEFB" + page
                                : "&#F8BEFB&l┃ &fВернуться к списку ключей.",
                        "&#F8BEFB&l┃ ",
                        "&7● &fНажмите для перехода"
                ),
                false
        ));

        inventory.setItem(SLOT_MIN_ROLLS, buildControl(
                Material.HOPPER,
                "&7« &#F8BEFBМинимум наград: &f" + keyType.getMinRolls() + " &7»",
                List.of(
                        "&#F8BEFB&l┃ ",
                        "&#F8BEFB&l┃ &fСейчас: &#F8BEFBот " + keyType.getMinRolls() + " до " + keyType.getMaxRolls(),
                        "&#F8BEFB&l┃ ",
                        "&7● &fЛКМ: &#F8BEFB+1 &8| &fПКМ: &#F8BEFB-1"
                ),
                false
        ));

        inventory.setItem(SLOT_MAX_ROLLS, buildControl(
                Material.COMPARATOR,
                "&7« &#F8BEFBМаксимум наград: &f" + keyType.getMaxRolls() + " &7»",
                List.of(
                        "&#F8BEFB&l┃ ",
                        "&#F8BEFB&l┃ &fСейчас: &#F8BEFBот " + keyType.getMinRolls() + " до " + keyType.getMaxRolls(),
                        "&#F8BEFB&l┃ ",
                        "&7● &fЛКМ: &#F8BEFB+1 &8| &fПКМ: &#F8BEFB-1"
                ),
                false
        ));

        inventory.setItem(SLOT_GIVE_KEY, buildControl(
                keyType.getKeyMaterial(),
                "&7« " + keyType.getDisplayName() + " &7»",
                List.of(
                        "&#F8BEFB&l┃ ",
                        "&#F8BEFB&l┃ &fПредмет: &#F8BEFB" + keyType.getKeyMaterialTitle(),
                        "&#F8BEFB&l┃ &fНаград в пуле: &#F8BEFB" + keyType.getEntries().size(),
                        "&#F8BEFB&l┃ &fСтраница: &#F8BEFB" + (page + 1) + "/" + (maxPage + 1),
                        "&#F8BEFB&l┃ ",
                        "&7● &fЛКМ - получить ключ x1 &8| &fПКМ - x5",
                        "&7● &fКлик по предмету в своём инвентаре - добавить"
                ),
                true
        ));

        inventory.setItem(SLOT_LEVEL, buildControl(
                Material.AMETHYST_SHARD,
                "&7« &#F8BEFBУровень лута: &f" + keyType.getLevel() + "/5 &7»",
                List.of(
                        "&#F8BEFB&l┃ ",
                        "&#F8BEFB&l┃ &f1-3 уровень: &7Ключ испытаний",
                        "&#F8BEFB&l┃ &f4-5 уровень: &7Зловещий ключ",
                        "&#F8BEFB&l┃ ",
                        "&7● &fЛКМ: &#F8BEFB+1 &8| &fПКМ: &#F8BEFB-1"
                ),
                false
        ));

        if (hasNextPage()) {
            inventory.setItem(SLOT_NEXT_PAGE, buildControl(
                    Material.BLACK_DYE,
                    "&7« &#F8BEFBСледующая страница &7»",
                    List.of(
                            "&#F8BEFB&l┃ ",
                            "&#F8BEFB&l┃ &fПерейти на страницу &#F8BEFB" + (page + 2),
                            "&#F8BEFB&l┃ ",
                            "&7● &fНажмите для перехода"
                    ),
                    false
            ));
        }

        inventory.setItem(SLOT_CLOSE, buildControl(
                Material.RED_DYE,
                "&7« &#F8BEFBЗакрыть &7»",
                List.of(
                        "&#F8BEFB&l┃ ",
                        "&#F8BEFB&l┃ &fСохранить и закрыть редактор.",
                        "&#F8BEFB&l┃ ",
                        "&7● &fНажмите для закрытия"
                ),
                false
        ));
    }

    private ItemStack buildEntryIcon(LootEntry entry, Map<Rarity, Integer> weights, int totalWeight) {
        ItemStack icon = entry.getPreviewItem();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            int weight = entry.getEffectiveWeight(weights);
            double chance = (weight * 100.0) / totalWeight;
            String rarColor = plugin.getKeyRegistry().getRarityColor(entry.getRarity());
            String rarName = plugin.getKeyRegistry().getRarityName(entry.getRarity());

            lore.add(TextUtil.color("&#F8BEFB&l┃ "));
            lore.add(TextUtil.color("&#F8BEFB&l┃ &fРедкость: " + rarColor + rarName));
            lore.add(TextUtil.color("&#F8BEFB&l┃ &fШанс: &#F8BEFB"
                    + String.format(Locale.US, "%.1f%%", chance) + " &7(вес: " + weight + ")"));
            if (entry.hasCommand()) {
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fТип: &7предмет /shop"));
            }
            lore.add(TextUtil.color("&#F8BEFB&l┃ "));
            lore.add(TextUtil.color("&7● &fЛКМ - сменить редкость"));
            lore.add(TextUtil.color("&7● &fShift + ЛКМ: &a+2 &8| &fПКМ: &c-1 &fк весу"));
            lore.add(TextUtil.color("&7● &fShift + ПКМ или Q - удалить"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static ItemStack buildControl(Material mat, String name, List<String> loreLines, boolean glint) {
        ItemStack st = new ItemStack(mat, 1);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(TextUtil.colorList(loreLines));
            if (glint) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
            meta.addItemFlags(ItemFlag.values());
            st.setItemMeta(meta);
        }
        return st;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
