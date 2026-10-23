package ru.rooyzee.elytrixcasino.automate;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class AutomateSettings {

    private final long bet;
    private final int spinTicks;
    private final int reelStopDelay;
    private final double maxMultiplier;
    private final Material bottomBlock;
    private final Material topBlock;
    private final List<SlotSymbol> symbols;

    private AutomateSettings(FileConfiguration config, Logger logger) {
        this.bet = Math.max(1L, config.getLong("automate.bet", 1000L));
        this.spinTicks = Math.max(28, Math.min(240, config.getInt("automate.spin-ticks", 48)));
        this.reelStopDelay = Math.max(3, Math.min(30, config.getInt("automate.reel-stop-delay", 7)));
        this.maxMultiplier = Math.max(1.0D, config.getDouble("automate.max-multiplier", 15.0D));

        Material bottom = Material.matchMaterial(config.getString("automate.bottom-block", "PINK_CONCRETE"));
        this.bottomBlock = bottom != null && bottom.isBlock() ? bottom : Material.PINK_CONCRETE;
        Material top = Material.matchMaterial(config.getString("automate.top-block", "PINK_STAINED_GLASS"));
        this.topBlock = top != null && top.isBlock() ? top : Material.PINK_STAINED_GLASS;

        List<SlotSymbol> parsed = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("automate.symbols")) {
            Object idRaw = raw.get("id");
            Object weightRaw = raw.get("weight");
            if (idRaw == null || !(weightRaw instanceof Number)) {
                continue;
            }
            Material icon = Material.ROTTEN_FLESH;
            Object materialRaw = raw.get("material");
            if (materialRaw != null) {
                Material matched = Material.matchMaterial(String.valueOf(materialRaw));
                if (matched != null && matched.isItem()) {
                    icon = matched;
                } else {
                    logger.warning("Неизвестный material автомата: " + materialRaw);
                }
            }
            String name = raw.get("name") == null ? "&f" + idRaw : String.valueOf(raw.get("name"));
            double pay3 = number(raw.get("pay3"), 0.0D);
            double pay4 = number(raw.get("pay4"), 0.0D);
            double pay5 = number(raw.get("pay5"), 0.0D);
            parsed.add(new SlotSymbol(String.valueOf(idRaw), icon, ((Number) weightRaw).intValue(), name, pay3, pay4, pay5));
        }
        if (parsed.isEmpty()) {
            parsed.add(new SlotSymbol("flesh", Material.ROTTEN_FLESH, 34, "&8Гнилая плоть", 1.2, 1.2, 1.2));
            parsed.add(new SlotSymbol("potato", Material.POISONOUS_POTATO, 16, "&aЯд. картофель", 1.5, 1.5, 1.5));
            parsed.add(new SlotSymbol("eye", Material.SPIDER_EYE, 13, "&cПаучий глаз", 2.0, 2.0, 2.0));
            parsed.add(new SlotSymbol("bread", Material.BREAD, 10, "&6Хлеб", 2.5, 2.5, 2.5));
            parsed.add(new SlotSymbol("beef", Material.COOKED_BEEF, 8, "&cСтейк", 3.5, 3.5, 3.5));
            parsed.add(new SlotSymbol("carrot", Material.GOLDEN_CARROT, 6, "&6Зол. морковь", 4.5, 4.5, 4.5));
            parsed.add(new SlotSymbol("apple", Material.APPLE, 5, "&cЯблоко", 6.0, 6.0, 6.0));
            parsed.add(new SlotSymbol("gapple", Material.GOLDEN_APPLE, 4, "&eЗол. яблоко", 8.0, 8.0, 8.0));
            parsed.add(new SlotSymbol("pie", Material.PUMPKIN_PIE, 3, "&6Пирог", 10.0, 10.0, 10.0));
            parsed.add(new SlotSymbol("cake", Material.CAKE, 1, "&#F8BEFBТорт", 15.0, 15.0, 15.0));
        }
        this.symbols = Collections.unmodifiableList(parsed);
    }

    public static AutomateSettings load(FileConfiguration config, Logger logger) {
        return new AutomateSettings(config, logger);
    }

    private static double number(Object raw, double def) {
        return raw instanceof Number ? ((Number) raw).doubleValue() : def;
    }

    public long getBet() {
        return bet;
    }

    public int getSpinTicks() {
        return spinTicks;
    }

    public int getReelStopDelay() {
        return reelStopDelay;
    }

    public double getMaxMultiplier() {
        return maxMultiplier;
    }

    public Material getBottomBlock() {
        return bottomBlock;
    }

    public Material getTopBlock() {
        return topBlock;
    }

    public List<SlotSymbol> getSymbols() {
        return symbols;
    }

    public int getTotalWeight() {
        int total = 0;
        for (SlotSymbol symbol : symbols) {
            total += symbol.getWeight();
        }
        return total;
    }
}
