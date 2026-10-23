package ru.elytrix.vault.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.vault.util.TextUtil;

public class VaultKeyType {

    private final String id;
    private String displayName;
    private int level;
    private int minRolls;
    private int maxRolls;
    private List<String> description;
    private final List<LootEntry> entries = new ArrayList<>();

    public VaultKeyType(String id, String displayName, int level, int minRolls, int maxRolls, List<String> description) {
        this.id = id;
        this.displayName = displayName;
        this.level = clamp(level, 1, 5);
        this.minRolls = Math.max(1, minRolls);
        this.maxRolls = Math.max(this.minRolls, maxRolls);
        this.description = description == null ? new ArrayList<>() : new ArrayList<>(description);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = clamp(level, 1, 5);
    }

    public int getMinRolls() {
        return minRolls;
    }

    public void setMinRolls(int minRolls) {
        this.minRolls = clamp(minRolls, 1, 16);
        if (this.maxRolls < this.minRolls) {
            this.maxRolls = this.minRolls;
        }
    }

    public int getMaxRolls() {
        return maxRolls;
    }

    public void setMaxRolls(int maxRolls) {
        this.maxRolls = clamp(maxRolls, 1, 16);
        if (this.minRolls > this.maxRolls) {
            this.minRolls = this.maxRolls;
        }
    }

    public List<String> getDescription() {
        return description;
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    public Material getKeyMaterial() {
        return level <= 3 ? Material.TRIAL_KEY : Material.OMINOUS_TRIAL_KEY;
    }

    public String getKeyMaterialTitle() {
        return level <= 3 ? "Ключ испытаний" : "Зловещий ключ";
    }

    public ItemStack createKeyItem(NamespacedKey keyPdc, int amount) {
        int safeAmount = clamp(amount, 1, 64);
        ItemStack stack = new ItemStack(getKeyMaterial(), safeAmount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color("&7« " + displayName + " &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.color("&#F8BEFB&l┃ "));
            lore.add(TextUtil.color("&#F8BEFB&l┃ &fУровень лута: &#F8BEFB" + level + "/5"));
            if (minRolls == maxRolls) {
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fВыпадает наград: &#F8BEFB" + minRolls));
            } else {
                lore.add(TextUtil.color("&#F8BEFB&l┃ &fВыпадает наград: &#F8BEFBот " + minRolls + " до " + maxRolls));
            }
            for (String line : description) {
                lore.add(TextUtil.color("&#F8BEFB&l┃ &7" + line));
            }
            lore.add(TextUtil.color("&#F8BEFB&l┃ "));
            lore.add(TextUtil.color("&7● &fНажмите ПКМ по Хранилищу"));
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(keyPdc, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public int getTotalWeight(Map<Rarity, Integer> rarityWeights) {
        int sum = 0;
        for (LootEntry entry : entries) {
            sum += entry.getEffectiveWeight(rarityWeights);
        }
        return sum;
    }

    public List<LootEntry> rollRewards(Map<Rarity, Integer> rarityWeights) {
        List<LootEntry> result = new ArrayList<>();
        if (entries.isEmpty()) {
            return result;
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = minRolls == maxRolls ? minRolls : rng.nextInt(minRolls, maxRolls + 1);
        List<LootEntry> pool = new ArrayList<>(entries);
        for (int i = 0; i < count; i++) {
            if (pool.isEmpty()) {
                pool.addAll(entries);
            }
            LootEntry chosen = pickWeighted(pool, rarityWeights, rng);
            if (chosen != null) {
                result.add(chosen);
                pool.remove(chosen);
            }
        }
        return result;
    }

    private static LootEntry pickWeighted(List<LootEntry> pool, Map<Rarity, Integer> rarityWeights, ThreadLocalRandom rng) {
        int total = 0;
        for (LootEntry entry : pool) {
            total += entry.getEffectiveWeight(rarityWeights);
        }
        if (total <= 0) {
            return pool.get(rng.nextInt(pool.size()));
        }
        int roll = rng.nextInt(total);
        int cursor = 0;
        for (LootEntry entry : pool) {
            cursor += entry.getEffectiveWeight(rarityWeights);
            if (roll < cursor) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
