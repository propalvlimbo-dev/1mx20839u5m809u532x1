package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.hook.VaultAndItemsHook;
import ru.elytrix.dungeons.hook.VaultAndItemsHook.MapEnchant;
import ru.elytrix.dungeons.model.DungeonLootEntry;
import ru.elytrix.dungeons.model.LootPoolType;

public final class DungeonLootRegistry {

    private static final String SNAKE_ORB_B64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFiNGIyMDE4OWQ5MDJiYzZjM2ZhYmUyM2Q5ZjU3ODY5N2Y1NDRjZjQxNTMxNzc4NzM3NDExODM5M2RhNCJ9fX0=";

    private final ElytrixDungeonsPlugin plugin;
    private final File lootFile;
    private final Map<LootPoolType, List<DungeonLootEntry>> pools = new EnumMap<>(LootPoolType.class);

    public DungeonLootRegistry(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "loot.yml");
    }

    public void load() {
        pools.clear();
        for (LootPoolType type : LootPoolType.values()) {
            pools.put(type, new ArrayList<>());
        }
        if (!lootFile.exists()) {
            populateDefaults();
            save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(lootFile);
        for (LootPoolType type : LootPoolType.values()) {
            readPool(yml.getConfigurationSection("pools." + type.name()), pools.get(type));
        }
    }

    private void readPool(ConfigurationSection sec, List<DungeonLootEntry> target) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection e = sec.getConfigurationSection(id);
            if (e == null) continue;
            DungeonLootEntry.SourceKind kind;
            try {
                kind = DungeonLootEntry.SourceKind.valueOf(e.getString("kind", "ITEM_STACK"));
            } catch (IllegalArgumentException ex) {
                kind = DungeonLootEntry.SourceKind.ITEM_STACK;
            }
            ItemStack item = e.getItemStack("item");
            if (item == null || item.getType().isAir()) {
                item = new ItemStack(Material.DIAMOND, 1);
            }
            target.add(new DungeonLootEntry(
                    id,
                    kind,
                    e.getString("external-id", ""),
                    item,
                    e.getDouble("chance", 35.0),
                    e.getInt("min", 1),
                    e.getInt("max", 1),
                    e.getBoolean("guaranteed", false)
            ));
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (LootPoolType type : LootPoolType.values()) {
            writePool(yml, "pools." + type.name(), pools.get(type));
        }
        try {
            yml.save(lootFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save loot.yml: " + ex.getMessage());
        }
    }

    private void writePool(YamlConfiguration yml, String basePath, List<DungeonLootEntry> list) {
        for (DungeonLootEntry e : list) {
            String p = basePath + "." + e.getId();
            yml.set(p + ".kind", e.getSourceKind().name());
            yml.set(p + ".external-id", e.getExternalId());
            yml.set(p + ".item", e.getFallbackStack());
            yml.set(p + ".chance", e.getChancePercent());
            yml.set(p + ".min", e.getMinAmount());
            yml.set(p + ".max", e.getMaxAmount());
            yml.set(p + ".guaranteed", e.isGuaranteed());
        }
    }

    public List<DungeonLootEntry> getPool(LootPoolType type) {
        return pools.get(type);
    }

    public ItemStack rollSingleItem(LootPoolType type) {
        List<DungeonLootEntry> list = pools.get(type);
        if (list == null || list.isEmpty()) {
            return new ItemStack(Material.DIAMOND, 4);
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double totalWeight = 0.0;
        for (DungeonLootEntry e : list) {
            totalWeight += Math.max(0.5, e.getChancePercent());
        }
        double r = rng.nextDouble(totalWeight);
        double cur = 0.0;
        for (DungeonLootEntry e : list) {
            cur += Math.max(0.5, e.getChancePercent());
            if (r <= cur) {
                return materialize(e, rng);
            }
        }
        return materialize(list.get(rng.nextInt(list.size())), rng);
    }

    public void fillContainerRandomSlots(Inventory inv, LootPoolType type, int maxStacks) {
        inv.clear();
        List<DungeonLootEntry> pool = pools.get(type);
        if (pool == null || pool.isEmpty()) {
            return;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<ItemStack> generated = new ArrayList<>();

        for (DungeonLootEntry entry : pool) {
            if (entry.isGuaranteed()) {
                generated.add(materialize(entry, rng));
            }
        }
        List<DungeonLootEntry> optional = new ArrayList<>();
        for (DungeonLootEntry entry : pool) {
            if (!entry.isGuaranteed()) optional.add(entry);
        }
        Collections.shuffle(optional, rng);
        for (DungeonLootEntry entry : optional) {
            if (generated.size() >= maxStacks) break;
            if (rng.nextDouble(100.0) < entry.getChancePercent()) {
                generated.add(materialize(entry, rng));
            }
        }
        if (generated.isEmpty()) {
            generated.add(materialize(pool.get(rng.nextInt(pool.size())), rng));
        }

        List<Integer> freeSlots = new ArrayList<>(inv.getSize());
        for (int s = 0; s < inv.getSize(); s++) {
            freeSlots.add(s);
        }
        Collections.shuffle(freeSlots, rng);
        int limit = Math.min(generated.size(), Math.min(maxStacks, freeSlots.size()));
        for (int i = 0; i < limit; i++) {
            inv.setItem(freeSlots.get(i), generated.get(i));
        }
    }

    public ItemStack materializePreview(DungeonLootEntry entry) {
        VaultAndItemsHook hook = plugin.getItemsHook();
        return switch (entry.getSourceKind()) {
            case VAULT_KEY -> hook.createVaultKey(entry.getExternalId(), entry.getMinAmount());
            case ELYTRIX_ITEM -> hook.resolveShopOrKitItem(entry.getExternalId(), entry.getMinAmount(), entry.getFallbackStack());
            case ITEM_STACK -> entry.getFallbackStack();
        };
    }

    private ItemStack materialize(DungeonLootEntry entry, ThreadLocalRandom rng) {
        int amt = entry.getMinAmount() == entry.getMaxAmount()
                ? entry.getMinAmount()
                : rng.nextInt(entry.getMinAmount(), entry.getMaxAmount() + 1);
        VaultAndItemsHook hook = plugin.getItemsHook();
        return switch (entry.getSourceKind()) {
            case VAULT_KEY -> hook.createVaultKey(entry.getExternalId(), amt);
            case ELYTRIX_ITEM -> hook.resolveShopOrKitItem(entry.getExternalId(), amt, entry.getFallbackStack());
            case ITEM_STACK -> {
                ItemStack st = entry.getFallbackStack();
                st.setAmount(Math.min(st.getMaxStackSize(), amt));
                yield st;
            }
        };
    }

    private void populateDefaults() {
        VaultAndItemsHook hook = plugin.getItemsHook();

        // 1. SURFACE_CHEST — Обычные сундуки Наземного данжа (основа + вещи из китов + расходники /shop)
        List<DungeonLootEntry> surfChest = pools.get(LootPoolType.SURFACE_CHEST);
        surfChest.add(stackEntry("sc_gapple", new ItemStack(Material.GOLDEN_APPLE, 1), 65.0, 3, 10, false));
        surfChest.add(stackEntry("sc_pearl", new ItemStack(Material.ENDER_PEARL, 1), 55.0, 4, 12, false));
        surfChest.add(stackEntry("sc_chorus", new ItemStack(Material.CHORUS_FRUIT, 1), 45.0, 8, 24, false));
        surfChest.add(stackEntry("sc_diamond", new ItemStack(Material.DIAMOND, 1), 50.0, 4, 12, false));
        surfChest.add(stackEntry("sc_exp", new ItemStack(Material.EXPERIENCE_BOTTLE, 1), 65.0, 12, 32, false));
        surfChest.add(stackEntry("sc_totem", new ItemStack(Material.TOTEM_OF_UNDYING, 1), 30.0, 1, 1, false));
        surfChest.add(stackEntry("sc_kit_sword", VaultAndItemsHook.enchantedGear(Material.DIAMOND_SWORD, null,
                new MapEnchant(Enchantment.SHARPNESS, 3), new MapEnchant(Enchantment.FIRE_ASPECT, 1), new MapEnchant(Enchantment.UNBREAKING, 2)),
                25.0, 1, 1, false));
        surfChest.add(stackEntry("sc_kit_chest", VaultAndItemsHook.enchantedGear(Material.DIAMOND_CHESTPLATE, null,
                new MapEnchant(Enchantment.PROTECTION, 3), new MapEnchant(Enchantment.UNBREAKING, 2)),
                22.0, 1, 1, false));
        surfChest.add(shopEntry("sc_pot_heal", "potion_healt",
                VaultAndItemsHook.customPotion(Material.SPLASH_POTION, 1, "&#FF6B8BЗелье исцеления", Color.fromRGB(255, 80, 120),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.INSTANT_HEALTH, 1, 1), VaultAndItemsHook.eff(PotionEffectType.REGENERATION, 20, 1))),
                32.0, 1, 2, false));
        surfChest.add(keyEntry("sc_key_rusty", "rusty", hook.createVaultKey("rusty", 1), 28.0, 1, 1, false));

        // 2. SURFACE_BARREL — Бочки Наземного данжа (базовые ресурсы + припасы китов)
        List<DungeonLootEntry> surfBarrel = pools.get(LootPoolType.SURFACE_BARREL);
        surfBarrel.add(stackEntry("sb_iron", new ItemStack(Material.IRON_INGOT, 1), 70.0, 12, 36, false));
        surfBarrel.add(stackEntry("sb_gold", new ItemStack(Material.GOLD_INGOT, 1), 60.0, 8, 24, false));
        surfBarrel.add(stackEntry("sb_arrows", new ItemStack(Material.ARROW, 1), 65.0, 16, 48, false));
        surfBarrel.add(stackEntry("sb_beef", new ItemStack(Material.COOKED_BEEF, 1), 65.0, 16, 48, false));
        surfBarrel.add(stackEntry("sb_obsidian", new ItemStack(Material.OBSIDIAN, 1), 50.0, 8, 24, false));
        surfBarrel.add(stackEntry("sb_slime", new ItemStack(Material.SLIME_BALL, 1), 40.0, 4, 12, false));
        surfBarrel.add(stackEntry("sb_firework", new ItemStack(Material.FIREWORK_ROCKET, 1), 45.0, 12, 32, false));
        surfBarrel.add(stackEntry("sb_gapple", new ItemStack(Material.GOLDEN_APPLE, 1), 45.0, 2, 6, false));

        // 3. SURFACE_UNIQUE_SHULKER — 3 Уникальных шалкера за Уникальный ключ (топ-лут /shop + киты elder/eternity!)
        List<DungeonLootEntry> surfShulker = pools.get(LootPoolType.SURFACE_UNIQUE_SHULKER);
        surfShulker.add(stackEntry("su_egapple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 70.0, 1, 3, false));
        surfShulker.add(stackEntry("su_ingot", new ItemStack(Material.NETHERITE_INGOT, 1), 75.0, 1, 3, false));
        surfShulker.add(stackEntry("su_eternity_sword", VaultAndItemsHook.enchantedGear(Material.NETHERITE_SWORD,
                "&#F8BEFB« Меч шалкера »",
                new MapEnchant(Enchantment.SHARPNESS, 6), new MapEnchant(Enchantment.FIRE_ASPECT, 2),
                new MapEnchant(Enchantment.MENDING, 1), new MapEnchant(Enchantment.UNBREAKING, 4)),
                32.0, 1, 1, false));
        surfShulker.add(stackEntry("su_eternity_pick", VaultAndItemsHook.enchantedGear(Material.NETHERITE_PICKAXE,
                "&#F8BEFB« Кирка шалкера »",
                new MapEnchant(Enchantment.EFFICIENCY, 6), new MapEnchant(Enchantment.FORTUNE, 3),
                new MapEnchant(Enchantment.MENDING, 1), new MapEnchant(Enchantment.UNBREAKING, 4)),
                30.0, 1, 1, false));
        surfShulker.add(shopEntry("su_snake_orb", "shar1", VaultAndItemsHook.texturedHead(SNAKE_ORB_B64,
                "&#F8BEFB« Шар Змеи »", List.of(
                        "&#F8BEFB&l┃ &fЭффект: &#F8BEFBСила II",
                        "&#F8BEFB&l┃ &fЭффект: &#F8BEFBСкорость I",
                        "&#F8BEFB&l┃ &fЭффект: &#F8BEFBСопротивление I")),
                35.0, 1, 1, false));
        surfShulker.add(shopEntry("su_doc_totem", "totem1", VaultAndItemsHook.namedItem(Material.TOTEM_OF_UNDYING, 1,
                "&#F8BEFB« Тотем доктора »", List.of("&#F8BEFB&l┃ &fБонус: &#F8BEFB+4 к макс. здоровью"), true),
                42.0, 1, 1, false));
        surfShulker.add(shopEntry("su_raskol", "kirkaraskola", VaultAndItemsHook.namedItem(Material.NETHERITE_PICKAXE, 1,
                "&#F8BEFBКирка Раскола", List.of("&7Копает 3×3"), true),
                26.0, 1, 1, false));
        surfShulker.add(stackEntry("su_spawner", new ItemStack(Material.SPAWNER, 1), 24.0, 1, 1, false));
        surfShulker.add(keyEntry("su_void_key", "void_key", hook.createVaultKey("void_key", 1), 45.0, 1, 1, false));

        // 4. UNDERGROUND_SHULKER — Главный шалкер Подземного данжа после победы над сильными мобами
        List<DungeonLootEntry> ugShulker = pools.get(LootPoolType.UNDERGROUND_SHULKER);
        ugShulker.add(keyEntry("ug_key_unique", "unique", hook.createVaultKey("unique", 1), 65.0, 1, 1, false));
        ugShulker.add(keyEntry("ug_key_void", "void_key", hook.createVaultKey("void_key", 1), 75.0, 1, 2, false));
        ugShulker.add(keyEntry("ug_key_royal", "royal", hook.createVaultKey("royal", 1), 100.0, 1, 2, true));
        ugShulker.add(shopEntry("ug_pot_war", "potion_voina",
                VaultAndItemsHook.customPotion(Material.POTION, 1, "&#FF4D4DЗелье воина", Color.fromRGB(220, 45, 45),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.STRENGTH, 120, 1), VaultAndItemsHook.eff(PotionEffectType.SPEED, 120, 1))),
                65.0, 1, 3, false));
        ugShulker.add(shopEntry("ug_pot_salv", "potion_salvation",
                VaultAndItemsHook.customPotion(Material.SPLASH_POTION, 1, "&#FFD55EЗелье спасения", Color.fromRGB(255, 210, 80),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.RESISTANCE, 25, 2), VaultAndItemsHook.eff(PotionEffectType.REGENERATION, 25, 2))),
                55.0, 1, 2, false));
        ugShulker.add(shopEntry("ug_anti_pearl", "anti_pearl",
                VaultAndItemsHook.namedItem(Material.ENDER_EYE, 1, "&#B97BFFАнти-пёрл", List.of("&7Блокирует эндер-пёрл врага"), true),
                50.0, 1, 3, false));
        ugShulker.add(shopEntry("ug_trap", "explosive_trap",
                VaultAndItemsHook.namedItem(Material.HEAVY_CORE, 1, "&#FF5555Взрывная трапка", List.of("&7Расходник из /shop"), true),
                45.0, 1, 2, false));
        ugShulker.add(stackEntry("ug_dragon_sword", VaultAndItemsHook.enchantedGear(Material.DIAMOND_SWORD, null,
                new MapEnchant(Enchantment.SHARPNESS, 5), new MapEnchant(Enchantment.FIRE_ASPECT, 2), new MapEnchant(Enchantment.LOOTING, 3)),
                45.0, 1, 1, false));
        ugShulker.add(stackEntry("ug_egapple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 50.0, 1, 2, false));
        ugShulker.add(stackEntry("ug_totems", new ItemStack(Material.TOTEM_OF_UNDYING, 1), 80.0, 1, 3, false));
        ugShulker.add(stackEntry("ug_ingot", new ItemStack(Material.NETHERITE_INGOT, 1), 55.0, 1, 2, false));

        // 5. UNDERGROUND_CONTAINER — Бочки/сундуки в зале Подземного данжа
        List<DungeonLootEntry> ugCont = pools.get(LootPoolType.UNDERGROUND_CONTAINER);
        ugCont.add(stackEntry("ugc_gapple", new ItemStack(Material.GOLDEN_APPLE, 1), 70.0, 4, 12, false));
        ugCont.add(stackEntry("ugc_pearl", new ItemStack(Material.ENDER_PEARL, 1), 60.0, 4, 16, false));
        ugCont.add(stackEntry("ugc_scrap", new ItemStack(Material.NETHERITE_SCRAP, 1), 45.0, 1, 3, false));
        ugCont.add(stackEntry("ugc_book_p4", VaultAndItemsHook.enchantedBook("&#7EC8FFКнига: Защита IV", Enchantment.PROTECTION, 4), 40.0, 1, 1, false));
        ugCont.add(keyEntry("ugc_key_know", "knowledge", hook.createVaultKey("knowledge", 1), 35.0, 1, 1, false));

        // 6. SKY_ANGELIC_SHARD — Предметы из Ангельских осколков Воздушного данжа (ветка Лут при 20% проке)
        List<DungeonLootEntry> skyShard = pools.get(LootPoolType.SKY_ANGELIC_SHARD);
        skyShard.add(stackEntry("sky_gapple", new ItemStack(Material.GOLDEN_APPLE, 1), 65.0, 2, 6, false));
        skyShard.add(stackEntry("sky_diamond", new ItemStack(Material.DIAMOND, 1), 60.0, 3, 10, false));
        skyShard.add(stackEntry("sky_totem", new ItemStack(Material.TOTEM_OF_UNDYING, 1), 45.0, 1, 1, false));
        skyShard.add(stackEntry("sky_scrap", new ItemStack(Material.NETHERITE_SCRAP, 1), 35.0, 1, 2, false));
        skyShard.add(stackEntry("sky_egapple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 18.0, 1, 1, false));
        skyShard.add(shopEntry("sky_arrow_levit", "arrow_levit",
                VaultAndItemsHook.customPotion(Material.TIPPED_ARROW, 8, "&#F8BEFBСтрела левитации", Color.fromRGB(248, 190, 251),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.LEVITATION, 6, 1))),
                40.0, 6, 12, false));
        skyShard.add(shopEntry("sky_pot_sport", "potion_sportsmena",
                VaultAndItemsHook.customPotion(Material.POTION, 1, "&#7EC8FFЗелье спортсмена", Color.fromRGB(90, 210, 255),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.SPEED, 150, 2), VaultAndItemsHook.eff(PotionEffectType.JUMP_BOOST, 150, 1))),
                35.0, 1, 1, false));
        skyShard.add(keyEntry("sky_key_royal", "royal", hook.createVaultKey("royal", 1), 22.0, 1, 1, false));
        skyShard.add(keyEntry("sky_key_void", "void_key", hook.createVaultKey("void_key", 1), 14.0, 1, 1, false));
        skyShard.add(keyEntry("sky_key_unique", "unique", hook.createVaultKey("unique", 1), 9.0, 1, 1, false));
    }

    private static DungeonLootEntry keyEntry(String id, String keyId, ItemStack preview, double chance, int min, int max, boolean guaranteed) {
        return new DungeonLootEntry(id, DungeonLootEntry.SourceKind.VAULT_KEY, keyId, preview, chance, min, max, guaranteed);
    }

    private static DungeonLootEntry shopEntry(String id, String itemId, ItemStack fallback, double chance, int min, int max, boolean guaranteed) {
        return new DungeonLootEntry(id, DungeonLootEntry.SourceKind.ELYTRIX_ITEM, itemId, fallback, chance, min, max, guaranteed);
    }

    private static DungeonLootEntry stackEntry(String id, ItemStack stack, double chance, int min, int max, boolean guaranteed) {
        return new DungeonLootEntry(id, DungeonLootEntry.SourceKind.ITEM_STACK, "", stack, chance, min, max, guaranteed);
    }
}
