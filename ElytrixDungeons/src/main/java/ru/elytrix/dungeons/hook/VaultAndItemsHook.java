package ru.elytrix.dungeons.hook;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import ru.elytrix.dungeons.util.TextUtil;

public final class VaultAndItemsHook {

    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("https?://textures\\.minecraft\\.net/texture/[A-Za-z0-9]+");
    private static final String SNAKE_SPHERE_B64 =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTFiNGIyMDE4OWQ5MDJiYzZjM2ZhYmUyM2Q5ZjU3ODY5N2Y1NDRjZjQxNTMxNzc4NzM3NDExODM5M2RhNCJ9fX0=";

    private final NamespacedKey vaultKeyPdc = new NamespacedKey("elytrixvault", "key_id");
    private final NamespacedKey dungeonItemPdc = new NamespacedKey("elytrixdungeons", "item_id");

    /**
     * Canonical Ghost Shard (Призрачный осколок — Material.AMETHYST_SHARD) dropped 100% by Ghosts
     * inside Surface Houses and ready to be exchanged at the upcoming Trader!
     */
    public ItemStack createGhostShard(int amount) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color("&7« &#F8BEFBПризрачный осколок &7»"));
            meta.setLore(TextUtil.colorList(List.of(
                    "&#F8BEFB&l┃",
                    "&#F8BEFB&l┃ &fДобывается с Призраков в Наземных домах",
                    "&#F8BEFB&l┃ &fОбменивается на ценные награды у Торговца",
                    "&#F8BEFB&l┃"
            )));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(dungeonItemPdc, PersistentDataType.STRING, "ghost_shard");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isGhostShard(ItemStack stack) {
        if (stack == null || stack.getType() != Material.AMETHYST_SHARD || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && "ghost_shard".equals(
                meta.getPersistentDataContainer().get(dungeonItemPdc, PersistentDataType.STRING));
    }

    public ItemStack createVaultKey(String keyId, int amount) {
        try {
            Plugin v = Bukkit.getPluginManager().getPlugin("ElytrixVault");
            if (v != null && v.isEnabled()) {
                Object km = v.getClass().getMethod("getKeyManager").invoke(v);
                Method mk = km.getClass().getMethod("createKeyItem", String.class, int.class);
                Object res = mk.invoke(km, keyId, amount);
                if (res instanceof ItemStack st) return st;
            }
        } catch (Throwable ignored) {
        }
        boolean ominous = keyId.equalsIgnoreCase("void") || keyId.equalsIgnoreCase("unique");
        ItemStack stack = new ItemStack(ominous ? Material.OMINOUS_TRIAL_KEY : Material.TRIAL_KEY, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color("&7« " + vaultKeyDisplayName(keyId) + " &7»"));
            meta.setLore(TextUtil.colorList(List.of(
                    "&#F8BEFB&l┃",
                    "&#F8BEFB&l┃ &fИспользуйте на &#F8BEFBХранилище&f!",
                    "&#F8BEFB&l┃"
            )));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(vaultKeyPdc, PersistentDataType.STRING, keyId.toLowerCase(Locale.ROOT));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static String vaultKeyDisplayName(String keyId) {
        return switch (keyId.toLowerCase(Locale.ROOT)) {
            case "rusty" -> "&#C88A65Ржавый ключ";
            case "knowledge" -> "&#7FD4FFКлюч Знаний";
            case "royal" -> "&#FFD36EКоролевский ключ";
            case "void" -> "&#B87DFFПустотный ключ";
            default -> "&#F8BEFBУникальный ключ";
        };
    }

    public ItemStack resolveShopOrKitItem(String externalId, int amount) {
        try {
            Plugin ei = Bukkit.getPluginManager().getPlugin("ElytrixItem");
            if (ei != null && ei.isEnabled()) {
                for (String getter : new String[]{"getItemRegistry", "getItemManager", "getRegistry"}) {
                    try {
                        Object reg = ei.getClass().getMethod(getter).invoke(ei);
                        for (String mName : new String[]{"createItem", "getItem", "build"}) {
                            try {
                                Object out = reg.getClass().getMethod(mName, String.class).invoke(reg, externalId);
                                if (out instanceof ItemStack st) {
                                    ItemStack copy = st.clone();
                                    copy.setAmount(Math.max(1, amount));
                                    return copy;
                                }
                            } catch (NoSuchMethodException ignored) {
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        ItemStack built = buildCanonicalShopOrKitItem(externalId);
        built.setAmount(Math.max(1, amount));
        return built;
    }

    public static ItemStack buildCanonicalShopOrKitItem(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "shulker_sword" -> enchantedGear(Material.NETHERITE_SWORD,
                    "&#DC00FA« &#DC05FAМ&#DC0AFBе&#DC0FFBч &#DC18FBш&#DD1DFBа&#DD22FCл&#DD26FCк&#DD2BFCе&#DD30FCр&#DD35FCа &#DD3EFD»",
                    new MapEnchant(Enchantment.SHARPNESS, 6),
                    new MapEnchant(Enchantment.FIRE_ASPECT, 2),
                    new MapEnchant(Enchantment.MENDING, 1),
                    new MapEnchant(Enchantment.UNBREAKING, 4));
            case "shulker_pickaxe" -> enchantedGear(Material.NETHERITE_PICKAXE,
                    "&#DC00FA« &#DC04FAК&#DC08FAи&#DC0CFBр&#DC10FBк&#DC14FBа &#DC1CFBш&#DC20FBа&#DD24FCл&#DD28FCк&#DD2CFCе&#DD30FCр&#DD34FCа &#DD3CFD»",
                    new MapEnchant(Enchantment.EFFICIENCY, 6),
                    new MapEnchant(Enchantment.FORTUNE, 3),
                    new MapEnchant(Enchantment.MENDING, 1),
                    new MapEnchant(Enchantment.UNBREAKING, 4));
            case "kit_chestplate" -> enchantedGear(Material.NETHERITE_CHESTPLATE,
                    "&7« &#F8BEFBНагрудник Вечности &7»",
                    new MapEnchant(Enchantment.PROTECTION, 5),
                    new MapEnchant(Enchantment.MENDING, 1),
                    new MapEnchant(Enchantment.UNBREAKING, 4));
            case "kit_helmet" -> enchantedGear(Material.NETHERITE_HELMET,
                    "&7« &#F8BEFBШлем Вечности &7»",
                    new MapEnchant(Enchantment.PROTECTION, 5),
                    new MapEnchant(Enchantment.MENDING, 1),
                    new MapEnchant(Enchantment.UNBREAKING, 4));
            case "kit_leggings" -> enchantedGear(Material.NETHERITE_LEGGINGS,
                    "&7« &#F8BEFBПоножи Вечности &7»",
                    new MapEnchant(Enchantment.PROTECTION, 5),
                    new MapEnchant(Enchantment.MENDING, 1),
                    new MapEnchant(Enchantment.UNBREAKING, 4));
            case "kit_boots" -> enchantedGear(Material.NETHERITE_BOOTS,
                    "&7« &#F8BEFBБотинки Вечности &7»",
                    new MapEnchant(Enchantment.PROTECTION, 5),
                    new MapEnchant(Enchantment.MENDING, 1),
                    new MapEnchant(Enchantment.UNBREAKING, 4));
            case "shar1" -> sphereHead("&#00FA0C« &#0DFB0AШ&#19FC08а&#26FD06р &#40FE02З&#4DFF00м&#4EFF00е&#50FF00и &#52FF00»",
                    SNAKE_SPHERE_B64, List.of(
                            "&fСпособности &8(В левой руке)&f:",
                            " &8● &x&0&0&F&A&0&CСила II",
                            " &8● &x&0&0&F&A&0&CСкорость I",
                            " &8● &x&0&0&F&A&0&CСопротивление I",
                            " &8● &x&0&0&F&A&0&CОгнестойкость I"));
            case "totem1" -> doctorTotem();
            case "infinite_firework" -> simpleNamed(Material.FIREWORK_ROCKET, "&7« &#F8BEFBБесконечный фейерверк &7»",
                    List.of("&#F8BEFB&l┃ &fНе тратится при полёте на элитрах"));
            case "explosive_trap" -> simpleNamed(Material.SCULK_SHRIEKER, "&7« &#F8BEFBВзрывная трапка &7»",
                    List.of("&#F8BEFB&l┃ &fБоевой предмет из /shop"));
            case "anti_pearl" -> simpleNamed(Material.ENDER_EYE, "&7« &#F8BEFBАнти-перл &7»",
                    List.of("&#F8BEFB&l┃ &fБлокирует эндер-жемчуг врага"));
            case "experience_pot" -> simpleNamed(Material.EXPERIENCE_BOTTLE, "&7« &#F8BEFBСосуд опыта &7»",
                    List.of("&#F8BEFB&l┃ &fХранилище концентрированного опыта"));
            case "kirkaraskola" -> enchantedGear(Material.NETHERITE_PICKAXE, "&7« &#F8BEFBКирка Раскола &7»",
                    new MapEnchant(Enchantment.EFFICIENCY, 5), new MapEnchant(Enchantment.UNBREAKING, 3));
            case "potion_voina" -> customPotion("&7« &cЗелье Воина &7»", Color.fromRGB(220, 45, 70),
                    new PotionEffect(PotionEffectType.STRENGTH, 20 * 180, 1),
                    new PotionEffect(PotionEffectType.SPEED, 20 * 180, 1));
            case "potion_salvation" -> customPotion("&7« &#96FF91Зелье Спасения &7»", Color.fromRGB(110, 245, 150),
                    new PotionEffect(PotionEffectType.REGENERATION, 20 * 45, 1),
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 240, 0),
                    new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1));
            case "potion_healt" -> customPotion("&7« &#FF6B9EЗелье Исцеления &7»", Color.fromRGB(255, 90, 140),
                    new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1),
                    new PotionEffect(PotionEffectType.REGENERATION, 20 * 30, 1));
            default -> new ItemStack(Material.TOTEM_OF_UNDYING);
        };
    }

    public record MapEnchant(Enchantment enchantment, int level) {}

    public static ItemStack enchantedGear(Material mat, String displayName, MapEnchant... enchants) {
        ItemStack st = new ItemStack(mat);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            if (displayName != null) meta.setDisplayName(TextUtil.color(displayName));
            for (MapEnchant e : enchants) {
                if (e.enchantment() != null) meta.addEnchant(e.enchantment(), e.level(), true);
            }
            st.setItemMeta(meta);
        }
        return st;
    }

    public static ItemStack enchantedBook(Enchantment ench, int level) {
        ItemStack st = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta raw = st.getItemMeta();
        if (raw instanceof EnchantmentStorageMeta esm && ench != null) {
            esm.addStoredEnchant(ench, level, true);
            st.setItemMeta(esm);
        }
        return st;
    }

    public static ItemStack customPotion(String name, Color color, PotionEffect... effects) {
        ItemStack st = new ItemStack(Material.SPLASH_POTION);
        ItemMeta raw = st.getItemMeta();
        if (raw instanceof PotionMeta pm) {
            pm.setDisplayName(TextUtil.color(name));
            pm.setColor(color);
            for (PotionEffect ef : effects) pm.addCustomEffect(ef, true);
            st.setItemMeta(pm);
        }
        return st;
    }

    private static ItemStack doctorTotem() {
        ItemStack st = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color("&#FB0000« &#FC000AТ&#FC0014о&#FD001Fт&#FD0029е&#FE0033м &#FE0042д&#FE0049о&#FE004Fк&#FE0056т&#FE005Cо&#FF0063р&#FF0069а &#FF0076»"));
            meta.setLore(TextUtil.colorList(List.of("&fСпособности:", " &8● &#FB0000+4 Макс. Здоровья &8(В левой руке)")));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                    new NamespacedKey("elytrixdungeons", "doctor_totem_hp"),
                    4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.OFFHAND));
            st.setItemMeta(meta);
        }
        return st;
    }

    private static ItemStack sphereHead(String name, String b64, List<String> lore) {
        ItemStack st = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = st.getItemMeta();
        if (raw instanceof SkullMeta sm) {
            sm.setDisplayName(TextUtil.color(name));
            sm.setLore(TextUtil.colorList(lore));
            try {
                String json = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
                Matcher m = TEXTURE_URL_PATTERN.matcher(json);
                if (m.find()) {
                    PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "ElytriX");
                    PlayerTextures tex = profile.getTextures();
                    tex.setSkin(URI.create(m.group(0)).toURL());
                    profile.setTextures(tex);
                    sm.setOwnerProfile(profile);
                }
            } catch (Throwable ignored) {
            }
            st.setItemMeta(sm);
        }
        return st;
    }

    private static ItemStack simpleNamed(Material mat, String name, List<String> lore) {
        ItemStack st = new ItemStack(mat);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name));
            meta.setLore(TextUtil.colorList(lore));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            st.setItemMeta(meta);
        }
        return st;
    }
}
