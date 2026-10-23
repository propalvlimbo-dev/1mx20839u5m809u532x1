package ru.rooyzee.elytrixcasino.roulette;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Разобранный и провалидированный config.yml для Рулетки (Крупье в классическом смокинге + голограмма dHolograms).
 */
public final class RouletteSettings {

    public static final String DEFAULT_CROUPIER_SKIN_VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTcyNjgzNTY0Mzk1OSwKICAicHJvZmlsZUlkIiA6ICI3NjczNTA4YTI4MTk0ODMwYTY0YzhjYWFjNGJhNmM2NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJKb3Jpc0hlcm9zIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE4NTIzNzMxN2JkNzc5ODZlNTgxYzRkZTM5OWFjMGFmYWU3YmUzZTgwY2RjODNlNjUxOTBhM2ViZTg3ODQ0ZTgiCiAgICB9CiAgfQp9";
    public static final String DEFAULT_CROUPIER_SKIN_SIGNATURE =
            "S/9Or0ThDcU7SCW6GSINBwLA5fSClCnGdMsA76gpoH/RVeSEtoG4pGOuCQy7cG7frN6qcsT6wtPbf49nFOiOgTSwER6+XX/uUvaZKfn2/59HuIW9M7gIeWv6cy6jaX9RvWqaQFFTbhMRmw/9hVpOIov3RKOpq5c1d1LtzqUlQk0exM/CqgEc8TrF4fKsxY9hFEmVtbu0cVSCZ1aRsVyV7M3B/n5LebspvMKo3n+L5Lt4dJvQ6AdYAmisC5QB5azw7f8UipNbOkIFX1XqFk/howUZAO/1hxPT3YUXyXlU89Cg7+gaA8SbYBZgfnCUHpmgwA0vhneTpqkLUzAha2qF+PtWZRl1AJoQHj6rpFY2ZjfgsUnEHRn6j9W3QjgiUv6KMJ3Bo6frbmliy7w5Pyh75szemkST1jO1/5oDWwmSCe4AQMck/pCChp9Dl3uZ5gxr0dbiXYt+TRKINN9PYyjZ9xvUNyfFd+l1sCVKA1Pa5JNmzR2nQm1+kYzeoBMRRzwxI0hlzapdjzPCX5sXmrF4AUg/lJ3Hz6rTK9UGA6pro3tC1lmZPrBqRFKMGkNUPrGgKMVK2jt1egnFSsMVzYJLulwjNDZzk1N941nkU+Vv3bKuRfqlcMVyYK+Xg4tm3+EbFKu7OhnexVqIkCTPPF75lm97IgNmpl5jhYgPFWjuli0=";

    private final long minBet;
    private final long maxBet;
    private final int spinTicks;
    private final int spinTurns;
    private final int walkTicks;
    private final int turnTicks;
    private final double lookTurnSpeed;
    private final double walkTurnSpeed;
    private final int secondSwingTick;
    private final double firstStageSpeed;
    private final double maxSpinSpeed;
    private final double wheelDistance;
    private final double wheelHeight;
    private final double wheelRadius;
    private final double npcSpinDistance;
    private final double npcSideOffset;
    private final double npcLookSideOffset;
    private final double npcLookHeightOffset;
    private final double itemYOffset;
    private final double lookRadius;
    private final int standCount;
    private final int viewRadius;
    private final boolean showLabels;
    private final boolean spinSound;
    private final boolean radiusCompensation;
    private final Material pointerItem;
    private final float[] pointerPose;
    private final double pointerHeight;
    private final double pointerSideOffset;
    private final double jackpotMultiplier;
    private final Material jackpotItem;
    private final int jackpotCount;
    private final int jackpotTicks;
    private final String npcName;
    private final String npcDisplayName;
    private final double hologramHeight;
    private final double hologramLineGap;
    private final List<String> hologramLines;
    private final boolean hideFromTab;
    private final String npcHandItem;
    private final String skinPlayer;
    private final String skinValue;
    private final String skinSignature;
    private final List<Sector> sectors;

