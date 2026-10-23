package ru.rooyzee.elytrixcasino.roulette;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Разобранный и провалидированный config.yml.
 * Читается один раз при старте/релоаде, чтобы не дёргать конфиг каждый тик.
 */
public final class RouletteSettings {

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
        this.skinValue = config.getString("roulette.npc.skin.value", "");
        this.skinSignature = config.getString("roulette.npc.skin.signature", "");

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

        String rawName = config.getString("roulette.npc.name", config.getString("roulette.npc-name", "&#F8BEFBРулетка"));
        this.npcDisplayName = rawName == null || rawName.isEmpty() ? "&#F8BEFBРулетка" : rawName;
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
            logger.warning("В конфиге нет валидных секторов рулетки! Использую значения по умолчанию.");
            parsed.add(new Sector(0.0D, 5, "&c", Material.RED_WOOL));
            parsed.add(new Sector(2.0D, 4, "&a", Material.LIME_WOOL));
            parsed.add(new Sector(3.0D, 2, "&b", Material.LIGHT_BLUE_WOOL));
            parsed.add(new Sector(5.0D, 1, "&#F8BEFB", Material.GOLD_BLOCK));
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

    public long getMinBet() {
        return minBet;
    }

    public long getMaxBet() {
        return maxBet;
    }

    public int getSpinTicks() {
        return spinTicks;
    }

    public int getSpinTurns() {
        return spinTurns;
    }

    public int getWalkTicks() {
        return walkTicks;
    }

    /**
     * Сколько тиков NPC разворачивается на месте перед тем, как пойти.
     */
    public int getTurnTicks() {
        return turnTicks;
    }

    /**
     * Скорость поворота головы (градусов за тик) — чем меньше, тем плавнее взгляд.
     */
    public double getLookTurnSpeed() {
        return lookTurnSpeed;
    }

    /**
     * Скорость разворота при ходьбе, градусов за тик.
     */
    public double getWalkTurnSpeed() {
        return walkTurnSpeed;
    }

    /**
     * На каком тике прокрутки NPC бьёт второй раз (и колесо ускоряется).
     */
    public int getSecondSwingTick() {
        return secondSwingTick;
    }

    /**
     * Скорость после первого удара, в долях от полной.
     */
    public double getFirstStageSpeed() {
        return firstStageSpeed;
    }

    /**
     * Максимальная угловая скорость колеса, рад/тик. Выше — клиент начинает
     * заметно срезать дугу, и колесо визуально «стягивается» к центру.
     */
    public double getMaxSpinSpeed() {
        return maxSpinSpeed;
    }

    public double getWheelDistance() {
        return wheelDistance;
    }

    public double getWheelHeight() {
        return wheelHeight;
    }

    public double getWheelRadius() {
        return wheelRadius;
    }

    public double getNpcSpinDistance() {
        return npcSpinDistance;
    }

    /**
     * Смещение NPC вбок относительно центра колеса во время игры
     * (отрицательное — левее, если смотреть со стороны игрока).
     */
    public double getNpcSideOffset() {
        return npcSideOffset;
    }

    public double getNpcLookSideOffset() {
        return npcLookSideOffset;
    }

    public double getNpcLookHeightOffset() {
        return npcLookHeightOffset;
    }

    /**
     * Голова маленького армор-стенда рендерится выше его позиции, смещение это компенсирует.
     */
    public double getItemYOffset() {
        return itemYOffset;
    }

    /**
     * Радиус, в котором NPC поворачивается к игроку (0 — выключено).
     */
    public double getLookRadius() {
        return lookRadius;
    }

    public int getStandCount() {
        return standCount;
    }

    public int getViewRadius() {
        return viewRadius;
    }

    public boolean isShowLabels() {
        return showLabels;
    }

    public boolean isSpinSound() {
        return spinSound;
    }

    public boolean isRadiusCompensation() {
        return radiusCompensation;
    }

    public Material getPointerItem() {
        return pointerItem;
    }

    /**
     * Поворот головы указателя в градусах (x, y, z).
     */
    public float[] getPointerPose() {
        return pointerPose;
    }

    /**
     * На сколько блоков указатель поднят над верхним краем колеса.
     */
    public double getPointerHeight() {
        return pointerHeight;
    }

    /**
     * Смещение указателя вбок: спрайт меча из-за поворота головы уезжает в сторону.
     */
    public double getPointerSideOffset() {
        return pointerSideOffset;
    }

    /**
     * С какого множителя играть эффект с самородками.
     */
    public double getJackpotMultiplier() {
        return jackpotMultiplier;
    }

    public Material getJackpotItem() {
        return jackpotItem;
    }

    public int getJackpotCount() {
        return jackpotCount;
    }

    public int getJackpotTicks() {
        return jackpotTicks;
    }

    public String getNpcName() {
        return npcName;
    }

    public String getNpcDisplayName() {
        return npcDisplayName;
    }

    public boolean isHideFromTab() {
        return hideFromTab;
    }

    public String getNpcHandItem() {
        return npcHandItem == null ? "" : npcHandItem;
    }

    public String getSkinPlayer() {
        return skinPlayer == null ? "" : skinPlayer;
    }

    public String getSkinValue() {
        return skinValue == null ? "" : skinValue;
    }

    public String getSkinSignature() {
        return skinSignature == null ? "" : skinSignature;
    }

    public List<Sector> getSectors() {
        return sectors;
    }

    public int getTotalWeight() {
        int total = 0;
        for (Sector sector : sectors) {
            total += sector.getWeight();
        }
        return total;
    }
}
