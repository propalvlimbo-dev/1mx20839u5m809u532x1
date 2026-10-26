package ru.rooyzee.elytrixcemetery.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.rooyzee.elytrixcemetery.Main;

/** Replaces old Dungeons/Cemetery shard variants in players' inventories with the canonical item. */
public final class GhostShardMigrationListener implements Listener {

    private final Main plugin;

    public GhostShardMigrationListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> migrate(player));
    }

    public void migrate(Player player) {
        if (player == null || plugin.getLootStorageManager() == null) return;
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            ItemStack normalized = plugin.getLootStorageManager().normalizeGhostShard(item);
            if (normalized != item) inventory.setItem(slot, normalized);
        }

        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack normalized = plugin.getLootStorageManager().normalizeGhostShard(armor[i]);
            if (normalized != armor[i]) {
                armor[i] = normalized;
                armorChanged = true;
            }
        }
        if (armorChanged) inventory.setArmorContents(armor);

        ItemStack offhand = inventory.getItemInOffHand();
        ItemStack normalizedOffhand = plugin.getLootStorageManager().normalizeGhostShard(offhand);
        if (normalizedOffhand != offhand) inventory.setItemInOffHand(normalizedOffhand);
    }
}
