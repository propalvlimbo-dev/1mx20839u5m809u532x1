package ru.rooyzee.elytrixcemetery.loot;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.util.MaterialUtil;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootStorageManager {

    private final Main plugin;
    private final File lootFolder;
    private final NamespacedKey ghostShardIdPdc;
    private final NamespacedKey legacyDungeonShardIdPdc;

    private final Map<LootType, Map<Integer, List<ItemStack>>> lootTables = new ConcurrentHashMap<>();

    public LootStorageManager(Main plugin) {
        this.plugin = plugin;
        this.lootFolder = new File(plugin.getDataFolder(), "loot");
        this.ghostShardIdPdc = new NamespacedKey(plugin, "item_id");
        this.legacyDungeonShardIdPdc = new NamespacedKey("elytrixdungeons", "item_id");
        if (!lootFolder.exists() && !lootFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create loot directory: " + lootFolder.getAbsolutePath());
        }
        loadAll();
    }

    public void loadAll() {
        lootTables.clear();
        for (LootType type : LootType.values()) {
            loadType(type);
        }
    }

    public void loadType(LootType type) {
        File file = new File(lootFolder, type.getFileName());
        Map<Integer, List<ItemStack>> pages = new HashMap<>();
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }

        if (!file.exists()) {
            generateDefaultLoot(type, pages);
            saveType(type, pages);
        } else {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                // Keep the original file intact; malformed user data must never be replaced by defaults.
                plugin.getLogger().severe("Failed to load loot " + file.getName() + ": " + e.getMessage());
                lootTables.put(type, pages);
                return;
            }

            for (int p = 1; p <= 4; p++) {
                List<?> list = config.getList("page_" + p);
                if (list == null) continue;

                List<ItemStack> items = new ArrayList<>();
                for (Object obj : list) {
                    if (obj instanceof ItemStack) {
                        // Only migrate items carrying ElytriX's known shard PDC. Display names alone
                        // are not identity: custom items from other plugins must remain untouched.
                        items.add(normalizeGhostShard((ItemStack) obj));
                    } else if (obj != null) {
                        plugin.getLogger().warning("Ignoring non-ItemStack entry in " + file.getName()
                                + " page_" + p + " (" + obj.getClass().getName() + ").");
                    }
                }
                pages.put(p, items);
            }
        }

        // An existing empty table is an intentional admin choice, not a reason to restore defaults.
        lootTables.put(type, pages);
    }

    public boolean saveType(LootType type, Map<Integer, List<ItemStack>> pages) {
        File file = new File(lootFolder, type.getFileName());
        YamlConfiguration config = new YamlConfiguration();

        // Merge into the current YAML so custom keys and future page data are not discarded.
        if (file.exists()) {
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                plugin.getLogger().severe("Refusing to overwrite unreadable loot " + file.getName()
                        + ": " + e.getMessage());
                return false;
            }
        }

        for (int p = 1; p <= 4; p++) {
            List<ItemStack> list = pages.get(p);
            config.set("page_" + p, list != null ? list : Collections.emptyList());
        }

        File tempFile = new File(lootFolder, file.getName() + ".tmp");
        try {
            config.save(tempFile);
            try {
                Files.move(tempFile.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            lootTables.put(type, pages);
            return true;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Failed to save loot " + file.getName() + ": " + e.getMessage());
            return false;
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                plugin.getLogger().warning("Could not remove temporary loot file: " + tempFile.getName());
            }
        }
    }

    public List<ItemStack> getItems(LootType type, int page) {
        Map<Integer, List<ItemStack>> pages = lootTables.get(type);
        if (pages == null) return Collections.emptyList();
        List<ItemStack> list = pages.get(page);
        return list != null ? list : Collections.emptyList();
    }

    public boolean setItems(LootType type, int page, List<ItemStack> items) {
        if (page < 1 || page > 4) return false;
        Map<Integer, List<ItemStack>> pages = lootTables.computeIfAbsent(type, k -> new HashMap<>());
        pages.put(page, new ArrayList<>(items));
        return saveType(type, pages);
    }

    /** Single canonical Ghost Shard factory used by Cemetery and Dungeons rewards. */
    public ItemStack createGhostShard(int amount) {
        ItemStack shard = new ItemStack(MaterialUtil.getGhostShardMaterial(), Math.max(1, amount));
        ItemMeta meta = shard.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("&7« &#F8BEFBПризрачный осколок &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.colorize("&#F8BEFB&l┃"));
            lore.add(ColorUtil.colorize("&#F8BEFB&l┃ &fДобывается с Призраков и Стражей Кладбища, сильных мобов и босса подземелья"));
            lore.add(ColorUtil.colorize("&#F8BEFB&l┃ &fОбменивается на ценные награды у Торговца"));
            lore.add(ColorUtil.colorize("&#F8BEFB&l┃"));
            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(ghostShardIdPdc, PersistentDataType.STRING, "ghost_shard");
            shard.setItemMeta(meta);
        }
        return shard;
    }

    /** Replaces only known legacy shards; a matching display name is never enough to rewrite an item. */
    public ItemStack normalizeGhostShard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String cemeteryId = meta.getPersistentDataContainer()
                .get(ghostShardIdPdc, PersistentDataType.STRING);
        if ("ghost_shard".equals(cemeteryId)) return item;

        String legacyDungeonId = meta.getPersistentDataContainer()
                .get(legacyDungeonShardIdPdc, PersistentDataType.STRING);
        return "ghost_shard".equals(legacyDungeonId) ? createGhostShard(item.getAmount()) : item;
    }

    /**
     * Выбирает лут для сундука могилы в зависимости от онлайна сервера.
     */
    public List<ItemStack> rollGraveDrops(int playerCount) {
        List<ItemStack> result = new ArrayList<>();

        double[] chances;
        int targetStacks;

        if (playerCount <= 3) {
            chances = new double[]{85.0, 45.0, 15.0, 5.0};
            targetStacks = 4;
        } else if (playerCount <= 6) {
            chances = new double[]{95.0, 60.0, 30.0, 12.0};
            targetStacks = 6;
        } else {
            chances = new double[]{100.0, 80.0, 50.0, 25.0};
            targetStacks = 8;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int page = 1; page <= 4; page++) {
            List<ItemStack> pool = getItems(LootType.GRAVES, page);
            if (pool.isEmpty()) continue;

            double roll = rng.nextDouble(0.0, 100.0);
            if (roll <= chances[page - 1]) {
                int picks = Math.max(1, targetStacks / 2);
                for (int i = 0; i < picks; i++) {
                    if (result.size() >= targetStacks) break;
                    ItemStack sample = pool.get(rng.nextInt(pool.size()));
                    if (sample != null && sample.getType() != Material.AIR) {
                        result.add(sample.clone());
                    }
                }
            }
        }

        // If all probability rolls miss, fall back only to an item that is actually configured.
        // An intentionally empty/custom table must never gain built-in replacement loot.
        if (result.isEmpty()) {
            for (int page = 1; page <= 4; page++) {
                List<ItemStack> pool = getItems(LootType.GRAVES, page);
                List<ItemStack> usable = new ArrayList<>();
                for (ItemStack item : pool) {
                    if (item != null && !item.getType().isAir()) usable.add(item);
                }
                if (!usable.isEmpty()) {
                    result.add(usable.get(rng.nextInt(usable.size())).clone());
                    break;
                }
            }
        }

        return result;
    }

    /** Rolls the same shard and bonus-loot pool for cemetery ghosts. */
    public List<ItemStack> rollGhostDrops() {
        List<ItemStack> result = new ArrayList<>();
        result.add(createGhostShard(1));
        addGhostTableBonus(result);
        return result;
    }

    /** Guardians retain their grave loot, but the shard reward is capped at one. */
    public List<ItemStack> rollGuardianDrops() {
        List<ItemStack> result = new ArrayList<>();
        result.add(createGhostShard(1));
        addGhostTableBonus(result);

        for (int page = 3; page <= 4; page++) {
            List<ItemStack> items = getItems(LootType.GRAVES, page);
            if (!items.isEmpty()) {
                ItemStack template = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                if (template != null && template.getType() != Material.AIR && !isGhostShard(template)) {
                    result.add(template.clone());
                }
            }
        }
        if (result.size() < 2) {
            result.add(new ItemStack(Material.NETHERITE_INGOT, 1));
            result.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }
        return result;
    }

    private void addGhostTableBonus(List<ItemStack> result) {
        double chance = plugin.getConfig().getDouble("ghosts.bonus-loot-chance", 35.0);
        chance = Double.isFinite(chance) ? Math.max(0.0, Math.min(100.0, chance)) : 0.0;
        if (ThreadLocalRandom.current().nextDouble(100.0) >= chance) return;

        List<ItemStack> candidates = new ArrayList<>();
        for (int page = 1; page <= 4; page++) candidates.addAll(getItems(LootType.GHOSTS, page));
        if (!candidates.isEmpty()) {
            ItemStack item = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            if (item != null && item.getType() != Material.AIR && !isGhostShard(item)) result.add(item.clone());
        }
    }

    private boolean isGhostShard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String cemeteryId = meta.getPersistentDataContainer()
                .get(ghostShardIdPdc, PersistentDataType.STRING);
        String legacyDungeonId = meta.getPersistentDataContainer()
                .get(legacyDungeonShardIdPdc, PersistentDataType.STRING);
        return "ghost_shard".equals(cemeteryId) || "ghost_shard".equals(legacyDungeonId);
    }

    /** Returns a weighted ElytrixVault key, or null when the configured chance misses. */
    public ItemStack rollVaultKeyDrop(double chance) {
        double safeChance = Double.isFinite(chance) ? Math.max(0.0, Math.min(100.0, chance)) : 0.0;
        if (ThreadLocalRandom.current().nextDouble(100.0) >= safeChance) return null;
        return createVaultKeyItem(rollWeightedVaultKeyId());
    }

    private String rollWeightedVaultKeyId() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("guardian-key-weights");
        if (section == null) section = plugin.getConfig().getConfigurationSection("ghosts.key-weights");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int weight = section.getInt(key, 0);
                if (weight > 0) weights.put(key.toLowerCase(Locale.ROOT), weight);
            }
        }
        if (weights.isEmpty()) {
            weights.put("rusty", 52);
            weights.put("knowledge", 25);
            weights.put("royal", 13);
            weights.put("void", 7);
            weights.put("unique", 3);
        }

        int totalWeight = 0;
        for (Integer weight : weights.values()) totalWeight += weight;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, totalWeight));
        int cursor = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            cursor += entry.getValue();
            if (roll < cursor) return entry.getKey();
        }
        return "rusty";
    }

    private ItemStack createVaultKeyItem(String keyId) {
        Plugin vault = Bukkit.getPluginManager().getPlugin("ElytrixVault");
        if (vault != null && vault.isEnabled()) {
            try {
                Object registry = vault.getClass().getMethod("getKeyRegistry").invoke(vault);
                Object keyType = registry.getClass().getMethod("getKey", String.class).invoke(registry, keyId);
                if (keyType != null) {
                    Object keyPdc = registry.getClass().getMethod("getKeyPdc").invoke(registry);
                    if (keyPdc instanceof NamespacedKey) {
                        Method factory = keyType.getClass().getMethod(
                                "createKeyItem", NamespacedKey.class, int.class);
                        Object key = factory.invoke(keyType, keyPdc, 1);
                        if (key instanceof ItemStack) return (ItemStack) key;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Keep rewards usable when Vault is temporarily unavailable; the Vault PDC makes the key recognizable.
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, 1);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize("&7« " + vaultKeyDisplayName(keyId) + " &7»"));
            meta.setLore(Arrays.asList(ColorUtil.colorize("&7Используйте на Хранилище ElytrixVault")));
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("elytrixvault", "key_id"), PersistentDataType.STRING, keyId);
            key.setItemMeta(meta);
        }
        return key;
    }

    private static String vaultKeyDisplayName(String keyId) {
        switch (keyId.toLowerCase(Locale.ROOT)) {
            case "rusty": return "&#C88A65Ржавый ключ";
            case "knowledge": return "&#7FD4FFКлюч Знаний";
            case "royal": return "&#FFD36EКоролевский ключ";
            case "void": return "&#B87DFFПустотный ключ";
            default: return "&#F8BEFBУникальный ключ";
        }
    }

    private void generateDefaultLoot(LootType type, Map<Integer, List<ItemStack>> pages) {
        for (int p = 1; p <= 4; p++) {
            pages.put(p, new ArrayList<ItemStack>());
        }

        if (type == LootType.GRAVES) {
            // Страница 1 (Обычный лут)
            List<ItemStack> p1 = pages.get(1);
            p1.add(new ItemStack(Material.GOLDEN_APPLE, 3));
            p1.add(new ItemStack(Material.DIAMOND, 6));
            p1.add(new ItemStack(Material.EMERALD, 12));
            p1.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
            p1.add(new ItemStack(Material.ENDER_PEARL, 6));
            p1.add(new ItemStack(Material.GOLDEN_CARROT, 12));
            p1.add(new ItemStack(Material.IRON_INGOT, 16));
            p1.add(new ItemStack(Material.GOLD_INGOT, 12));
            p1.add(new ItemStack(Material.OBSIDIAN, 8));

            // Страница 2 (Необычный лут)
            List<ItemStack> p2 = pages.get(2);
            p2.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            p2.add(matchItem("SHULKER_SHELL", 3));
            p2.add(matchItem("NETHERITE_SCRAP", 2));
            p2.add(createEnchantedBook(Enchantment.MENDING, 1));
            p2.add(createEnchantedBook(Enchantment.PROTECTION_ENVIRONMENTAL, 4));
            p2.add(createEnchantedBook(Enchantment.DAMAGE_ALL, 5));
            p2.add(createEnchantedBook(Enchantment.DURABILITY, 3));

            // Страница 3 (Редкий лут)
            List<ItemStack> p3 = pages.get(3);
            p3.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            p3.add(matchItem("NETHERITE_INGOT", 2));
            p3.add(new ItemStack(Material.DIAMOND_BLOCK, 2));
            p3.add(new ItemStack(Material.EMERALD_BLOCK, 4));
            p3.add(createGear(Material.ELYTRA, "&#FFBD59Элитры Скитальца", Enchantment.DURABILITY, 3, Enchantment.MENDING, 1));
            p3.add(createGear(Material.TRIDENT, "&#C58BFFТрезубец Хранителя", Enchantment.IMPALING, 5, Enchantment.DURABILITY, 3));

            // Страница 4 (Легендарный лут)
            List<ItemStack> p4 = pages.get(4);
            p4.add(matchItem("NETHERITE_BLOCK", 1));
            p4.add(createGear(matchMaterial("NETHERITE_CHESTPLATE", "DIAMOND_CHESTPLATE"), "&#FF5E8AДоспех Правды", Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3, Enchantment.MENDING, 1));
            p4.add(createGear(matchMaterial("NETHERITE_HELMET", "DIAMOND_HELMET"), "&#F8BEFBШлем Стража", Enchantment.PROTECTION_ENVIRONMENTAL, 4, Enchantment.DURABILITY, 3, Enchantment.MENDING, 1));
            p4.add(matchItem("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1));
            p4.add(createGhostShard(4));
        } else if (type == LootType.GHOSTS) {
            List<ItemStack> p1 = pages.get(1);
            p1.add(new ItemStack(Material.ENDER_PEARL, 4));
            p1.add(new ItemStack(Material.GOLDEN_APPLE, 2));
            p1.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 12));

            List<ItemStack> p2 = pages.get(2);
            p2.add(new ItemStack(Material.DIAMOND, 4));
            p2.add(matchItem("SHULKER_SHELL", 2));
            p2.add(createEnchantedBook(Enchantment.MENDING, 1));

            List<ItemStack> p3 = pages.get(3);
            p3.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            p3.add(matchItem("NETHERITE_SCRAP", 2));

            List<ItemStack> p4 = pages.get(4);
            p4.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            p4.add(matchItem("NETHERITE_INGOT", 1));
        }
    }

    private ItemStack matchItem(String name, int amount) {
        Material m = Material.matchMaterial(name);
        if (m == null) m = Material.DIAMOND;
        return new ItemStack(m, Math.max(1, amount));
    }

    private Material matchMaterial(String primary, String fallback) {
        Material m = Material.matchMaterial(primary);
        if (m != null) return m;
        m = Material.matchMaterial(fallback);
        return m != null ? m : Material.DIAMOND;
    }

    private ItemStack createEnchantedBook(Enchantment ench, int level) {
        ItemStack is = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = is.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            ((EnchantmentStorageMeta) meta).addStoredEnchant(ench, level, true);
            is.setItemMeta(meta);
        }
        return is;
    }

    private ItemStack createGear(Material mat, String name, Object... enchants) {
        ItemStack is = new ItemStack(mat != null ? mat : Material.DIAMOND_SWORD);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            for (int i = 0; i < enchants.length; i += 2) {
                if (i + 1 < enchants.length && enchants[i] instanceof Enchantment && enchants[i + 1] instanceof Integer) {
                    meta.addEnchant((Enchantment) enchants[i], (Integer) enchants[i + 1], true);
                }
            }
            is.setItemMeta(meta);
        }
        return is;
    }
}