    private RouletteSettings(FileConfiguration config, Logger logger) {
        this.minBet = Math.max(1L, config.getLong("roulette.min-bet", 100L));
        this.maxBet = Math.max(minBet, config.getLong("roulette.max-bet", 100000L));
        this.spinTicks = clamp(config.getInt("roulette.spin-duration-ticks", 200), 20, 2400);
        this.spinTurns = clamp(config.getInt("roulette.spin-turns", 5), 1, 50);
        this.walkTicks = clamp(config.getInt("roulette.walk-ticks", 20), 1, 200);
        this.turnTicks = clamp(config.getInt("roulette.turn-ticks", 30), 0, 80);
        this.lookTurnSpeed = clampD(config.getDouble("roulette.look-turn-speed", 6.0D), 1.0D, 90.0D);
        this.walkTurnSpeed = clampD(config.getDouble("roulette.walk-turn-speed", 16.0D), 1.0D, 90.0D);
        this.secondSwingTick = clamp(config.getInt("roulette.second-swing-tick", 14), 2, Math.max(3, spinTicks - 2));
        this.firstStageSpeed = clampD(config.getDouble("roulette.first-stage-speed", 0.45D), 0.05D, 1.0D);
        this.maxSpinSpeed = clampD(config.getDouble("roulette.max-spin-speed", 0.2D), 0.02D, 1.0D);
        this.wheelDistance = clampD(config.getDouble("roulette.wheel.distance", 3.5D), 1.5D, 10.0D);
        this.wheelHeight = clampD(config.getDouble("roulette.wheel.height", 2.0D), 0.0D, 10.0D);
        this.wheelRadius = clampD(config.getDouble("roulette.wheel.radius", 1.5D), 0.5D, 6.0D);
        this.npcSpinDistance = clampD(config.getDouble("roulette.npc-spin-distance", 1.5D), 0.5D, 8.0D);
        this.npcSideOffset = clampD(config.getDouble("roulette.npc-side-offset", -0.9D), -4.0D, 4.0D);
        this.npcLookSideOffset = clampD(config.getDouble("roulette.npc-look-side-offset", -0.9D), -4.0D, 4.0D);
        this.npcLookHeightOffset = clampD(config.getDouble("roulette.npc-look-height-offset", -0.3D), -4.0D, 4.0D);
        this.itemYOffset = clampD(config.getDouble("roulette.wheel.item-y-offset", -0.7D), -3.0D, 3.0D);
        this.lookRadius = clampD(config.getDouble("roulette.look-radius", 10.0D), 0.0D, 48.0D);
        this.viewRadius = clamp(config.getInt("roulette.view-radius", 48), 8, 128);
        this.showLabels = config.getBoolean("roulette.wheel.show-labels", true);
        this.spinSound = config.getBoolean("roulette.spin-sound", true);
        this.radiusCompensation = config.getBoolean("roulette.wheel.radius-compensation", true);
        this.hideFromTab = config.getBoolean("roulette.npc.hide-from-tab", true);
        this.npcHandItem = config.getString("roulette.npc.hand-item", "");
        this.skinPlayer = config.getString("roulette.npc.skin.player", "");

        String cfgVal = config.getString("roulette.npc.skin.value", "");
        String cfgSig = config.getString("roulette.npc.skin.signature", "");
        if ((cfgVal == null || cfgVal.isEmpty()) && (this.skinPlayer == null || this.skinPlayer.isEmpty())) {
            this.skinValue = DEFAULT_CROUPIER_SKIN_VALUE;
            this.skinSignature = DEFAULT_CROUPIER_SKIN_SIGNATURE;
        } else {
            this.skinValue = cfgVal == null ? "" : cfgVal;
            this.skinSignature = cfgSig == null ? "" : cfgSig;
        }

        this.hologramHeight = config.getDouble("roulette.npc.hologram.height", 0.92D);
        this.hologramLineGap = config.getDouble("roulette.npc.hologram.line-gap", 0.25D);
        List<String> rawHolo = config.getStringList("roulette.npc.hologram.lines");
        if (rawHolo == null || rawHolo.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            defaults.add("<gradient:#F8BEFB:#89A0FF><bold>РУЛЕТКА</bold></gradient>");
            defaults.add("{wave}");
            defaults.add("&7● &fНажмите, чтобы &#F8BEFBиграть");
            this.hologramLines = Collections.unmodifiableList(defaults);
        } else {
            this.hologramLines = Collections.unmodifiableList(new ArrayList<>(rawHolo));
        }

        Material pointer = Material.matchMaterial(config.getString("roulette.wheel.pointer.item", "GOLDEN_SWORD"));
        if (pointer == null || !pointer.isItem()) {
            logger.warning("Неизвестный предмет указателя, использую GOLDEN_SWORD");
            pointer = Material.GOLDEN_SWORD;
        }
        this.pointerItem = pointer;

        this.pointerHeight = clampD(config.getDouble("roulette.wheel.pointer.height", 1.35D), 0.0D, 5.0D);
        this.pointerSideOffset = clampD(config.getDouble("roulette.wheel.pointer.side-offset", -0.38D), -3.0D, 3.0D);

        Material jackpot = Material.matchMaterial(config.getString("roulette.jackpot.item", "GOLD_NUGGET"));
        if (jackpot == null || !jackpot.isItem()) {
            jackpot = Material.GOLD_NUGGET;
        }
        this.jackpotItem = jackpot;
        this.jackpotMultiplier = config.getDouble("roulette.jackpot.multiplier", 5.0D);
        this.jackpotCount = clamp(config.getInt("roulette.jackpot.count", 14), 0, 64);
        this.jackpotTicks = clamp(config.getInt("roulette.jackpot.ticks", 50), 5, 200);

        List<Double> pose = config.getDoubleList("roulette.wheel.pointer.pose");
        this.pointerPose = new float[]{
                pose.size() > 0 ? pose.get(0).floatValue() : 0.0F,
                pose.size() > 1 ? pose.get(1).floatValue() : 0.0F,
                pose.size() > 2 ? pose.get(2).floatValue() : 135.0F
        };

        String rawName = config.getString("roulette.npc.name", config.getString("roulette.npc-name", "ec_r_npc"));
        this.npcDisplayName = rawName == null || rawName.isEmpty() ? "ec_r_npc" : rawName;
        this.npcName = ru.rooyzee.elytrixcasino.util.Text.profileName(this.npcDisplayName);

        List<Sector> parsed = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("roulette.sectors")) {
            Object multiplierRaw = raw.get("multiplier");
            Object weightRaw = raw.get("weight");
            if (!(multiplierRaw instanceof Number) || !(weightRaw instanceof Number)) {
                logger.warning("Пропущен сектор рулетки: не заданы multiplier/weight -> " + raw);
                continue;
            }
            double multiplier = Math.max(0.0D, ((Number) multiplierRaw).doubleValue());
            int weight = Math.max(0, ((Number) weightRaw).intValue());
            if (weight == 0) {
                continue;
            }
            String color = raw.get("color") == null ? "&f" : String.valueOf(raw.get("color"));
            Material icon = Material.WHITE_WOOL;
            Object materialRaw = raw.get("material");
            if (materialRaw != null) {
                Material matched = Material.matchMaterial(String.valueOf(materialRaw));
                if (matched == null || !matched.isItem()) {
                    logger.warning("Неизвестный material '" + materialRaw + "' у сектора рулетки, использую WHITE_WOOL");
                } else {
                    icon = matched;
                }
            }
            parsed.add(new Sector(multiplier, weight, color, icon));
        }
        if (parsed.isEmpty()) {
            parsed.add(new Sector(0.0D, 71, "&c", Material.RED_WOOL));
            parsed.add(new Sector(2.0D, 19, "&a", Material.LIME_WOOL));
            parsed.add(new Sector(3.0D, 7, "&b", Material.LIGHT_BLUE_WOOL));
            parsed.add(new Sector(5.0D, 3, "&#F8BEFB", Material.GOLD_BLOCK));
        }
        this.sectors = Collections.unmodifiableList(parsed);

