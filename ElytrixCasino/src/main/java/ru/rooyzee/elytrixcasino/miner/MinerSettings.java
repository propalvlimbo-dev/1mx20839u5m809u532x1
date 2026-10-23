package ru.rooyzee.elytrixcasino.miner;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Все настройки NPC Минёра и математики поля «Минёр».
 */
public final class MinerSettings {

    public static final String DEFAULT_SKIN_NAME = "SignalSMPLover";
    public static final String DEFAULT_SKIN_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc5Mjc2NTI4NDMyOCwKICAicHJvZmlsZUlkIiA6ICI3ZmJlNDY4NWJiNTA0NGRiYmU3Mjc1MzIxNjViNDc2YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJTaWduYWxTTVBMb3ZlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xNWJmZjUzMjBiYzRlMmRmYjg5MDQ3ZDZkOTAzNzdjYTExMTk3YWI5MzcyOGIyM2Q3NzkyZmMzZjMxNWUwODZiIgogICAgfQogIH0KfQ==";
    public static final String DEFAULT_SKIN_SIGNATURE =
            "kHdXL1UlyXd+cY1QwSYevTiR7oeI6H4r3EIr7S05k/XaQUWoiVHLE2/VfY8/Tl7I2mNxklZkwtb2zSG1MiQE3zezqokwSzSKl2n/hSHaR/U0xBwq+8rutDi2XotFKZyk9f8KueSCgug5yZBoKTwvbRLo1s5arxZt71pv0JuNEh4lG83qgmgwzLuBupR6VkxkUNQ90YxG4v5t1wOyHa7spYKBLBVdfvsPM0wTU4GRvVv38EPvsjcTWGbY8+TBC9GOXu3xwxxVzod7RHyLiPXfuZcvK36UARGE1PEI48G5vXpOFZZoiwCac2iz7lri02XpcrhkSlobLjy6ka59+agr8m4YMSMZ8Ar9Y8lReJzon8GKRgnWqn9PNJfBY4skbmOFohKcgACwz/m+TrgwlpdlOjWLUToejDHmDLokSGoWt5thCFIUA6tL3QJ7W3HPxFUi1jQprHRQjkuHBfCuo+kcglOAElzrtW/xjAqviaZoaQHTGLNDQ7klCshzKCQ5yiHO3isbDFwc3d+hv7oZ3ZBPrIiYm1ZyfoSwT1tFkIphh4GjX7QsmSh0fQcpa0peb4dJcDX/dHPFkK79IE1ZIJ+v2SbyOAVknTgNlhx+wo7dNT2Jp2jaxPTkzvnXMYmFgBlHUyKokLo0bIpbBdkAJIpzSLWQ87yjG5+Gt1FuVPH8QQM=";

    public static final int BOARD_SIZE = 45;

    public static final class CoinTier {
        private final Material icon;
        private final String displayName;
        private final double multiplier;
        private final int count;

        public CoinTier(Material icon, String displayName, double multiplier, int count) {
            this.icon = icon == null ? Material.GOLD_INGOT : icon;
            this.displayName = displayName == null ? "&#F8BEFB&lМонеты" : displayName;
            this.multiplier = Math.max(0.05D, multiplier);
            this.count = Math.max(0, count);
        }

        public Material getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public int getCount() {
            return count;
        }

        public long rewardForBet(long bet) {
            return Math.max(1L, Math.round(bet * multiplier));
        }
    }

    private String skinName = DEFAULT_SKIN_NAME;
    private String skinValue = DEFAULT_SKIN_VALUE;
    private String skinSignature = DEFAULT_SKIN_SIGNATURE;
    private int viewDistance = 48;
    private int throwIntervalTicks = 60;
    private float turnAngle = 28.0F;
    private int groundLifetimeTicks = 36;
    private double hologramHeight = 2.08D;
    private double hologramLineGap = 0.27D;
    private List<String> hologramLines = Collections.emptyList();

    private int minesCount = 16;
    private int emptyCount = 13;
    private List<CoinTier> coinTiers = Collections.emptyList();

