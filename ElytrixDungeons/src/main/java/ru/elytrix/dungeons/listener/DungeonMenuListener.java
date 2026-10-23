package ru.elytrix.dungeons.listener;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.gui.DungeonGuiHolder;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonLootEntry;
import ru.elytrix.dungeons.model.DungeonTemplate;

public final class DungeonMenuListener implements Listener {

    private final ElytrixDungeonsPlugin plugin;

    public DungeonMenuListener(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DungeonGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DungeonGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int raw = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        switch (holder.getScreen()) {
            case PLAYER_OVERVIEW -> {
                if (raw == 49) {
                    player.closeInventory();
                } else if (raw == 48 && player.hasPermission("elytrixdungeons.admin")) {
                    plugin.getMenuService().openAdminBoard(player);
                }
            }
            case ADMIN_BOARD -> handleAdminBoardClick(player, raw, clicked, event.getClick());
            case LOOT_TIER_SELECT -> {
                if (raw == 40) {
                    plugin.getMenuService().openAdminBoard(player);
                } else if (raw >= 11 && raw <= 15) {
                    plugin.getMenuService().openLootPoolEditor(player, raw - 10, true);
                } else if (raw >= 20 && raw <= 24) {
                    plugin.getMenuService().openLootPoolEditor(player, raw - 19, false);
                }
            }
            case LOOT_POOL_EDITOR -> handleLootEditorClick(player, holder, raw, clicked, event.getClick());
            case TEMPLATE_SPAWNER -> {
                if (raw == 40) {
                    plugin.getMenuService().openAdminBoard(player);
                    return;
                }
                if (clicked != null && clicked.hasItemMeta()) {
                    String tplId = clicked.getItemMeta().getPersistentDataContainer()
                            .get(plugin.getGuiTargetPdc(), PersistentDataType.STRING);
                    if (tplId != null) {
                        DungeonTemplate tpl = plugin.getDungeonManager().getTemplate(tplId);
                        if (tpl != null) {
                            player.closeInventory();
                            DungeonInstance created = plugin.getDungeonManager().manualSpawnHere(player, tpl);
                            if (created != null) {
                                player.sendMessage(plugin.prefix() + "Создан данж §f" + created.getDisplayName() + " §7(#" + created.getId() + ")");
                                player.playSound(player.getLocation(), Sound.BLOCK_VAULT_ACTIVATE, 1.0f, 1.1f);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleAdminBoardClick(Player admin, int raw, ItemStack clicked, ClickType click) {
        if (raw == 49) {
            admin.closeInventory();
            return;
        }
        if (raw == 46) {
            plugin.getMenuService().openLootTierSelector(admin);
            return;
        }
        if (raw == 47) {
            plugin.getMenuService().openTemplateSpawner(admin);
            return;
        }
        if (raw == 51) {
            boolean cur = plugin.getConfig().getBoolean("regeneration.relocate-on-reset", false);
            plugin.getConfig().set("regeneration.relocate-on-reset", !cur);
            plugin.saveConfig();
            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            plugin.getMenuService().openAdminBoard(admin);
            return;
        }
        if (raw == 52) {
            admin.closeInventory();
            for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                plugin.getDungeonManager().regenerateDungeon(d, false);
            }
            admin.sendMessage(plugin.msg("broadcast-all-reset"));
            return;
        }
        if (raw == 53) {
            plugin.getDungeonManager().ensureTargetDungeonCounts();
            admin.sendMessage(plugin.prefix() + "Запущена проверка и доспавн недостающих данжей (5 / 5 / 5).");
            admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            return;
        }
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String dungeonId = clicked.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiTargetPdc(), PersistentDataType.STRING);
        if (dungeonId == null) {
            return;
        }
        DungeonInstance d = plugin.getDungeonManager().getDungeon(dungeonId);
        if (d == null) {
            return;
        }
        if (click == ClickType.SHIFT_RIGHT) {
            admin.closeInventory();
            plugin.getDungeonManager().regenerateDungeon(d, true);
            admin.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7восстановил рельеф и переехал на новое место!");
        } else if (click.isRightClick()) {
            plugin.getDungeonManager().regenerateDungeon(d, false);
            admin.playSound(admin.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
            plugin.getMenuService().openAdminBoard(admin);
        } else {
            Location tp = d.teleportLocation();
            if (tp != null) {
                admin.closeInventory();
                admin.teleport(tp);
                admin.playSound(tp, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.15f);
            }
        }
    }

    private void handleLootEditorClick(
            Player admin,
            DungeonGuiHolder holder,
            int raw,
            ItemStack clicked,
            ClickType click
    ) {
        if (raw == 48) {
            plugin.getMenuService().openLootTierSelector(admin);
            return;
        }
        if (raw == 49) {
            admin.closeInventory();
            return;
        }
        List<DungeonLootEntry> pool = plugin.getLootRegistry().getPool(holder.getTier(), holder.isRewardPool());

        // Clicking player's own inventory adds that item straight into the pool
        if (raw >= 54) {
            if (clicked != null && !clicked.getType().isAir()) {
                ItemStack copy = clicked.clone();
                int amt = copy.getAmount();
                copy.setAmount(1);
                String newId = "custom_" + (System.currentTimeMillis() % 100000);
                pool.add(new DungeonLootEntry(
                        newId,
                        DungeonLootEntry.SourceKind.ITEM_STACK,
                        "",
                        copy,
                        40.0,
                        1,
                        Math.max(1, amt),
                        false
                ));
                plugin.getLootRegistry().save();
                admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
                plugin.getMenuService().openLootPoolEditor(admin, holder.getTier(), holder.isRewardPool());
            }
            return;
        }

        if (raw < 0 || raw >= pool.size()) {
            return;
        }
        DungeonLootEntry entry = pool.get(raw);
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            pool.remove(raw);
        } else if (click == ClickType.SHIFT_LEFT) {
            entry.setGuaranteed(!entry.isGuaranteed());
        } else if (click == ClickType.MIDDLE) {
            entry.setMaxAmount(entry.getMaxAmount() >= 32 ? 1 : entry.getMaxAmount() + 1);
        } else if (click.isLeftClick()) {
            entry.setChancePercent(entry.getChancePercent() + 5.0);
        } else if (click.isRightClick()) {
            entry.setChancePercent(entry.getChancePercent() - 5.0);
        }
        plugin.getLootRegistry().save();
        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        plugin.getMenuService().openLootPoolEditor(admin, holder.getTier(), holder.isRewardPool());
    }
}
