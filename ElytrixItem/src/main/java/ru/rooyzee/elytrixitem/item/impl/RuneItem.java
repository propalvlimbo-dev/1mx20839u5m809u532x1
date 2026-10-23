package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import ru.rooyzee.elytrixitem.Main;
import ru.rooyzee.elytrixitem.item.OffhandBonusItem;
import ru.rooyzee.elytrixitem.util.ColorUtil;
import ru.rooyzee.elytrixitem.util.ItemStackUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Шар: голова с текстурой, которую носят во второй руке.
 *
 * Сразу даёт только бонусы к характеристикам. После нужного числа убийств
 * шар пробуждается и дополнительно включает бесконечные эффекты.
 * Число убийств хранится в самом предмете, поэтому у каждого шара своё.
 */
public final class RuneItem implements OffhandBonusItem {

    private static final String SKIN_URL = "https://textures.minecraft.net/texture/";

    private final NamespacedKey key;
    private final NamespacedKey killsKey;
    private final String id;
    private final String name;
    private final String texture;
    private final int killsRequired;
    private final List<Stat> stats = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();

    public RuneItem(Main plugin, String id, String name, String texture, int killsRequired) {
        this.key = new NamespacedKey(plugin, "custom_item_id");
        this.killsKey = new NamespacedKey(plugin, "rune_kills");
        this.id = id;
        this.name = name;
        this.texture = texture;
        this.killsRequired = killsRequired;
    }

    public RuneItem stat(Attribute attribute, double amount, String label) {
        this.stats.add(new Stat(attribute, amount, label));
        return this;
    }

    /** Эффект, который включится после пробуждения. */
    public RuneItem awaken(PotionEffectType type, int level, String label) {
        this.effects.add(new Effect(type, level, label));
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getItemName() {
        return name;
    }

    public int getKillsRequired() {
        return killsRequired;
    }

    @Override
    public List<Stat> getStats() {
        return stats;
    }

    @Override
    public List<Effect> getEffects(ItemStack item) {
        return isAwakened(item) ? effects : Collections.emptyList();
    }

    @Override
    public boolean isStackable() {
        return false;
    }

    @Override
    public ItemStack createItem() {
        return build(0);
    }

    /** Копия шара с указанным числом убийств. */
    public ItemStack build(int kills) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = item.getItemMeta();

        if (raw instanceof SkullMeta meta) {
            meta.setOwnerProfile(buildProfile());
            meta.setDisplayName(ColorUtil.gradient("« " + name + " »"));

            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            for (Stat stat : stats) {
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &fБонус: &#F8BEFB+" + stat.getLabel()));
            }
            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));

            int shown = Math.min(kills, killsRequired);
            if (kills >= killsRequired) {
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &fПробуждён: &#F8BEFB" + describeEffects()));
            } else {
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &fПробуждение: &#F8BEFB"
                        + shown + "/" + killsRequired + " &fубийств"));
                lore.add(ColorUtil.color("&#F8BEFB&l┃ &fПосле: &#F8BEFB" + describeEffects()));
            }

            lore.add(ColorUtil.color("&#F8BEFB&l┃ "));
            lore.add(ColorUtil.color("&7● &fРаботает во второй руке"));
            meta.setLore(lore);

            meta.setCustomModelData(3);

            if ("donshar".equals(id)) {
                meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            }

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
            meta.getPersistentDataContainer().set(killsKey, PersistentDataType.INTEGER, kills);
            item.setItemMeta(meta);
        }

        ItemStackUtil.hideAllFlags(item);
        return item;
    }

    public int getKills(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        Integer kills = item.getItemMeta().getPersistentDataContainer()
                .get(killsKey, PersistentDataType.INTEGER);
        return kills == null ? 0 : kills;
    }

    public boolean isAwakened(ItemStack item) {
        return getKills(item) >= killsRequired;
    }

    private String describeEffects() {
        StringBuilder builder = new StringBuilder();
        for (Effect effect : effects) {
            if (builder.length() > 0) {
                builder.append("&7, &#F8BEFB");
            }
            builder.append(effect.describe());
        }
        return builder.toString();
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
}