        int weightSum = 0;
        for (Sector sector : sectors) {
            weightSum += sector.getWeight();
        }
        int slots = clamp(config.getInt("roulette.wheel.slots", 12), 4, 32);
        this.standCount = clamp(weightSum > 0 ? weightSum : slots, 4, 32);
    }

    public static RouletteSettings load(FileConfiguration config, Logger logger) {
        return new RouletteSettings(config, logger);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampD(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public long getMinBet() { return minBet; }
    public long getMaxBet() { return maxBet; }
    public int getSpinTicks() { return spinTicks; }
    public int getSpinTurns() { return spinTurns; }
    public int getWalkTicks() { return walkTicks; }
    public int getTurnTicks() { return turnTicks; }
    public double getLookTurnSpeed() { return lookTurnSpeed; }
    public double getWalkTurnSpeed() { return walkTurnSpeed; }
    public int getSecondSwingTick() { return secondSwingTick; }
    public double getFirstStageSpeed() { return firstStageSpeed; }
    public double getMaxSpinSpeed() { return maxSpinSpeed; }
    public double getWheelDistance() { return wheelDistance; }
    public double getWheelHeight() { return wheelHeight; }
    public double getWheelRadius() { return wheelRadius; }
    public double getNpcSpinDistance() { return npcSpinDistance; }
    public double getNpcSideOffset() { return npcSideOffset; }
    public double getNpcLookSideOffset() { return npcLookSideOffset; }
    public double getNpcLookHeightOffset() { return npcLookHeightOffset; }
    public double getItemYOffset() { return itemYOffset; }
    public double getLookRadius() { return lookRadius; }
    public int getStandCount() { return standCount; }
    public int getViewRadius() { return viewRadius; }
    public boolean isShowLabels() { return showLabels; }
    public boolean isSpinSound() { return spinSound; }
    public boolean isRadiusCompensation() { return radiusCompensation; }
    public Material getPointerItem() { return pointerItem; }
    public float[] getPointerPose() { return pointerPose; }
    public double getPointerHeight() { return pointerHeight; }
    public double getPointerSideOffset() { return pointerSideOffset; }
    public double getJackpotMultiplier() { return jackpotMultiplier; }
    public Material getJackpotItem() { return jackpotItem; }
    public int getJackpotCount() { return jackpotCount; }
    public int getJackpotTicks() { return jackpotTicks; }
    public String getNpcName() { return npcName; }
    public String getNpcDisplayName() { return npcDisplayName; }
    public double getHologramHeight() { return hologramHeight; }
    public double getHologramLineGap() { return hologramLineGap; }
    public List<String> getHologramLines() { return hologramLines; }
    public boolean isHideFromTab() { return hideFromTab; }
    public String getNpcHandItem() { return npcHandItem == null ? "" : npcHandItem; }
    public String getSkinPlayer() { return skinPlayer == null ? "" : skinPlayer; }
    public String getSkinValue() { return skinValue == null ? "" : skinValue; }
    public String getSkinSignature() { return skinSignature == null ? "" : skinSignature; }
    public List<Sector> getSectors() { return sectors; }

    public int getTotalWeight() {
        int total = 0;
        for (Sector sector : sectors) {
            total += sector.getWeight();
        }
        return total;
    }
}
