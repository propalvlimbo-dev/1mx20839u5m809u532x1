package ru.rooyzee.elytrixcasino.miner;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Настройки NPC Минёра и математики поля «Минёр».
 */
public final class MinerSettings {

    public static final String DEFAULT_SKIN_NAME = "ExplodingTNT";
    public static final String DEFAULT_SKIN_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc5Mjc2ODE3OTg2NCwKICAicHJvZmlsZUlkIiA6ICJjMGU5MTRmZDRlYjY0N2I3OTgwNmVlMzZkY2U5ZjYwOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJFeHBsb2RpbmdUTlQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDkwYjQ0ZmEwODg5MjJmMTQ2NzhiMjMxNTc4NTQxNDg1NGQ4MmFiNmU5M2ZlYzljNjJkYThiMDEzYWNlNzljZCIKICAgIH0sCiAgICAiQ0FQRSIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTJlOGQ5N2VjNzkxMDBlOTBhNzVkMzY5ZDFiM2JhODEyNzNjNGY4MmJjMWI3MzdlOTM0ZWVkNGE4NTRiZTFiNiIKICAgIH0KICB9Cn0=";
    public static final String DEFAULT_SKIN_SIGNATURE =
            "A0QYz+WvCKJCSi7+4jWzD1xR6McT3Mrv1gS7Halci+75hWkq4VFY9eIsQ18RGZ9h/gONG/Acri9DHgkMWETZITOy0fSy7Bz/UwGFLTsbG+5/R3fVoR2Ywt/geqLktL6Blj0ZLJqroVhZkOcoU5etxZmluE1ILqnlMGBsTdXKGyiZpUtd4muEaqy8OYN2qjbn5XbGQSrI9ulws1uERPTEDc6HjJ1/05CIkP9tdvm1UFxX71wd8Aj48r+ZFdj/MXmz4iXK5aCwbn7w+aOWc97d5K34H0+KEaPNJEsvzEqigIa5mUlDxR9GQFRxQXKOuoILRu4Zy2ev54rkEPehE+cXZbkSwR1+Z60jBSFseocYNIPmYYXfCCDMzt7tDc4cZ80nsXfEX4n5fNN+YKfql5HbMRC7KcMyMqacxKi6PCG7YLEbmfnGFuTrXkZbhHra/vjx4PLsYeWMiB8r9uWhhQaukidK+GoZo4OfzxsoZrWW04Adek0320VGe8ivCRBMr8s04opUL+GIOAX9YjG8ml59rD38/kt8qZpuRln6mdu+8BB6MZCO+5jqDt8BtutvODBI6n1qzP9lErSXC82iTdv0bOXqWNe6pHyA5AmCh/VIbM2CpewX6sizHkqWD6dpTUo/ZbLneAZax8utAbB2pfiN20DtN2dbwGYxHXOE712j5Tk=";

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
    private double hologramHeight = 0.92D;
    private double hologramLineGap = 0.25D;
    private List<String> hologramLines = Collections.emptyList();

    private int minesCount = 14;
    private int emptyCount = 11;
    private List<CoinTier> coinTiers = Collections.emptyList();

    public void load(FileConfiguration config) {
        this.skinName = config.getString("miner.skin", DEFAULT_SKIN_NAME);
        this.skinValue = config.getString("miner.skin-value", DEFAULT_SKIN_VALUE);
        this.skinSignature = config.getString("miner.skin-signature", DEFAULT_SKIN_SIGNATURE);
        this.viewDistance = Math.max(8, config.getInt("miner.view-distance", 48));
        this.throwIntervalTicks = Math.max(10, config.getInt("miner.throw-interval-ticks", 60));
        this.turnAngle = (float) Math.max(5.0D, Math.min(90.0D, config.getDouble("miner.turn-angle", 28.0D)));
        this.groundLifetimeTicks = Math.max(10, config.getInt("miner.ground-lifetime-ticks", 36));
        this.hologramHeight = config.getDouble("miner.hologram.height", 0.92D);
        this.hologramLineGap = config.getDouble("miner.hologram.line-gap", 0.25D);

        List<String> rawLines = config.getStringList("miner.hologram.lines");
        if (rawLines == null || rawLines.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            defaults.add("<gradient:#F8BEFB:#89A0FF><bold>МИНЁР</bold></gradient>");
            defaults.add("{wave}");
            defaults.add("&7● &fНажмите, чтобы &#F8BEFBиграть");
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
                    : 0.40D;
            int count = entry.get("count") instanceof Number
                    ? ((Number) entry.get("count")).intValue()
                    : 0;
            if (count > 0) {
                parsedTiers.add(new CoinTier(mat, name, mult, count));
            }
        }
        if (parsedTiers.isEmpty()) {
            parsedTiers.add(new CoinTier(Material.SUNFLOWER, "&#FFE066&lГорсть монет", 0.40D, 8));
            parsedTiers.add(new CoinTier(Material.GOLD_NUGGET, "&#FFD24D&lЗолотые самородки", 0.60D, 6));
            parsedTiers.add(new CoinTier(Material.GOLD_INGOT, "&#FFA94D&lЗолотой слиток", 0.85D, 4));
            parsedTiers.add(new CoinTier(Material.EMERALD, "&#55FF99&lИзумруд удачи", 1.15D, 2));
        }

        int totalCoins = 0;
        for (CoinTier tier : parsedTiers) {
            totalCoins += tier.getCount();
        }
        int requestedMines = Math.max(8, Math.min(BOARD_SIZE - 4, config.getInt("miner.field.mines", 14)));
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
