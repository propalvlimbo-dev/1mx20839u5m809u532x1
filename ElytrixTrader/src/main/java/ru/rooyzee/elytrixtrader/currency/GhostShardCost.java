package ru.rooyzee.elytrixtrader.currency;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixtrader.model.CostConfig;
import ru.rooyzee.elytrixtrader.model.TradeConfig;

/** Identity and validation helpers for the canonical Cemetery Ghost Shard. */
public final class GhostShardCost {

    private static final NamespacedKey CEMETERY_ITEM_ID = new NamespacedKey("elytrixcemetery", "item_id");
    private static final NamespacedKey LEGACY_DUNGEON_ITEM_ID = new NamespacedKey("elytrixdungeons", "item_id");

    private GhostShardCost() {
    }

    public static boolean isGhostTrader(String traderId) {
        return traderId != null && traderId.equalsIgnoreCase("ghost");
    }

    /** Display names/material alone are never accepted as proof of shard identity. */
    public static boolean isGhostShard(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String currentId = meta.getPersistentDataContainer()
                .get(CEMETERY_ITEM_ID, PersistentDataType.STRING);
        if ("ghost_shard".equals(currentId)) return true;
        String legacyId = meta.getPersistentDataContainer()
                .get(LEGACY_DUNGEON_ITEM_ID, PersistentDataType.STRING);
        return "ghost_shard".equals(legacyId);
    }

    /** Ghost Trader deals must have exactly one price, and it must be the custom shard. */
    public static boolean isOnlyGhostShardCost(TradeConfig trade) {
        if (trade == null || trade.costs() == null || trade.costs().size() != 1) return false;
        CostConfig cost = trade.costs().get(0);
        return cost != null && cost.type() == CostConfig.Type.ITEM && cost.exactItem()
                && isGhostShard(cost.rawItem()) && cost.amountFor(1) >= 1.0D;
    }
}
