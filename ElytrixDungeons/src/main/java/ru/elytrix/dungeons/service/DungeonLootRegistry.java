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

/** Vanilla-only default loot tables; old shop/PlayerKits ids are never resolved on use. */
public final class DungeonLootRegistry {

    private static final int LOOT_VERSION = 2;

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
        if (yml.getInt("loot-version", 0) < LOOT_VERSION) {
            // The earlier generated defaults referenced shop and kit ids that are not guaranteed
            // to exist. Upgrade that generated table once to reliable vanilla item stacks.
            seedDefaults();
            save();
            return;
        }

        for (LootPoolType type : LootPoolType.values()) {
            ConfigurationSection sec = yml.getConfigurationSection("pools." + type.name());
            if (sec == null) continue;
            List<DungeonLootEntry> list = pools.get(type);
            for (String key : sec.getKeys(false)) {
                ConfigurationSection entry = sec.getConfigurationSection(key);
                if (entry == null) continue;
                SourceKind kind;
                try {
                    kind = SourceKind.valueOf(entry.getString("kind", "ITEM_STACK"));
                } catch (IllegalArgumentException exception) {
                    kind = SourceKind.ITEM_STACK;
                }
                String externalId = entry.getString("external-id", "");
                ItemStack stack = entry.getItemStack("item");
                if (stack == null || stack.getType().isAir()) stack = new ItemStack(Material.DIAMOND);
                list.add(new DungeonLootEntry(
                        key,
                        kind,
                        externalId,
                        stack,
                        entry.getDouble("chance", 40.0),
                        entry.getInt("min", 1),
                        entry.getInt("max", 1),
                        entry.getBoolean("guaranteed", false)
                ));
            }
        }
        ensureMissingPoolsHaveDefaults();
    }

    private void ensureMissingPoolsHaveDefaults() {
        boolean hasEmptyPool = pools.values().stream().anyMatch(List::isEmpty);
        if (!hasEmptyPool) return;

        EnumMap<LootPoolType, List<DungeonLootEntry>> configured = new EnumMap<>(LootPoolType.class);
        for (LootPoolType type : LootPoolType.values()) {
            configured.put(type, new ArrayList<>(pools.get(type)));
            pools.get(type).clear();
        }
        seedDefaults();
        for (LootPoolType type : LootPoolType.values()) {
            if (configured.get(type).isEmpty()) continue;
            pools.get(type).clear();
            pools.get(type).addAll(configured.get(type));
        }
        save();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("loot-version", LOOT_VERSION);
        for (LootPoolType type : LootPoolType.values()) {
            String base = "pools." + type.name();
            int i = 0;
            for (DungeonLootEntry entry : pools.get(type)) {
                String path = base + ".item_" + (i++);
                yml.set(path + ".kind", entry.getSourceKind().name());
                yml.set(path + ".external-id", entry.getExternalId());
                yml.set(path + ".item", entry.getFallbackStack());
                yml.set(path + ".chance", entry.getChancePercent());
                yml.set(path + ".min", entry.getMinAmount());
                yml.set(path + ".max", entry.getMaxAmount());
                yml.set(path + ".guaranteed", entry.isGuaranteed());
            }
        }
        try {
            yml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить loot.yml: " + exception.getMessage());
        }
    }

    public List<DungeonLootEntry> getPool(LootPoolType type) {
        return pools.computeIfAbsent(type, key -> new ArrayList<>());
    }

    public void fillContainerRandomSlots(Inventory inventory, LootPoolType poolType, int targetStacks) {
        inventory.clear();
        List<ItemStack> items = rollStacks(poolType, targetStacks);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int size = inventory.getSize();
        for (ItemStack item : items) {
            for (int tries = 0; tries < 14; tries++) {
                int slot = random.nextInt(size);
                ItemStack current = inventory.getItem(slot);
                if (current == null || current.getType().isAir()) {
                    inventory.setItem(slot, item);
                    break;
                }
            }
        }
    }

    public List<ItemStack> rollStacks(LootPoolType type, int targetStacks) {
        List<DungeonLootEntry> entries = getPool(type);
        List<ItemStack> output = new ArrayList<>();
        if (entries.isEmpty()) {
            output.add(new ItemStack(Material.GOLDEN_APPLE));
            return output;
        }
        targetStacks = Math.max(1, targetStacks);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (DungeonLootEntry entry : entries) {
            if (entry.isGuaranteed()) {
                output.add(materialize(entry, random));
            }
        }
        int guard = 0;
        while (output.size() < targetStacks && guard++ < 96) {
            DungeonLootEntry candidate = entries.get(random.nextInt(entries.size()));
            if (candidate.isGuaranteed()) continue;
            if (random.nextDouble(100.0) <= candidate.getChancePercent()) {
                output.add(materialize(candidate, random));
            }
        }
        // A custom pool with zero chances must still never leave a loot container completely empty.
        if (output.isEmpty()) {
            output.add(materialize(entries.get(random.nextInt(entries.size())), random));
        }
        return output;
    }

    public ItemStack materializePreview(DungeonLootEntry entry) {
        return materialize(entry, ThreadLocalRandom.current());
    }

    private ItemStack materialize(DungeonLootEntry entry, ThreadLocalRandom random) {
        int amount = entry.getMinAmount() == entry.getMaxAmount()
                ? entry.getMinAmount()
                : random.nextInt(entry.getMinAmount(), entry.getMaxAmount() + 1);
        return switch (entry.getSourceKind()) {
            case VAULT_KEY -> plugin.getItemsHook().createVaultKey(entry.getExternalId(), amount);
            // Legacy ELYTRIX_ITEM entries are rendered as their saved vanilla fallback stack;
            // no lookup against /shop, ElytrixItem, or PlayerKits is attempted.
            case ELYTRIX_ITEM, ITEM_STACK -> {
                ItemStack stack = entry.getFallbackStack().clone();
                stack.setAmount(Math.min(stack.getMaxStackSize(), amount));
                yield stack;
            }
        };
    }

    private void seedDefaults() {
        addSurfaceChestLoot();
        addSurfaceShulkerLoot();
        addUndergroundLoot();
    }

    private void addSurfaceChestLoot() {
        List<DungeonLootEntry> chest = pools.get(LootPoolType.SURFACE_CHEST);
        chest.add(item("house_gapples", new ItemStack(Material.GOLDEN_APPLE), 90.0, 1, 3, true));
        chest.add(item("house_diamonds", new ItemStack(Material.DIAMOND), 72.0, 2, 8, false));
        chest.add(item("house_emeralds", new ItemStack(Material.EMERALD), 72.0, 4, 16, false));
        chest.add(item("house_xp", new ItemStack(Material.EXPERIENCE_BOTTLE), 75.0, 8, 24, false));
        chest.add(item("house_pearl", new ItemStack(Material.ENDER_PEARL), 68.0, 2, 8, false));
        chest.add(item("house_golden_carrot", new ItemStack(Material.GOLDEN_CARROT), 60.0, 4, 12, false));
        chest.add(item("house_iron", new ItemStack(Material.IRON_INGOT), 78.0, 8, 24, false));
        chest.add(item("house_gold", new ItemStack(Material.GOLD_INGOT), 70.0, 5, 16, false));
        chest.add(item("house_obsidian", new ItemStack(Material.OBSIDIAN), 48.0, 4, 12, false));
        chest.add(item("house_shells", new ItemStack(Material.SHULKER_SHELL), 26.0, 1, 3, false));
        chest.add(item("house_scrap", new ItemStack(Material.NETHERITE_SCRAP), 20.0, 1, 2, false));
        chest.add(item("house_totem", new ItemStack(Material.TOTEM_OF_UNDYING), 9.0, 1, 1, false));
        chest.add(item("house_enchanted_apple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 3.0, 1, 1, false));
        chest.add(item("house_book_mending", VaultAndItemsHook.enchantedBook(Enchantment.MENDING, 1), 16.0, 1, 1, false));
        chest.add(item("house_chestplate", VaultAndItemsHook.enchantedGear(Material.DIAMOND_CHESTPLATE,
                "&7« &#96FF91Нагрудник Искателя &7»",
                new MapEnchant(Enchantment.PROTECTION, 4), new MapEnchant(Enchantment.UNBREAKING, 3)), 12.0, 1, 1, false));
        chest.add(item("house_sword", VaultAndItemsHook.enchantedGear(Material.DIAMOND_SWORD,
                "&7« &#96FF91Клинок Искателя &7»",
                new MapEnchant(Enchantment.SHARPNESS, 5), new MapEnchant(Enchantment.UNBREAKING, 3)), 12.0, 1, 1, false));
        chest.add(item("house_saddle", new ItemStack(Material.SADDLE), 26.0, 1, 1, false));
    }

    private void addSurfaceShulkerLoot() {
        List<DungeonLootEntry> common = pools.get(LootPoolType.SURFACE_SHULKER_COMMON);
        common.add(item("common_gapples", new ItemStack(Material.GOLDEN_APPLE), 80.0, 1, 3, true));
        common.add(item("common_diamonds", new ItemStack(Material.DIAMOND), 72.0, 3, 9, false));
        common.add(item("common_emeralds", new ItemStack(Material.EMERALD), 76.0, 6, 20, false));
        common.add(item("common_xp", new ItemStack(Material.EXPERIENCE_BOTTLE), 78.0, 12, 32, false));
        common.add(item("common_pearls", new ItemStack(Material.ENDER_PEARL), 72.0, 4, 12, false));
        common.add(item("common_iron", new ItemStack(Material.IRON_INGOT), 82.0, 12, 32, false));
        common.add(item("common_gold", new ItemStack(Material.GOLD_INGOT), 75.0, 8, 24, false));
        common.add(item("common_carrots", new ItemStack(Material.GOLDEN_CARROT), 70.0, 8, 20, false));
        common.add(item("common_obsidian", new ItemStack(Material.OBSIDIAN), 54.0, 8, 20, false));
        common.add(item("common_shells", new ItemStack(Material.SHULKER_SHELL), 28.0, 1, 3, false));
        common.add(item("common_mending_book", VaultAndItemsHook.enchantedBook(Enchantment.UNBREAKING, 3), 8.0, 1, 1, false));

        List<DungeonLootEntry> uncommon = pools.get(LootPoolType.SURFACE_SHULKER_UNCOMMON);
        uncommon.add(item("uncommon_gapples", new ItemStack(Material.GOLDEN_APPLE), 84.0, 2, 5, true));
        uncommon.add(item("uncommon_diamonds", new ItemStack(Material.DIAMOND), 78.0, 6, 16, false));
        uncommon.add(item("uncommon_emeralds", new ItemStack(Material.EMERALD), 78.0, 10, 24, false));
        uncommon.add(item("uncommon_xp", new ItemStack(Material.EXPERIENCE_BOTTLE), 76.0, 16, 40, false));
        uncommon.add(item("uncommon_pearl", new ItemStack(Material.ENDER_PEARL), 72.0, 8, 16, false));
        uncommon.add(item("uncommon_scrap", new ItemStack(Material.NETHERITE_SCRAP), 42.0, 1, 3, false));
        uncommon.add(item("uncommon_shells", new ItemStack(Material.SHULKER_SHELL), 52.0, 2, 5, false));
        uncommon.add(item("uncommon_totem", new ItemStack(Material.TOTEM_OF_UNDYING), 18.0, 1, 1, false));
        uncommon.add(item("uncommon_book", VaultAndItemsHook.enchantedBook(Enchantment.MENDING, 1), 28.0, 1, 1, false));
        uncommon.add(item("uncommon_chestplate", VaultAndItemsHook.enchantedGear(Material.DIAMOND_CHESTPLATE,
                "&7« &#8CFF91Броня Следопыта &7»",
                new MapEnchant(Enchantment.PROTECTION, 4), new MapEnchant(Enchantment.UNBREAKING, 3)), 24.0, 1, 1, false));

        List<DungeonLootEntry> rare = pools.get(LootPoolType.SURFACE_SHULKER_RARE);
        rare.add(item("rare_gapples", new ItemStack(Material.GOLDEN_APPLE), 85.0, 3, 7, true));
        rare.add(item("rare_enchanted_apple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 20.0, 1, 2, false));
        rare.add(item("rare_diamond_blocks", new ItemStack(Material.DIAMOND_BLOCK), 66.0, 1, 4, false));
        rare.add(item("rare_emerald_blocks", new ItemStack(Material.EMERALD_BLOCK), 66.0, 2, 6, false));
        rare.add(item("rare_netherite", new ItemStack(Material.NETHERITE_INGOT), 38.0, 1, 2, false));
        rare.add(item("rare_totem", new ItemStack(Material.TOTEM_OF_UNDYING), 34.0, 1, 2, false));
        rare.add(item("rare_shells", new ItemStack(Material.SHULKER_SHELL), 62.0, 3, 8, false));
        rare.add(item("rare_echo", new ItemStack(Material.ECHO_SHARD), 40.0, 1, 3, false));
        rare.add(item("rare_key", new ItemStack(Material.TRIAL_KEY), 35.0, 1, 2, false));
        rare.add(item("rare_book", VaultAndItemsHook.enchantedBook(Enchantment.MENDING, 1), 44.0, 1, 1, false));
        rare.add(item("rare_sword", VaultAndItemsHook.enchantedGear(Material.DIAMOND_SWORD,
                "&7« &#72C9FFКлинок Искателя &7»",
                new MapEnchant(Enchantment.SHARPNESS, 5), new MapEnchant(Enchantment.MENDING, 1),
                new MapEnchant(Enchantment.UNBREAKING, 3)), 28.0, 1, 1, false));

        List<DungeonLootEntry> epic = pools.get(LootPoolType.SURFACE_SHULKER_EPIC);
        epic.add(item("epic_gapples", new ItemStack(Material.GOLDEN_APPLE), 86.0, 4, 8, true));
        epic.add(item("epic_enchanted_apple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 44.0, 1, 3, false));
        epic.add(item("epic_diamond_blocks", new ItemStack(Material.DIAMOND_BLOCK), 74.0, 2, 6, false));
        epic.add(item("epic_netherite", new ItemStack(Material.NETHERITE_INGOT), 60.0, 1, 3, false));
        epic.add(item("epic_totems", new ItemStack(Material.TOTEM_OF_UNDYING), 52.0, 1, 3, false));
        epic.add(item("epic_shulker_shells", new ItemStack(Material.SHULKER_SHELL), 68.0, 5, 12, false));
        epic.add(item("epic_ominous_key", new ItemStack(Material.OMINOUS_TRIAL_KEY), 42.0, 1, 2, false));
        epic.add(item("epic_trident", VaultAndItemsHook.enchantedGear(Material.TRIDENT,
                "&7« &#C58BFFТрезубец Хранителя &7»",
                new MapEnchant(Enchantment.IMPALING, 5), new MapEnchant(Enchantment.UNBREAKING, 3)), 24.0, 1, 1, false));
        epic.add(item("epic_chestplate", VaultAndItemsHook.enchantedGear(Material.NETHERITE_CHESTPLATE,
                "&7« &#C58BFFНагрудник Хранителя &7»",
                new MapEnchant(Enchantment.PROTECTION, 4), new MapEnchant(Enchantment.UNBREAKING, 3)), 22.0, 1, 1, false));
        epic.add(item("epic_mending", VaultAndItemsHook.enchantedBook(Enchantment.MENDING, 1), 48.0, 1, 1, false));

        List<DungeonLootEntry> legendary = pools.get(LootPoolType.SURFACE_SHULKER_LEGENDARY);
        legendary.add(item("legendary_gapples", new ItemStack(Material.GOLDEN_APPLE), 86.0, 6, 12, true));
        legendary.add(item("legendary_enchanted_apple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 56.0, 2, 4, false));
        legendary.add(item("legendary_netherite_blocks", new ItemStack(Material.NETHERITE_BLOCK), 28.0, 1, 2, false));
        legendary.add(item("legendary_netherite_ingots", new ItemStack(Material.NETHERITE_INGOT), 72.0, 2, 5, false));
        legendary.add(item("legendary_totems", new ItemStack(Material.TOTEM_OF_UNDYING), 68.0, 2, 5, false));
        legendary.add(item("legendary_elytra", VaultAndItemsHook.enchantedGear(Material.ELYTRA,
                "&7« &#FFBD59Элитры Скитальца &7»",
                new MapEnchant(Enchantment.UNBREAKING, 3), new MapEnchant(Enchantment.MENDING, 1)), 14.0, 1, 1, false));
        legendary.add(item("legendary_mace", VaultAndItemsHook.enchantedGear(Material.MACE,
                "&7« &#FFBD59Булава Покорителя &7»",
                new MapEnchant(Enchantment.DENSITY, 4), new MapEnchant(Enchantment.UNBREAKING, 3)), 12.0, 1, 1, false));
        legendary.add(item("legendary_ominous_keys", new ItemStack(Material.OMINOUS_TRIAL_KEY), 60.0, 2, 4, false));
        legendary.add(item("legendary_template", new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 34.0, 1, 2, false));
        legendary.add(item("legendary_shells", new ItemStack(Material.SHULKER_SHELL), 62.0, 8, 16, false));

        List<DungeonLootEntry> mythic = pools.get(LootPoolType.SURFACE_SHULKER_MYTHIC);
        mythic.add(item("mythic_gapples", new ItemStack(Material.GOLDEN_APPLE), 90.0, 8, 16, true));
        mythic.add(item("mythic_enchanted_apples", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 74.0, 3, 8, false));
        mythic.add(item("mythic_netherite_blocks", new ItemStack(Material.NETHERITE_BLOCK), 58.0, 1, 4, false));
        mythic.add(item("mythic_netherite_ingots", new ItemStack(Material.NETHERITE_INGOT), 80.0, 4, 10, false));
        mythic.add(item("mythic_totems", new ItemStack(Material.TOTEM_OF_UNDYING), 78.0, 4, 10, false));
        mythic.add(item("mythic_elytra", VaultAndItemsHook.enchantedGear(Material.ELYTRA,
                "&7« &#FF5E8AКрылья Правды &7»",
                new MapEnchant(Enchantment.UNBREAKING, 3), new MapEnchant(Enchantment.MENDING, 1)), 32.0, 1, 1, false));
        mythic.add(item("mythic_mace", VaultAndItemsHook.enchantedGear(Material.MACE,
                "&7« &#FF5E8AБулава Разлома &7»",
                new MapEnchant(Enchantment.DENSITY, 5), new MapEnchant(Enchantment.UNBREAKING, 3),
                new MapEnchant(Enchantment.MENDING, 1)), 26.0, 1, 1, false));
        mythic.add(item("mythic_netherite_chestplate", VaultAndItemsHook.enchantedGear(Material.NETHERITE_CHESTPLATE,
                "&7« &#FF5E8AДоспех Правды &7»",
                new MapEnchant(Enchantment.PROTECTION, 5), new MapEnchant(Enchantment.UNBREAKING, 4),
                new MapEnchant(Enchantment.MENDING, 1)), 36.0, 1, 1, false));
        mythic.add(item("mythic_ominous_keys", new ItemStack(Material.OMINOUS_TRIAL_KEY), 72.0, 4, 8, false));
        mythic.add(item("mythic_templates", new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 55.0, 2, 4, false));
    }

    private void addUndergroundLoot() {
        List<DungeonLootEntry> boss = pools.get(LootPoolType.UNDERGROUND_SHULKER);
        boss.add(item("boss_trial_keys", new ItemStack(Material.TRIAL_KEY), 100.0, 2, 5, true));
        boss.add(item("boss_ominous_keys", new ItemStack(Material.OMINOUS_TRIAL_KEY), 48.0, 1, 3, false));
        boss.add(item("boss_netherite", new ItemStack(Material.NETHERITE_INGOT), 68.0, 1, 4, false));
        boss.add(item("boss_enchanted_apples", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 40.0, 1, 3, false));
        boss.add(item("boss_totems", new ItemStack(Material.TOTEM_OF_UNDYING), 62.0, 1, 4, false));
        boss.add(item("boss_diamond_blocks", new ItemStack(Material.DIAMOND_BLOCK), 68.0, 2, 8, false));
        boss.add(item("boss_emerald_blocks", new ItemStack(Material.EMERALD_BLOCK), 68.0, 4, 12, false));
        boss.add(item("boss_mace", VaultAndItemsHook.enchantedGear(Material.MACE,
                "&7« &#F8BEFBБулава Стража &7»",
                new MapEnchant(Enchantment.DENSITY, 4), new MapEnchant(Enchantment.UNBREAKING, 3)), 24.0, 1, 1, false));
        boss.add(item("boss_netherite_armor", VaultAndItemsHook.enchantedGear(Material.NETHERITE_HELMET,
                "&7« &#F8BEFBШлем Стража &7»",
                new MapEnchant(Enchantment.PROTECTION, 5), new MapEnchant(Enchantment.UNBREAKING, 4)), 22.0, 1, 1, false));
        boss.add(item("boss_mending", VaultAndItemsHook.enchantedBook(Enchantment.MENDING, 1), 54.0, 1, 1, false));
        boss.add(item("boss_elytra", VaultAndItemsHook.enchantedGear(Material.ELYTRA,
                "&7« &#F8BEFBЭлитры Хранителя &7»",
                new MapEnchant(Enchantment.UNBREAKING, 3), new MapEnchant(Enchantment.MENDING, 1)), 14.0, 1, 1, false));

        List<DungeonLootEntry> side = pools.get(LootPoolType.UNDERGROUND_CONTAINER);
        side.add(item("side_gapples", new ItemStack(Material.GOLDEN_APPLE), 86.0, 2, 6, true));
        side.add(item("side_diamonds", new ItemStack(Material.DIAMOND), 70.0, 4, 12, false));
        side.add(item("side_emeralds", new ItemStack(Material.EMERALD), 66.0, 6, 18, false));
        side.add(item("side_pearl", new ItemStack(Material.ENDER_PEARL), 70.0, 3, 10, false));
        side.add(item("side_xp", new ItemStack(Material.EXPERIENCE_BOTTLE), 76.0, 12, 32, false));
        side.add(item("side_scrap", new ItemStack(Material.NETHERITE_SCRAP), 42.0, 1, 3, false));
        side.add(item("side_shells", new ItemStack(Material.SHULKER_SHELL), 42.0, 1, 4, false));
        side.add(item("side_totem", new ItemStack(Material.TOTEM_OF_UNDYING), 18.0, 1, 1, false));
        side.add(item("side_protection_book", VaultAndItemsHook.enchantedBook(Enchantment.PROTECTION, 4), 28.0, 1, 1, false));
        side.add(item("side_mending_book", VaultAndItemsHook.enchantedBook(Enchantment.MENDING, 1), 18.0, 1, 1, false));
    }

    private static DungeonLootEntry item(
            String id, ItemStack stack, double chance, int min, int max, boolean guaranteed
    ) {
        return new DungeonLootEntry(id, SourceKind.ITEM_STACK, "", stack, chance, min, max, guaranteed);
    }
}
