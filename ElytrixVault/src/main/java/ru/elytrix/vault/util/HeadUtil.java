package ru.elytrix.vault.util;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public final class HeadUtil {

    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Map<String, PlayerProfile> CACHE = new ConcurrentHashMap<>();

    private HeadUtil() {}

    public static ItemStack createTexturedHead(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isEmpty()) {
            return head;
        }
        try {
            PlayerProfile profile = CACHE.computeIfAbsent(base64, key -> {
                try {
                    String json = new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8);
                    Matcher matcher = SKIN_URL.matcher(json);
                    if (!matcher.find()) {
                        return null;
                    }
                    String urlStr = matcher.group(1);
                    UUID deterministicId = UUID.nameUUIDFromBytes(urlStr.getBytes(StandardCharsets.UTF_8));
                    PlayerProfile created = Bukkit.createPlayerProfile(deterministicId, "ElytrixVault");
                    PlayerTextures textures = created.getTextures();
                    textures.setSkin(new URL(urlStr));
                    created.setTextures(textures);
                    return created;
                } catch (Exception ignored) {
                    return null;
                }
            });
            if (profile != null && head.getItemMeta() instanceof SkullMeta skullMeta) {
                skullMeta.setOwnerProfile(profile);
                head.setItemMeta(skullMeta);
            }
        } catch (Exception ignored) {
        }
        return head;
    }
}
