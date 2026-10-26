package ru.elytrix.vault.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.model.LootEntry;
import ru.elytrix.vault.model.Rarity;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.preset.DefaultKeysFactory;
import ru.elytrix.vault.util.TextUtil;

public class KeyRegistry {

    private final ElytrixVaultPlugin plugin;
    private final NamespacedKey keyPdc;
    private final NamespacedKey protectedDropPdc;
    private final Map<String, VaultKeyType> keys = new LinkedHashMap<>();
    private final Map<Rarity, Integer> rarityWeights = new EnumMap<>(Rarity.class);
    private final Map<Rarity, String> rarityNames = new EnumMap<>(Rarity.class);
    private final Map<Rarity, String> rarityColors = new EnumMap<>(Rarity.class);

    private File keysFile;
    private File pendingFile;
    private FileConfiguration pendingConfig;

    public KeyRegistry(ElytrixVaultPlugin plugin) {
        this.plugin = plugin;
        this.keyPdc = new NamespacedKey(plugin, "key_id");
        this.protectedDropPdc = new NamespacedKey(plugin, "protected_owner");
    }

    public NamespacedKey getKeyPdc() {
        return keyPdc;
    }

    public NamespacedKey getProtectedDropPdc() {
        return protectedDropPdc;
    }

    public void load() {
        loadRaritiesFromConfig();
        keysFile = new File(plugin.getDataFolder(), "keys.yml");
        if (!keysFile.exists()) {
            keys.clear();
            keys.putAll(DefaultKeysFactory.createDefaults());
            saveKeys();
        } else {
            loadKeysFile();
        }

        pendingFile = new File(plugin.getDataFolder(), "pending.yml");
        if (!pendingFile.exists()) {
            try {
                pendingFile.getParentFile().mkdirs();
                pendingFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        pendingConfig = YamlConfiguration.loadConfiguration(pendingFile);
    }

    private void loadRaritiesFromConfig() {
        rarityWeights.clear();
        rarityNames.clear();
        rarityColors.clear();
        FileConfiguration cfg = plugin.getConfig();
        for (Rarity r : Rarity.values()) {
            String base = "rarities." + r.name() + ".";
            rarityWeights.put(r, Math.max(1, cfg.getInt(base + "weight", r.getDefaultWeight())));
            rarityNames.put(r, cfg.getString(base + "name", r.getDefaultDisplayName()));
            rarityColors.put(r, cfg.getString(base + "color", r.getDefaultColor()));
        }
    }

    private void loadKeysFile() {
        keys.clear();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(keysFile);
        ConfigurationSection root = cfg.getConfigurationSection("keys");
        if (root == null) {
            keys.putAll(DefaultKeysFactory.createDefaults());
            saveKeys();
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            String displayName = sec.getString("display-name", "&#F8BEFB" + id);
            int level = sec.getInt("level", 1);
            int minRolls = sec.getInt("min-rolls", 1);
            int maxRolls = sec.getInt("max-rolls", minRolls);
            List<String> desc = sec.getStringList("description");
            VaultKeyType keyType = new VaultKeyType(id, displayName, level, minRolls, maxRolls, desc);

            ConfigurationSection itemsSec = sec.getConfigurationSection("entries");
            if (itemsSec != null) {
                for (String entryId : itemsSec.getKeys(false)) {
                    ConfigurationSection es = itemsSec.getConfigurationSection(entryId);
                    if (es == null) continue;
                    ItemStack stack = es.getItemStack("item");
                    if (stack == null || stack.getType() == Material.AIR) continue;
                    String cmd = es.getString("command", null);
                    Rarity rarity = Rarity.fromName(es.getString("rarity"), Rarity.RARE);
                    int customWeight = es.getInt("custom-weight", 0);
                    keyType.getEntries().add(new LootEntry(entryId, stack, cmd, rarity, customWeight));
                }
            }
            keys.put(id.toLowerCase(Locale.ROOT), keyType);
        }
    }

    public synchronized void saveKeys() {
        if (keysFile == null) {
            keysFile = new File(plugin.getDataFolder(), "keys.yml");
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (VaultKeyType keyType : keys.values()) {
            String base = "keys." + keyType.getId() + ".";
            cfg.set(base + "display-name", keyType.getDisplayName());
            cfg.set(base + "level", keyType.getLevel());
            cfg.set(base + "min-rolls", keyType.getMinRolls());
            cfg.set(base + "max-rolls", keyType.getMaxRolls());
            cfg.set(base + "description", keyType.getDescription());
            int index = 0;
            for (LootEntry entry : keyType.getEntries()) {
                String entryKey = entry.getId() != null && !entry.getId().isEmpty()
                        ? entry.getId()
                        : ("item_" + index);
                String ep = base + "entries." + entryKey + ".";
                cfg.set(ep + "rarity", entry.getRarity().name());
                if (entry.getCustomWeight() > 0) {
                    cfg.set(ep + "custom-weight", entry.getCustomWeight());
                }
                if (entry.hasCommand()) {
                    cfg.set(ep + "command", entry.getCommand());
                }
                cfg.set(ep + "item", entry.getPreviewItem());
                index++;
            }
        }
        try {
            keysFile.getParentFile().mkdirs();
            cfg.save(keysFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить keys.yml: " + ex.getMessage());
        }
    }

    public Collection<VaultKeyType> getAllKeys() {
        return Collections.unmodifiableCollection(keys.values());
    }

    public VaultKeyType getKey(String id) {
        if (id == null) return null;
        return keys.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<Rarity, Integer> getRarityWeights() {
        return rarityWeights;
    }

    public String getRarityName(Rarity rarity) {
        return rarityNames.getOrDefault(rarity, rarity.getDefaultDisplayName());
    }

    public String getRarityColor(Rarity rarity) {
        return rarityColors.getOrDefault(rarity, rarity.getDefaultColor());
    }

    public VaultKeyType resolveKeyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String keyId = meta.getPersistentDataContainer().get(keyPdc, PersistentDataType.STRING);
        if (keyId == null) {
            return null;
        }
        return getKey(keyId);
    }

    public boolean isAnyVaultKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(keyPdc, PersistentDataType.STRING);
    }

    public void giveKeyToPlayer(Player onlineTarget, String targetName, VaultKeyType keyType, int amount) {
        if (onlineTarget != null && onlineTarget.isOnline()) {
            giveOrDropProtected(onlineTarget, keyType.createKeyItem(keyPdc, amount));
            return;
        }
        String lower = targetName.toLowerCase(Locale.ROOT);
        String base = "offline-keys." + lower;
        List<String> list = pendingConfig.getStringList(base);
        list.add(keyType.getId() + ":" + amount);
        pendingConfig.set(base, list);
        savePending();
    }

    public void deliverReward(Player player, LootEntry entry) {
        if (player == null || !player.isOnline()) {
            if (player != null) {
                enqueuePendingReward(player.getUniqueId(), entry);
            }
            return;
        }
        if (entry.hasCommand()) {
            executeRewardCommandSafely(player, entry);
        } else {
            giveOrDropProtected(player, entry.getPreviewItem());
        }
    }

    private void executeRewardCommandSafely(Player player, LootEntry entry) {
        String cmd = entry.getCommand()
                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName())
                .replace("{player}", player.getName());
        PlayerInventory inv = player.getInventory();
        if (inv.firstEmpty() != -1) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return;
        }
        ItemStack displaced = inv.getItem(8);
        inv.setItem(8, null);
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } finally {
            ItemStack newlyGiven = inv.getItem(8);
            inv.setItem(8, displaced);
            if (newlyGiven != null && newlyGiven.getType() != Material.AIR) {
                dropProtectedAtFeet(player, newlyGiven);
            }
        }
    }