    public void load(FileConfiguration config) {
        this.skinName = config.getString("miner.skin", DEFAULT_SKIN_NAME);
        this.skinValue = config.getString("miner.skin-value", DEFAULT_SKIN_VALUE);
        this.skinSignature = config.getString("miner.skin-signature", DEFAULT_SKIN_SIGNATURE);
        this.viewDistance = Math.max(8, config.getInt("miner.view-distance", 48));
        this.throwIntervalTicks = Math.max(10, config.getInt("miner.throw-interval-ticks", 60));
        this.turnAngle = (float) Math.max(5.0D, Math.min(90.0D, config.getDouble("miner.turn-angle", 28.0D)));
        this.groundLifetimeTicks = Math.max(10, config.getInt("miner.ground-lifetime-ticks", 36));
        this.hologramHeight = config.getDouble("miner.hologram.height", 2.08D);
        this.hologramLineGap = config.getDouble("miner.hologram.line-gap", 0.27D);

        List<String> rawLines = config.getStringList("miner.hologram.lines");
        if (rawLines == null || rawLines.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            defaults.add("&#F8BEFB&lМИНЁР");
            defaults.add("&7Нажми, чтобы испытать удачу");
            this.hologramLines = Collections.unmodifiableList(defaults);
        } else {
            this.hologramLines = Collections.unmodifiableList(new ArrayList<>(rawLines));
        }

        List<CoinTier> parsedTiers = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("miner.field.coin-tiers")) {
            Object rawMat = entry.get("icon");
            Material mat = rawMat == null ? null : Material.matchMaterial(String.valueOf(rawMat));
            if (mat == null || !mat.isItem()) {
                mat = Material.GOLD_INGOT;
            }
            String name = entry.containsKey("name") ? String.valueOf(entry.get("name")) : "&#F8BEFB&lМонеты";
            double mult = entry.get("multiplier") instanceof Number
                    ? ((Number) entry.get("multiplier")).doubleValue()
                    : 0.80D;
            int count = entry.get("count") instanceof Number
                    ? ((Number) entry.get("count")).intValue()
                    : 0;
            if (count > 0) {
                parsedTiers.add(new CoinTier(mat, name, mult, count));
            }
        }
        if (parsedTiers.isEmpty()) {
            parsedTiers.add(new CoinTier(Material.SUNFLOWER, "&#FFE066&lГорсть монет", 0.80D, 6));
            parsedTiers.add(new CoinTier(Material.GOLD_INGOT, "&#FFB84D&lЗолотой слиток", 1.15D, 5));
            parsedTiers.add(new CoinTier(Material.EMERALD, "&#55FF99&lИзумруд удачи", 1.70D, 3));
            parsedTiers.add(new CoinTier(Material.DIAMOND, "&#66E0FF&lАлмазный куш", 2.60D, 2));
        }

        int totalCoins = 0;
        for (CoinTier tier : parsedTiers) {
            totalCoins += tier.getCount();
        }
        int requestedMines = Math.max(8, Math.min(BOARD_SIZE - 4, config.getInt("miner.field.mines", 16)));
        if (requestedMines + totalCoins > BOARD_SIZE) {
            requestedMines = Math.max(1, BOARD_SIZE - totalCoins);
        }
        this.minesCount = requestedMines;
        this.emptyCount = Math.max(0, BOARD_SIZE - this.minesCount - totalCoins);
        this.coinTiers = Collections.unmodifiableList(parsedTiers);
    }

    public String getSkinName() {
        return skinName;
    }

    public String getSkinValue() {
        return skinValue;
    }

    public String getSkinSignature() {
        return skinSignature;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public int getThrowIntervalTicks() {
        return throwIntervalTicks;
    }

    public float getTurnAngle() {
        return turnAngle;
    }

    public int getGroundLifetimeTicks() {
        return groundLifetimeTicks;
    }

    public double getHologramHeight() {
        return hologramHeight;
    }

    public double getHologramLineGap() {
        return hologramLineGap;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public int getMinesCount() {
        return minesCount;
    }

    public int getEmptyCount() {
        return emptyCount;
    }

    public List<CoinTier> getCoinTiers() {
        return coinTiers;
    }
}
