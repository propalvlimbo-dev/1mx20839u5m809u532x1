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
import ru.elytrix.dungeons.model.LootPoolType;

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
        if (!(top.getHolder() instanceof DungeonGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        switch (holder.getScreen()) {
            case PLAYER_OVERVIEW -> {
                if (raw == 49) {
                    player.closeInventory();
                    return;
                }
                if (raw == 48 && player.hasPermission("elytrixdungeons.admin")) {
                    plugin.getMenuService().openAdminBoard(player);
                    return;
                }
                if (clicked != null && clicked.hasItemMeta() && plugin.getMenuService().canTeleportFromPlayerMenu(player)) {
                    String id = clicked.getItemMeta().getPersistentDataContainer()
                            .get(plugin.getGuiTargetPdc(), PersistentDataType.STRING);
                    if (id != null) {
                        DungeonInstance d = plugin.getDungeonManager().getDungeon(id);
                        if (d != null && d.teleportLocation() != null) {
                            player.closeInventory();
                            player.teleport(d.teleportLocation());
                            player.playSound(d.teleportLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.15f);
                        }
                    }
                }
            }
            case ADMIN_BOARD -> handleAdminBoard(player, raw, clicked, event.getClick());
            case LOOT_POOL_SELECT -> {
                if (raw == 40) {
                    plugin.getMenuService().openAdminBoard(player);
                    return;
                }
                if (clicked != null && clicked.hasItemMeta()) {
                    String poolName = clicked.getItemMeta().getPersistentDataContainer()
                            .get(plugin.getGuiTargetPdc(), PersistentDataType.STRING);
                    if (poolName != null) {
                        plugin.getMenuService().openLootPoolEditor(player, LootPoolType.valueOf(poolName));
                    }
                }
            }
            case LOOT_POOL_EDITOR -> handleLootEditor(player, holder.getPoolType(), raw, clicked, event.getClick());
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
                                player.sendMessage(plugin.prefix() + "Создан данж §f" + created.getId() + "§7.");
                                player.playSound(player.getLocation(), Sound.BLOCK_VAULT_ACTIVATE, 1.0f, 1.1f);
                            } else if (plugin.getDungeonManager().countByCategory(tpl.getCategory())
                                    >= ru.elytrix.dungeons.service.DungeonManager.MAX_DUNGEONS_PER_CATEGORY) {
                                player.sendMessage(plugin.prefix() + "§cДля типа «" + tpl.getCategory().getTitle() + "» максимум — 7 данжей.");
                            } else {
                                player.sendMessage(plugin.prefix() + "§cНе удалось создать данж здесь: место занято, защищено или идёт поиск мест.");
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleAdminBoard(Player admin, int raw, ItemStack clicked, ClickType click) {
        if (raw == 49) {
            admin.closeInventory();
            return;
        }
        if (raw == 45) {
            plugin.getMenuService().openLootPoolSelector(admin);
            return;
        }
        if (raw == 46) {
            plugin.getMenuService().openTemplateSpawner(admin);
            return;
        }
        if (raw == 47) {
            admin.getInventory().addItem(plugin.getItemsHook().createGhostShard(16));
            admin.playSound(admin.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
            admin.sendMessage(plugin.prefix() + "Выдано §f16× Призрачный осколок§7.");
            return;
        }
        if (raw == 51) {
            boolean cur = plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);
            plugin.getConfig().set("protection.allow-block-modify-in-zone", !cur);
            plugin.saveConfig();
            plugin.getDungeonManager().syncAllWorldGuardRegions();
            admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            plugin.getMenuService().openAdminBoard(admin);
            return;
        }
        if (raw == 52) {
            if (plugin.getDungeonManager().isHiddenFromWorld()) {
                plugin.getDungeonManager().showAllDungeonsNow();
                admin.sendMessage(plugin.prefix() + "Возвращаем все 14 данжей на их сохранённые места!");
            } else {
                plugin.getDungeonManager().hideAllDungeonsNow();
                admin.sendMessage(plugin.prefix() + "Все данжи скрыты с карты, природный рельеф восстановлен!");
            }
            admin.playSound(admin.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.3f);
            plugin.getMenuService().openAdminBoard(admin);
            return;
        }
        if (raw == 53) {
            admin.closeInventory();
            if (click == ClickType.SHIFT_RIGHT) {
                plugin.getDungeonManager().rebuildAllFromSchematics();
                admin.sendMessage(plugin.prefix() + "Старые данжи очищены, запущен поиск 7 Наземных + 7 Подземных идеальных мест!");
            } else {
                for (DungeonInstance d : plugin.getDungeonManager().getAllDungeons()) {
                    plugin.getDungeonManager().regenerateDungeon(d, false);
                }
                admin.sendMessage(plugin.msg("broadcast-all-reset"));
            }
            return;
        }

        if (clicked == null || !clicked.hasItemMeta()) return;
        String dungeonId = clicked.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiTargetPdc(), PersistentDataType.STRING);
        if (dungeonId == null) return;
        DungeonInstance d = plugin.getDungeonManager().getDungeon(dungeonId);
        if (d == null) return;

        if (click == ClickType.SHIFT_RIGHT) {
            admin.closeInventory();
            plugin.getDungeonManager().regenerateDungeon(d, true);
            admin.sendMessage(plugin.prefix() + "Данж §f" + d.getId() + " §7перенесён на новые идеальные координаты!");
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

    private void handleLootEditor(Player admin, LootPoolType poolType, int raw, ItemStack clicked, ClickType click) {
        if (raw == 48) {
            plugin.getMenuService().openLootPoolSelector(admin);
            return;
        }
        if (raw == 49) {
            admin.closeInventory();
            return;
        }
        List<DungeonLootEntry> pool = plugin.getLootRegistry().getPool(poolType);

        if (raw >= 54) {
            if (clicked != null && !clicked.getType().isAir()) {
                ItemStack copy = clicked.clone();
                int amt = copy.getAmount();
                copy.setAmount(1);
                pool.add(new DungeonLootEntry(
                        "custom_" + (System.currentTimeMillis() % 100000),
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
                plugin.getMenuService().openLootPoolEditor(admin, poolType);
            }
            return;
        }

        if (raw < 0 || raw >= pool.size()) return;
        DungeonLootEntry entry = pool.get(raw);
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            pool.remove(raw);
        } else if (click == ClickType.SHIFT_LEFT) {
            entry.setGuaranteed(!entry.isGuaranteed());
        } else if (click == ClickType.MIDDLE) {
            entry.setMaxAmount(entry.getMaxAmount() >= 64 ? 1 : entry.getMaxAmount() + 1);
        } else if (click.isLeftClick()) {
            entry.setChancePercent(entry.getChancePercent() + 5.0);
        } else if (click.isRightClick()) {
            entry.setChancePercent(entry.getChancePercent() - 5.0);
        }
        plugin.getLootRegistry().save();
        admin.playSound(admin.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        plugin.getMenuService().openLootPoolEditor(admin, poolType);
    }
}
