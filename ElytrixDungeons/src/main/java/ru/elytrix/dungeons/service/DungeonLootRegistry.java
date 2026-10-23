package ru.elytrix.dungeons.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import ru.elytrix.dungeons.model.DungeonLootEntry;

public final class DungeonLootRegistry {

    private final ElytrixDungeonsPlugin plugin;
    private final File lootFile;
    private final Map<Integer, List<DungeonLootEntry>> explorerPools = new LinkedHashMap<>();
    private final Map<Integer, List<DungeonLootEntry>> rewardPools = new LinkedHashMap<>();

    public DungeonLootRegistry(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "loot.yml");
    }

    public void load() {
        explorerPools.clear();
        rewardPools.clear();
        for (int tier = 1; tier <= 5; tier++) {
            explorerPools.put(tier, new ArrayList<>());
            rewardPools.put(tier, new ArrayList<>());
        }
        if (!lootFile.exists()) {
            populateDefaults();
            save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(lootFile);
        for (int tier = 1; tier <= 5; tier++) {
            readPool(yml.getConfigurationSection("tiers." + tier + ".explorer"), explorerPools.get(tier));
            readPool(yml.getConfigurationSection("tiers." + tier + ".reward"), rewardPools.get(tier));
        }
    }

    private void readPool(ConfigurationSection sec, List<DungeonLootEntry> target) {
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection e = sec.getConfigurationSection(id);
            if (e == null) continue;
            DungeonLootEntry.SourceKind kind;
            try {
                kind = DungeonLootEntry.SourceKind.valueOf(e.getString("kind", "ITEM_STACK"));
            } catch (IllegalArgumentException ex) {
                kind = DungeonLootEntry.SourceKind.ITEM_STACK;
            }
            String ext = e.getString("external-id", "");
            ItemStack item = e.getItemStack("item");
            if (item == null || item.getType().isAir()) {
                item = new ItemStack(Material.DIAMOND, 1);
            }
            target.add(new DungeonLootEntry(
                    id,
                    kind,
                    ext,
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
        for (int tier = 1; tier <= 5; tier++) {
            writePool(yml, "tiers." + tier + ".explorer", explorerPools.get(tier));
            writePool(yml, "tiers." + tier + ".reward", rewardPools.get(tier));
        }
        try {
            yml.save(lootFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save loot.yml: " + ex.getMessage());
        }
    }

    private void writePool(YamlConfiguration yml, String basePath, List<DungeonLootEntry> list) {
        if (list == null) return;
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

    public List<DungeonLootEntry> getPool(int tier, boolean reward) {
        int clamped = Math.max(1, Math.min(5, tier));
        return (reward ? rewardPools : explorerPools).get(clamped);
    }

    public void fillContainerRandomSlots(Inventory inv, int tier, boolean reward, int maxStacks) {
        inv.clear();
        List<DungeonLootEntry> pool = getPool(tier, reward);
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
            if (generated.size() >= maxStacks) {
                break;
            }
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

        // Explorer pools (Tier 1..5 barrels inside halls)
        for (int tier = 1; tier <= 5; tier++) {
            List<DungeonLootEntry> exp = explorerPools.get(tier);
            exp.add(stackEntry("gapple_" + tier, new ItemStack(Material.GOLDEN_APPLE, 1), 65.0, 1 + tier / 2, 2 + tier, false));
            exp.add(stackEntry("pearls_" + tier, new ItemStack(Material.ENDER_PEARL, 1), 55.0, 2, 4 + tier, false));
            exp.add(stackEntry("diamonds_" + tier, new ItemStack(Material.DIAMOND, 1), 60.0, 2 * tier, 5 * tier, false));
            exp.add(stackEntry("exp_" + tier, new ItemStack(Material.EXPERIENCE_BOTTLE, 1), 70.0, 8, 12 + tier * 6, false));
            exp.add(stackEntry("obsidian_" + tier, new ItemStack(Material.OBSIDIAN, 1), 45.0, 8, 16 + tier * 4, false));
            exp.add(stackEntry("fireworks_" + tier, new ItemStack(Material.FIREWORK_ROCKET, 1), 50.0, 8, 16 + tier * 4, false));
            if (tier >= 2) {
                exp.add(keyEntry("exp_rusty_" + tier, "rusty", hook.createVaultKey("rusty", 1), 22.0 + tier * 4.0, 1, 1, false));
                exp.add(stackEntry("scrap_" + tier, new ItemStack(Material.NETHERITE_SCRAP, 1), 28.0, 1, 1 + tier / 2, false));
            }
            if (tier >= 4) {
                exp.add(keyEntry("exp_knowledge_" + tier, "knowledge", hook.createVaultKey("knowledge", 1), 20.0, 1, 1, false));
                exp.add(stackEntry("totem_exp_" + tier, new ItemStack(Material.TOTEM_OF_UNDYING, 1), 32.0, 1, 1, false));
            }
        }

        // Tier 1 Reward (Surface Temple)
        List<DungeonLootEntry> r1 = rewardPools.get(1);
        r1.add(keyEntry("r1_key_rusty", "rusty", hook.createVaultKey("rusty", 1), 100.0, 1, 2, true));
        r1.add(keyEntry("r1_key_know", "knowledge", hook.createVaultKey("knowledge", 1), 28.0, 1, 1, false));
        r1.add(shopEntry("r1_pot_heal", "potion_healt",
                VaultAndItemsHook.customPotion(Material.SPLASH_POTION, 1, "&#FF6B8BЗелье исцеления", Color.fromRGB(255, 80, 120),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.INSTANT_HEALTH, 1, 1), VaultAndItemsHook.eff(PotionEffectType.REGENERATION, 20, 1))),
                55.0, 1, 2, false));
        r1.add(stackEntry("r1_gapple", new ItemStack(Material.GOLDEN_APPLE, 1), 80.0, 3, 6, false));
        r1.add(stackEntry("r1_diamonds", new ItemStack(Material.DIAMOND, 1), 75.0, 6, 14, false));

        // Tier 2 Reward (Surface Bastion)
        List<DungeonLootEntry> r2 = rewardPools.get(2);
        r2.add(keyEntry("r2_key_rusty", "rusty", hook.createVaultKey("rusty", 1), 100.0, 1, 2, true));
        r2.add(keyEntry("r2_key_know", "knowledge", hook.createVaultKey("knowledge", 1), 45.0, 1, 1, false));
        r2.add(shopEntry("r2_pot_war", "potion_voina",
                VaultAndItemsHook.customPotion(Material.POTION, 1, "&#FF4D4DЗелье воина", Color.fromRGB(220, 45, 45),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.STRENGTH, 120, 1), VaultAndItemsHook.eff(PotionEffectType.SPEED, 120, 1))),
                50.0, 1, 2, false));
        r2.add(shopEntry("r2_arrow_freeze", "arrow1",
                VaultAndItemsHook.customPotion(Material.TIPPED_ARROW, 8, "&#7EC8FFЛедяная стрела", Color.fromRGB(120, 205, 255),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.SLOWNESS, 8, 2))),
                45.0, 6, 12, false));
        r2.add(stackEntry("r2_totem", new ItemStack(Material.TOTEM_OF_UNDYING, 1), 45.0, 1, 1, false));
        r2.add(stackEntry("r2_scrap", new ItemStack(Material.NETHERITE_SCRAP, 1), 50.0, 1, 3, false));

        // Tier 3 Reward (Purpur Keep / Underground Rift Crypt)
        List<DungeonLootEntry> r3 = rewardPools.get(3);
        r3.add(keyEntry("r3_key_know", "knowledge", hook.createVaultKey("knowledge", 1), 100.0, 1, 2, true));
        r3.add(keyEntry("r3_key_royal", "royal", hook.createVaultKey("royal", 1), 42.0, 1, 1, false));
        r3.add(shopEntry("r3_anti_pearl", "anti_pearl",
                VaultAndItemsHook.namedItem(Material.ENDER_EYE, 1, "&#B97BFFАнти-пёрл", List.of("&7Блокирует телепортацию врага"), true),
                40.0, 1, 2, false));
        r3.add(shopEntry("r3_trap", "explosive_trap",
                VaultAndItemsHook.namedItem(Material.HEAVY_CORE, 1, "&#FF5555Взрывная трапка", List.of("&7Боевой расходник ElytriX"), true),
                35.0, 1, 2, false));
        r3.add(stackEntry("r3_book_prot", VaultAndItemsHook.enchantedBook("&#7EC8FFКнига: Защита V", Enchantment.PROTECTION, 5), 40.0, 1, 1, false));
        r3.add(stackEntry("r3_totem", new ItemStack(Material.TOTEM_OF_UNDYING, 1), 65.0, 1, 2, false));

        // Tier 4 Reward (Underground Sculk Sanctum / Sky Astral Island)
        List<DungeonLootEntry> r4 = rewardPools.get(4);
        r4.add(keyEntry("r4_key_royal", "royal", hook.createVaultKey("royal", 1), 100.0, 1, 2, true));
        r4.add(keyEntry("r4_key_void", "void_key", hook.createVaultKey("void_key", 1), 48.0, 1, 1, false));
        r4.add(keyEntry("r4_key_unique", "unique", hook.createVaultKey("unique", 1), 18.0, 1, 1, false));
        r4.add(shopEntry("r4_shar1", "shar1",
                VaultAndItemsHook.namedItem(Material.PLAYER_HEAD, 1, "&#F8BEFBСфера Скорости", List.of("&7Боевая сфера в левую руку"), true),
                28.0, 1, 1, false));
        r4.add(shopEntry("r4_pot_salv", "potion_salvation",
                VaultAndItemsHook.customPotion(Material.SPLASH_POTION, 1, "&#FFD55EЗелье спасения", Color.fromRGB(255, 210, 80),
                        List.of(VaultAndItemsHook.eff(PotionEffectType.RESISTANCE, 25, 2), VaultAndItemsHook.eff(PotionEffectType.REGENERATION, 25, 2))),
                50.0, 1, 2, false));
        r4.add(stackEntry("r4_egapple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 35.0, 1, 2, false));
        r4.add(stackEntry("r4_ingot", new ItemStack(Material.NETHERITE_INGOT, 1), 45.0, 1, 2, false));

        // Tier 5 Reward (Sky Elytrix Citadel — Crown Dungeon)
        List<DungeonLootEntry> r5 = rewardPools.get(5);
        r5.add(keyEntry("r5_key_void", "void_key", hook.createVaultKey("void_key", 1), 100.0, 1, 2, true));
        r5.add(keyEntry("r5_key_unique", "unique", hook.createVaultKey("unique", 1), 55.0, 1, 1, false));
        r5.add(shopEntry("r5_totem1", "totem1",
                VaultAndItemsHook.namedItem(Material.TOTEM_OF_UNDYING, 1, "&#F8BEFBТалисман Карателя", List.of("&7Редкий боевой талисман"), true),
                32.0, 1, 1, false));
        r5.add(shopEntry("r5_shar2", "shar2",
                VaultAndItemsHook.namedItem(Material.PLAYER_HEAD, 1, "&#FF7AFEСфера Мощи", List.of("&7Усиливает урон в бою"), true),
                30.0, 1, 1, false));
        r5.add(shopEntry("r5_pick", "kirkaraskola",
                VaultAndItemsHook.namedItem(Material.NETHERITE_PICKAXE, 1, "&#F8BEFBКирка Раскола", List.of("&7Ломает породу 3×3"), true),
                24.0, 1, 1, false));
        r5.add(stackEntry("r5_egapple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 60.0, 1, 3, false));
        r5.add(stackEntry("r5_ingot", new ItemStack(Material.NETHERITE_INGOT, 1), 65.0, 1, 3, false));
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
