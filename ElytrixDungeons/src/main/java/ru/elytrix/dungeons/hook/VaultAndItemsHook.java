package ru.elytrix.dungeons.hook;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import ru.elytrix.dungeons.util.TextUtil;

public final class VaultAndItemsHook {

    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    public boolean isVaultKey(ItemStack stack, String expectedKeyId) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        String directPdc = meta.getPersistentDataContainer()
                .get(new NamespacedKey("elytrixvault", "key_id"), PersistentDataType.STRING);
        if (expectedKeyId.equalsIgnoreCase(directPdc)) {
            return true;
        }
        Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("ElytrixVault");
        if (vaultPlugin != null && vaultPlugin.isEnabled()) {
            try {
                Object reg = vaultPlugin.getClass().getMethod("getKeyRegistry").invoke(vaultPlugin);
                Object keyType = reg.getClass().getMethod("resolveKeyItem", ItemStack.class).invoke(reg, stack);
                if (keyType != null) {
                    Object id = keyType.getClass().getMethod("getId").invoke(keyType);
                    return expectedKeyId.equalsIgnoreCase(String.valueOf(id));
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public boolean depositCoins(Player player, int amount, String fallbackCommandTemplate) {
        if (amount <= 0) {
            return true;
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp != null && rsp.getProvider() != null) {
                    rsp.getProvider().depositPlayer((OfflinePlayer) player, (double) amount);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        if (fallbackCommandTemplate != null && !fallbackCommandTemplate.isBlank()) {
            String cmd = fallbackCommandTemplate
                    .replace("{player}", player.getName())
                    .replace("{amount}", String.valueOf(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return true;
        }
        return false;
    }

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

    public static ItemStack enchantedGear(Material mat, String name, MapEnchant... enchants) {
        ItemStack st = new ItemStack(mat, 1);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(TextUtil.color(name));
            }
            for (MapEnchant me : enchants) {
                if (me.ench() != null) {
                    meta.addEnchant(me.ench(), me.level(), true);
                }
            }
            st.setItemMeta(meta);
        }
        return st;
    }

    public record MapEnchant(Enchantment ench, int level) {}

    public static ItemStack texturedHead(String base64, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta raw = head.getItemMeta();
        if (raw instanceof SkullMeta meta) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(TextUtil.colorList(lore));
            try {
                String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
                Matcher m = SKIN_URL.matcher(json);
                if (m.find()) {
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "elytrix_orb");
                    PlayerTextures tex = profile.getTextures();
                    tex.setSkin(URI.create(m.group(1)).toURL());
                    profile.setTextures(tex);
                    meta.setOwnerProfile(profile);
                }
            } catch (Throwable ignored) {
            }
            head.setItemMeta(meta);
        }
        return head;
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
