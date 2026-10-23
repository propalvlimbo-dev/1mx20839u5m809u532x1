package ru.elytrix.dungeons.hook;

import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.elytrix.dungeons.util.TextUtil;

public final class VaultAndItemsHook {

    public ItemStack createVaultKey(String keyId, int amount) {
        int qty = Math.max(1, amount);
        Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("ElytrixVault");
        if (vaultPlugin != null && vaultPlugin.isEnabled()) {
            try {
                Method getRegistry = vaultPlugin.getClass().getMethod("getKeyRegistry");
                Object registry = getRegistry.invoke(vaultPlugin);
                Method getKey = registry.getClass().getMethod("getKey", String.class);
                Object keyType = getKey.invoke(registry, keyId);
                if (keyType != null) {
                    Method getPdc = registry.getClass().getMethod("getKeyPdc");
                    NamespacedKey pdc = (NamespacedKey) getPdc.invoke(registry);
                    Method createItem = keyType.getClass().getMethod("createKeyItem", NamespacedKey.class, int.class);
                    Object created = createItem.invoke(keyType, pdc, qty);
                    if (created instanceof ItemStack st) {
                        return st;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return createStandaloneCompatibleVaultKey(keyId, qty);
    }

    private ItemStack createStandaloneCompatibleVaultKey(String keyId, int amount) {
        String name;
        int level;
        switch (keyId) {
            case "rusty" -> { name = "&#C98A5BРжавый ключ"; level = 1; }
            case "knowledge" -> { name = "&#7EC8FFКлюч знаний"; level = 3; }
            case "royal" -> { name = "&#FFD55EКоролевский ключ"; level = 4; }
            case "void_key" -> { name = "&#B97BFFПустотный ключ"; level = 5; }
            case "unique" -> { name = "&#F8BEFBУникальный ключ"; level = 5; }
            default -> { name = "&#F8BEFBКлюч Хранилища"; level = 3; }
        }
        Material mat = level >= 4 ? Material.OMINOUS_TRIAL_KEY : Material.TRIAL_KEY;
        ItemStack stack = new ItemStack(mat, Math.max(1, Math.min(mat.getMaxStackSize(), amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(TextUtil.colorList(List.of(
                    "&#F8BEFB&l┃",
                    "&#F8BEFB&l┃ &fРедкость: " + TextUtil.stars(level),
                    "&#F8BEFB&l┃ &fДобыт в кастомном данже",
                    "&#F8BEFB&l┃",
                    "&7● &fНажмите ПКМ по Хранилищу на спавне"
            )));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("elytrixvault", "key_id"),
                    PersistentDataType.STRING,
                    keyId
            );
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack resolveShopOrKitItem(String itemId, int amount, ItemStack fallback) {
        int qty = Math.max(1, amount);
        Plugin ei = Bukkit.getPluginManager().getPlugin("ElytrixItem");
        if (ei != null && ei.isEnabled()) {
            try {
                Class<?> regClass = Class.forName("ru.elytrix.items.registry.CustomItemRegistry", true, ei.getClass().getClassLoader());
                for (Method m : regClass.getMethods()) {
                    if (ItemStack.class.isAssignableFrom(m.getReturnType())) {
                        if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == String.class && m.getParameterTypes()[1] == int.class) {
                            Object out = m.invoke(null, itemId, qty);
                            if (out instanceof ItemStack st) return st;
                        } else if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                            Object out = m.invoke(null, itemId);
                            if (out instanceof ItemStack st) {
                                st = st.clone();
                                st.setAmount(Math.min(st.getMaxStackSize(), qty));
                                return st;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        ItemStack copy = fallback.clone();
        copy.setAmount(Math.min(copy.getMaxStackSize(), qty));
        return copy;
    }

    public static ItemStack namedItem(Material mat, int amount, String name, List<String> lore, boolean glow) {
        ItemStack st = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(TextUtil.colorList(lore));
            }
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            st.setItemMeta(meta);
        }
        return st;
    }

    public static ItemStack enchantedBook(String title, Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta raw = book.getItemMeta();
        if (raw instanceof EnchantmentStorageMeta meta) {
            meta.setDisplayName(TextUtil.color(title));
            if (ench != null) {
                meta.addStoredEnchant(ench, level, true);
            }
            book.setItemMeta(meta);
        }
        return book;
    }

    public static ItemStack customPotion(Material mat, int amount, String name, Color color, List<PotionEffect> effects) {
        ItemStack st = new ItemStack(mat, Math.max(1, amount));
        ItemMeta raw = st.getItemMeta();
        if (raw instanceof PotionMeta meta) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setColor(color);
            for (PotionEffect effect : effects) {
                if (effect.getType() != null) {
                    meta.addCustomEffect(effect, true);
                }
            }
            st.setItemMeta(meta);
        }
        return st;
    }

    public static PotionEffect eff(PotionEffectType type, int seconds, int amplifier) {
        return new PotionEffect(type, seconds * 20, amplifier);
    }
}