    public void giveOrDropProtected(Player player, ItemStack reward) {
        if (reward == null || reward.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward.clone());
        if (!leftovers.isEmpty()) {
            for (ItemStack left : leftovers.values()) {
                dropProtectedAtFeet(player, left);
            }
        }
    }

    private void dropProtectedAtFeet(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        Location loc = player.getLocation();
        UUID ownerId = player.getUniqueId();
        loc.getWorld().dropItem(loc, stack.clone(), dropped -> {
            dropped.setOwner(ownerId);
            dropped.setPickupDelay(0);
            dropped.setCanMobPickup(false);
            dropped.setInvulnerable(true);
            dropped.getPersistentDataContainer().set(protectedDropPdc, PersistentDataType.STRING, ownerId.toString());
        });
    }

    public synchronized void enqueuePendingReward(UUID playerUuid, LootEntry entry) {
        String base = "pending-rewards." + playerUuid;
        int idx = 0;
        ConfigurationSection sec = pendingConfig.getConfigurationSection(base);
        if (sec != null) {
            idx = sec.getKeys(false).size();
        }
        String path = base + ".r" + System.nanoTime() + "_" + idx;
        if (entry.hasCommand()) {
            pendingConfig.set(path + ".command", entry.getCommand());
        }
        pendingConfig.set(path + ".item", entry.getPreviewItem());
        pendingConfig.set(path + ".rarity", entry.getRarity().name());
        savePending();
    }

