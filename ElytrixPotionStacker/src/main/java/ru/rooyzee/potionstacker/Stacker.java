package ru.rooyzee.potionstacker;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Правила стакинга зелий на нативном компоненте max_stack_size (Paper 1.21+).
 *
 * Никаких виртуальных счётчиков в PDC или в лоре: количество всегда равно
 * реальному ItemStack#getAmount(), а объединение, разделение, питьё и броски
 * обрабатывает сам ванильный сервер.
 */
public final class Stacker {

    public static final int HARD_MAX = 99;

    private static final NamespacedKey ELYTRIX_ITEM_KEY = new NamespacedKey("elytrixitem", "custom_item_id");

    private final Main plugin;
    private final Map<Material, Integer> materialLimits = new EnumMap<>(Material.class);
    private final Map<String, Integer> effectLimits = new HashMap<>();
    private final Map<String, Integer> customItemLimits = new HashMap<>();

    private int refreshTicks = 40;

    public Stacker(Main plugin) {
        this.plugin = plugin;
    }

    /** Перечитать настройки из config.yml. */
    public void load() {
        refreshTicks = Math.max(0, plugin.getConfig().getInt("refresh-ticks", 40));

        materialLimits.clear();
        ConfigurationSection limitsSection = plugin.getConfig().getConfigurationSection("limits");
        if (limitsSection != null) {
            for (String key : limitsSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("Неизвестный материал в limits: " + key);
                    continue;
                }
                materialLimits.put(material, clamp(limitsSection.getInt(key, 64)));
            }
        }

        if (materialLimits.isEmpty()) {
            int fallback = clamp(plugin.getConfig().getInt("max-stack-size", 64));
            materialLimits.put(Material.POTION, fallback);
            materialLimits.put(Material.SPLASH_POTION, fallback);
            materialLimits.put(Material.LINGERING_POTION, fallback);
        }

        effectLimits.clear();
        ConfigurationSection effectsSection = plugin.getConfig().getConfigurationSection("effect-limits");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                effectLimits.put(key.toUpperCase(Locale.ROOT), clamp(effectsSection.getInt(key, 64)));
            }
        }

        customItemLimits.clear();
        ConfigurationSection customSection = plugin.getConfig().getConfigurationSection("custom-limits");
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                customItemLimits.put(key.toLowerCase(Locale.ROOT), clamp(customSection.getInt(key, 64)));
            }
        }
    }

    public int getRefreshTicks() {
        return refreshTicks;
    }

    public Set<Material> getMaterials() {
        return materialLimits.keySet();
    }

    public Map<Material, Integer> getMaterialLimits() {
        return materialLimits;
    }

    private static int clamp(int value) {
        if (value < 1) {
            return 1;
        }
        return Math.min(value, HARD_MAX);
    }

    /** Управляет ли плагин этим предметом. */
    public boolean affects(ItemStack item) {
        return item != null && item.getType() != Material.AIR && materialLimits.containsKey(item.getType());
    }

    /** Стак зелий (> 1 шт.) — для защиты варочной стойки. */
    public boolean isStackedPotion(ItemStack item) {
        return item != null
                && (item.getType() == Material.POTION
                || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION
                || item.getType() == Material.GLASS_BOTTLE
                || affects(item))
                && item.getAmount() > 1;
    }

    /** Вычислить целевой лимит стака для конкретного экземпляра зелья. */
    public int resolveLimit(ItemStack item, ItemMeta meta) {
        int limit = materialLimits.getOrDefault(item.getType(), item.getType().getMaxStackSize());

        if (!customItemLimits.isEmpty()) {
            String customId = meta.getPersistentDataContainer().get(ELYTRIX_ITEM_KEY, PersistentDataType.STRING);
            if (customId != null) {
                Integer override = customItemLimits.get(customId.toLowerCase(Locale.ROOT));
                if (override != null) {
                    return override;
                }
            }
        }

        if (!effectLimits.isEmpty() && meta instanceof PotionMeta potionMeta) {
            int strictest = Integer.MAX_VALUE;
            PotionType base = potionMeta.getBasePotionType();
            if (base != null) {
                for (PotionEffect effect : base.getPotionEffects()) {
                    strictest = Math.min(strictest, lookupEffectLimit(effect.getType()));
                }
            }
            if (potionMeta.hasCustomEffects()) {
                for (PotionEffect effect : potionMeta.getCustomEffects()) {
                    strictest = Math.min(strictest, lookupEffectLimit(effect.getType()));
                }
            }
            if (strictest != Integer.MAX_VALUE) {
                limit = strictest;
            }
        }

        return limit;
    }

    private int lookupEffectLimit(PotionEffectType type) {
        if (type == null) {
            return Integer.MAX_VALUE;
        }
        NamespacedKey key = type.getKey();
        if (key != null) {
            Integer byKey = effectLimits.get(key.getKey().toUpperCase(Locale.ROOT));
            if (byKey != null) {
                return byKey;
            }
        }
        String legacy = type.getName();
        if (legacy != null) {
            Integer byName = effectLimits.get(legacy.toUpperCase(Locale.ROOT));
            if (byName != null) {
                return byName;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Привести компонент max_stack_size предмета к актуальному лимиту.
     *
     * Если лимит равен ванильному и количество не превышает его — компонент
     * очищается (предмет остаётся 100% чистым ванильным).
     * Если в стаке уже больше нового лимита — компонент держится равным
     * текущему количеству, чтобы кодек Paper 1.21 не отбросил стак при сохранении.
     *
     * @return true, если мета предмета изменилась
     */
    public boolean normalize(ItemStack item) {
        if (!affects(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        int configured = resolveLimit(item, meta);
        int target = Math.min(HARD_MAX, Math.max(configured, item.getAmount()));
        int vanillaMax = item.getType().getMaxStackSize();

        if (target == vanillaMax) {
            if (!meta.hasMaxStackSize()) {
                return false;
            }
            meta.setMaxStackSize(null);
            return item.setItemMeta(meta);
        }

        if (meta.hasMaxStackSize() && meta.getMaxStackSize() == target) {
            return false;
        }

        meta.setMaxStackSize(target);
        return item.setItemMeta(meta);
    }

    /** Нормализовать все стаки в инвентаре. */
    public int normalize(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }

        int changed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && normalize(item)) {
                inventory.setItem(slot, item);
                changed++;
            }
        }
        return changed;
    }
}
