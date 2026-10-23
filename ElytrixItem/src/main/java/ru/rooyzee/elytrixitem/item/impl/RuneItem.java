package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.CustomItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Шар: голова с текстурой, которая даёт эффекты, пока лежит в инвентаре.
 *
 * Раньше это был тип RUNE из ShadowCustomItems.
 */
public final class RuneItem implements CustomItem {

    private static final String SKIN_URL = "https://textures.minecraft.net/texture/";

    private final NamespacedKey key;
    private final String id;
    private final String name;
    private final String texture;
    private final List<Effect> effects;

    public RuneItem(Main plugin, String id, String name, String texture, Effect... effects) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.id = id;
        this.name = name;
        this.texture = texture;
        this.effects = List.of(effects);
    }

    public static Effect effect(PotionEffectType type, int level, String label) {
        return new Effect(type, level, label);
    }

    public List<Effect> getEffects() {
        return effects;
    }

    /** Та же голова, что и в ShadowCustomItems: текстура + CustomModelData 3. */
    private PlayerProfile buildProfile() {
        UUID uuid = UUID.nameUUIDFromBytes(("elytrix:" + texture).getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "elytrix");
        PlayerTextures textures = profile.getTextures();

        try {
            textures.setSkin(new URL(SKIN_URL + texture));
        } catch (MalformedURLException ignored) {
            // Текстура битая — голова останется стандартной.
        }

        profile.setTextures(textures);
        return profile;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isStackable() {
        return false;
    }

    @Override
    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = item.getItemMeta();

        if (raw instanceof SkullMeta meta) {
            meta.setOwnerProfile(buildProfile());
            meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            for (Effect effect : effects) {
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &fЭффект: &#F8BEFB" + effect.label + " "
                        + roman(effect.level)));
            }
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            meta.setLore(lore);

            meta.setCustomModelData(3);

            if ("donshar".equals(id)) {
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            }

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }

        ItemStackUtil.hideAllFlags(item);
        return item;
    }

    private static String roman(int value) {
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (value >= 1 && value <= 10) {
            return ones[value];
        }
        return String.valueOf(value);
    }

    public static final class Effect {

        private final PotionEffectType type;
        private final int level;
        private final String label;

        public Effect(PotionEffectType type, int level, String label) {
            this.type = type;
            this.level = level;
            this.label = label;
        }

        public PotionEffectType getType() {
            return type;
        }

        public int getLevel() {
            return level;
        }
    }
}