    public synchronized void flushPendingFor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        boolean changed = false;
        String lower = player.getName().toLowerCase(Locale.ROOT);
        String keyQueuePath = "offline-keys." + lower;
        List<String> queuedKeys = pendingConfig.getStringList(keyQueuePath);
        if (!queuedKeys.isEmpty()) {
            for (String token : queuedKeys) {
                String[] parts = token.split(":", 2);
                VaultKeyType type = getKey(parts[0]);
                int amt = 1;
                if (parts.length == 2) {
                    try {
                        amt = Math.max(1, Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (type != null) {
                    giveOrDropProtected(player, type.createKeyItem(keyPdc, amt));
                    player.sendMessage(plugin.msg("key-received")
                            .replace("{key}", TextUtil.color(type.getDisplayName()))
                            .replace("{amount}", String.valueOf(amt)));
                }
            }
            pendingConfig.set(keyQueuePath, null);
            changed = true;
        }

        String rewardsPath = "pending-rewards." + player.getUniqueId();
        ConfigurationSection sec = pendingConfig.getConfigurationSection(rewardsPath);
        if (sec != null) {
            int delivered = 0;
            for (String k : sec.getKeys(false)) {
                ConfigurationSection rs = sec.getConfigurationSection(k);
                if (rs == null) continue;
                ItemStack st = rs.getItemStack("item");
                String cmd = rs.getString("command", null);
                Rarity rar = Rarity.fromName(rs.getString("rarity"), Rarity.RARE);
                if (st != null && st.getType() != Material.AIR) {
                    deliverReward(player, new LootEntry(k, st, cmd, rar, 0));
                    delivered++;
                }
            }
            pendingConfig.set(rewardsPath, null);
            changed = true;
            if (delivered > 0) {
                player.sendMessage(plugin.msg("pending-delivered")
                        .replace("{count}", String.valueOf(delivered)));
            }
        }

        if (changed) {
            savePending();
        }
    }

    public boolean isProtectedDropForOther(Item item, UUID pickerUuid) {
        if (item == null) {
            return false;
        }
        String ownerStr = item.getPersistentDataContainer().get(protectedDropPdc, PersistentDataType.STRING);
        if (ownerStr == null) {
            return false;
        }
        return pickerUuid == null || !ownerStr.equals(pickerUuid.toString());
    }

    public boolean isProtectedDrop(Item item) {
        return item != null && item.getPersistentDataContainer().has(protectedDropPdc, PersistentDataType.STRING);
    }

    private synchronized void savePending() {
        try {
            pendingConfig.save(pendingFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить pending.yml: " + ex.getMessage());
        }
    }
}
