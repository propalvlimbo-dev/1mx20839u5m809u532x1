package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.hook.VaultAndItemsHook;
import ru.elytrix.dungeons.hook.VaultAndItemsHook.MapEnchant;
import ru.elytrix.dungeons.model.DungeonLootEntry;
import ru.elytrix.dungeons.model.DungeonLootEntry.SourceKind;
import ru.elytrix.dungeons.model.LootPoolType;

public final class DungeonLootRegistry {

    private final ElytrixDungeonsPlugin plugin;
    private final File file;
    private final Map<LootPoolType, List<DungeonLootEntry>> pools = new EnumMap<>(LootPoolType.class);

    public DungeonLootRegistry(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loot.yml");
    }

    public void load() {
        pools.clear();
        for (LootPoolType type : LootPoolType.values()) {
            pools.put(type, new ArrayList<>());
        }
        if (!file.exists()) {
            seedDefaults();
            save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (LootPoolType type : LootPoolType.values()) {
            ConfigurationSection sec = yml.getConfigurationSection("pools." + type.name());
            if (sec == null) continue;
            List<DungeonLootEntry> list = pools.get(type);
            for (String key : sec.getKeys(false)) {
                ConfigurationSection e = sec.getConfigurationSection(key);
                if (e == null) continue;
                SourceKind kind;
                try {
                    kind = SourceKind.valueOf(e.getString("kind", "ITEM_STACK"));
                } catch (IllegalArgumentException ex) {
                    kind = SourceKind.ITEM_STACK;
                }
                String ext = e.getString("external-id", "");
                ItemStack stack = e.getItemStack("item");
                if (stack == null) stack = new ItemStack(Material.DIAMOND);
                list.add(new DungeonLootEntry(
                        key,
                        kind,
                        ext,
                        stack,
                        e.getDouble("chance", 40.0),
                        e.getInt("min", 1),
                        e.getInt("max", 1),
                        e.getBoolean("guaranteed", false)
                ));
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (LootPoolType type : LootPoolType.values()) {
            String base = "pools." + type.name();
            int i = 0;
            for (DungeonLootEntry e : pools.get(type)) {
                String path = base + ".item_" + (i++);
                yml.set(path + ".kind", e.getSourceKind().name());
                yml.set(path + ".external-id", e.getExternalId());
                yml.set(path + ".item", e.getFallbackStack());
                yml.set(path + ".chance", e.getChancePercent());
                yml.set(path + ".min", e.getMinAmount());
                yml.set(path + ".max", e.getMaxAmount());
                yml.set(path + ".guaranteed", e.isGuaranteed());
            }
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось сохранить loot.yml: " + ex.getMessage());
        }
    }

    public List<DungeonLootEntry> getPool(LootPoolType type) {
        return pools.computeIfAbsent(type, k -> new ArrayList<>());
    }

    public void fillContainerRandomSlots(Inventory inv, LootPoolType poolType, int targetStacks) {
        inv.clear();
        List<ItemStack> items = rollStacks(poolType, targetStacks);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int size = inv.getSize();
        for (ItemStack item : items) {
            for (int tries = 0; tries < 14; tries++) {
                int slot = rng.nextInt(size);
                ItemStack cur = inv.getItem(slot);
                if (cur == null || cur.getType().isAir()) {
                    inv.setItem(slot, item);
                    break;
                }
            }
        }
    }

    public List<ItemStack> rollStacks(LootPoolType type, int targetStacks) {
        List<DungeonLootEntry> entries = getPool(type);
        List<ItemStack> out = new ArrayList<>();
        if (entries.isEmpty()) return out;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (DungeonLootEntry e : entries) {
            if (e.isGuaranteed()) {
                out.add(materialize(e, rng));
            }
        }
        int guard = 0;
        while (out.size() < targetStacks && guard++ < 48) {
            DungeonLootEntry cand = entries.get(rng.nextInt(entries.size()));
            if (cand.isGuaranteed()) continue;
            if (rng.nextDouble(100.0) <= cand.getChancePercent()) {
                out.add(materialize(cand, rng));
            }
        }
        return out;
    }

    public ItemStack materializePreview(DungeonLootEntry e) {
        return materialize(e, ThreadLocalRandom.current());
    }

    private ItemStack materialize(DungeonLootEntry e, ThreadLocalRandom rng) {
        int amt = e.getMinAmount() == e.getMaxAmount()
                ? e.getMinAmount()
                : rng.nextInt(e.getMinAmount(), e.getMaxAmount() + 1);
        return switch (e.getSourceKind()) {
            case VAULT_KEY -> plugin.getItemsHook().createVaultKey(e.getExternalId(), amt);
            case ELYTRIX_ITEM -> plugin.getItemsHook().resolveShopOrKitItem(e.getExternalId(), amt);
            case ITEM_STACK -> {
                ItemStack st = e.getFallbackStack().clone();
                st.setAmount(Math.min(st.getMaxStackSize(), amt));
                yield st;
            }
        };
    }

    private void seedDefaults() {
        // 1. SURFACE_CHEST — Сундуки в Наземном Доме (вещи из /shop, китов и ПвП-ресурсы)
        List<DungeonLootEntry> house = pools.get(LootPoolType.SURFACE_CHEST);
        house.add(item("sc_gap", new ItemStack(Material.GOLDEN_APPLE), 80.0, 2, 7, true));
        house.add(item("sc_pearl", new ItemStack(Material.ENDER_PEARL), 70.0, 2, 8, false));
        house.add(item("sc_totem", new ItemStack(Material.TOTEM_OF_UNDYING), 32.0, 1, 1, false));
        house.add(item("sc_diam", new ItemStack(Material.DIAMOND), 65.0, 3, 10, false));
        house.add(item("sc_em", new ItemStack(Material.EMERALD), 60.0, 4, 14, false));
        house.add(item("sc_exp", new ItemStack(Material.EXPERIENCE_BOTTLE), 65.0, 8, 24, false));
        house.add(item("sc_scrap", new ItemStack(Material.NETHERITE_SCRAP), 24.0, 1, 2, false));
        house.add(shopItem("sc_pot_war", "potion_voina", 36.0, 1, 2, false));
        house.add(shopItem("sc_pot_heal", "potion_healt", 38.0, 1, 2, false));
        house.add(shopItem("sc_trap", "explosive_trap", 18.0, 1, 2, false));
        house.add(shopItem("sc_anti", "anti_pearl", 20.0, 1, 2, false));
        house.add(item("sc_kit_chest", VaultAndItemsHook.enchantedGear(Material.DIAMOND_CHESTPLATE,
                "&7« &#96FF91Нагрудник Искателя &7»",
                new MapEnchant(Enchantment.PROTECTION, 4), new MapEnchant(Enchantment.UNBREAKING, 3)), 22.0, 1, 1, false));
        house.add(item("sc_kit_sword", VaultAndItemsHook.enchantedGear(Material.DIAMOND_SWORD,
                "&7« &#96FF91Клинок Искателя &7»",
                new MapEnchant(Enchantment.SHARPNESS, 5), new MapEnchant(Enchantment.UNBREAKING, 3)), 20.0, 1, 1, false));

        // 2. UNDERGROUND_SHULKER — Главный шалкер Подземного данжа (после убийства стражей!)
        List<DungeonLootEntry> ugBoss = pools.get(LootPoolType.UNDERGROUND_SHULKER);
        ugBoss.add(vaultKey("ug_royal_key", "royal", 100.0, 1, 1, true));
        ugBoss.add(vaultKey("ug_void_key", "void", 38.0, 1, 1, false));
        ugBoss.add(vaultKey("ug_unique_key", "unique", 18.0, 1, 1, false));
        ugBoss.add(shopItem("ug_shulker_sword", "shulker_sword", 26.0, 1, 1, false));
        ugBoss.add(shopItem("ug_shulker_pick", "shulker_pickaxe", 24.0, 1, 1, false));
        ugBoss.add(shopItem("ug_kit_helm", "kit_helmet", 30.0, 1, 1, false));
        ugBoss.add(shopItem("ug_kit_boots", "kit_boots", 30.0, 1, 1, false));
        ugBoss.add(shopItem("ug_doc_totem", "totem1", 32.0, 1, 1, false));
        ugBoss.add(shopItem("ug_snake_orb", "shar1", 16.0, 1, 1, false));
        ugBoss.add(shopItem("ug_salv_pot", "potion_salvation", 52.0, 1, 2, false));
        ugBoss.add(shopItem("ug_war_pot", "potion_voina", 55.0, 1, 2, false));
        ugBoss.add(item("ug_egap", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 44.0, 1, 2, false));
        ugBoss.add(item("ug_ingot", new ItemStack(Material.NETHERITE_INGOT), 45.0, 1, 2, false));
        ugBoss.add(item("ug_totem", new ItemStack(Material.TOTEM_OF_UNDYING), 65.0, 1, 2, false));

        // 3. UNDERGROUND_CONTAINER — Боковые сундуки и бочки Подземного данжа
        List<DungeonLootEntry> ugSide = pools.get(LootPoolType.UNDERGROUND_CONTAINER);
        ugSide.add(item("ugs_gap", new ItemStack(Material.GOLDEN_APPLE), 80.0, 2, 6, true));
        ugSide.add(item("ugs_diam", new ItemStack(Material.DIAMOND), 65.0, 4, 12, false));
        ugSide.add(item("ugs_scrap", new ItemStack(Material.NETHERITE_SCRAP), 35.0, 1, 3, false));
        ugSide.add(item("ugs_pearl", new ItemStack(Material.ENDER_PEARL), 65.0, 2, 8, false));
        ugSide.add(item("ugs_xp", new ItemStack(Material.EXPERIENCE_BOTTLE), 70.0, 10, 28, false));
        ugSide.add(item("ugs_prot4", VaultAndItemsHook.enchantedBook(Enchantment.PROTECTION, 4), 28.0, 1, 1, false));
        ugSide.add(vaultKey("ugs_know_key", "knowledge", 16.0, 1, 1, false));
    }

    private DungeonLootEntry vaultKey(String id, String keyId, double chance, int min, int max, boolean guar) {
        return new DungeonLootEntry(id, SourceKind.VAULT_KEY, keyId,
                plugin.getItemsHook().createVaultKey(keyId, 1), chance, min, max, guar);
    }

    private DungeonLootEntry shopItem(String id, String extId, double chance, int min, int max, boolean guar) {
        return new DungeonLootEntry(id, SourceKind.ELYTRIX_ITEM, extId,
                VaultAndItemsHook.buildCanonicalShopOrKitItem(extId), chance, min, max, guar);
    }

    private static DungeonLootEntry item(String id, ItemStack st, double chance, int min, int max, boolean guar) {
        return new DungeonLootEntry(id, SourceKind.ITEM_STACK, "", st, chance, min, max, guar);
    }
}
