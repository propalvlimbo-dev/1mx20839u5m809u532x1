package ru.rooyzee.nospears;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Полностью убирает копья (1.21.11+) с сервера:
 * нельзя скрафтить, нельзя получить из сундуков/дропа, у мобов их нет,
 * а найденные в инвентарях удаляются.
 */
public final class Main extends JavaPlugin implements Listener {

    private BukkitTask inventorySweepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        org.bukkit.command.PluginCommand command = getCommand("nospears");
        if (command != null) {
            command.setExecutor(this);
        }
        getLogger().info("NoSpears включён: копья убраны");

        if (getConfig().getBoolean("clean-on-start", false)) {
            int removed = 0;
            for (Player player : getServer().getOnlinePlayers()) {
                removed += clean(player);
            }
            if (removed > 0) {
                getLogger().info("Удалено копий у игроков при запуске: " + removed);
            }
        }
        scheduleInventorySweep();
    }

    private void scheduleInventorySweep() {
        if (inventorySweepTask != null) {
            inventorySweepTask.cancel();
            inventorySweepTask = null;
        }
        long interval = getConfig().getLong("inventory-sweep-ticks", 20L);
        if (interval <= 0L) return;
        interval = Math.max(1L, interval);
        inventorySweepTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                clean(player);
            }
        }, interval, interval);
    }

    // ── крафт ────────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onCraft(PrepareItemCraftEvent event) {
        if (!getConfig().getBoolean("block-crafting", true)) return;
        try {
            CraftingInventory inv = event.getInventory();
            if (shouldRemove(inv.getResult())) {
                inv.setResult(null);
            }
        } catch (Throwable ignored) {
        }
    }

    // ── сундуки и лут ────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (!getConfig().getBoolean("block-loot", true)) return;
        try {
            List<ItemStack> items = event.getLoot();
            if (items == null || items.isEmpty()) return;
            List<ItemStack> kept = new ArrayList<>(items.size());
            boolean changed = false;
            for (ItemStack item : items) {
                if (shouldRemove(item)) {
                    changed = true;
                } else {
                    kept.add(item);
                }
            }
            if (changed) {
                event.setLoot(kept);
            }
        } catch (Throwable ignored) {
        }
    }

    // ── предметы на земле ────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!getConfig().getBoolean("block-item-drop", true)) return;
        try {
            Item item = event.getEntity();
            if (item != null && shouldRemove(item.getItemStack())) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    // ── мобы со копьём в руках ───────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!getConfig().getBoolean("clean-mobs", true)) return;
        try {
            Entity entity = event.getEntity();
            if (!(entity instanceof LivingEntity)) return;
            EntityEquipment eq = ((LivingEntity) entity).getEquipment();
            if (eq == null) return;
            if (isSpear(eq.getItemInMainHand())) eq.setItemInMainHand(null);
            if (isSpear(eq.getItemInOffHand())) eq.setItemInOffHand(null);
        } catch (Throwable ignored) {
        }
    }

    // ── игроки ───────────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("clean-on-join", true)) return;
        try {
            int removed = clean(event.getPlayer());
            if (removed > 0 && getConfig().getBoolean("notify-player", true)) {
                event.getPlayer().sendMessage("§cКопья убраны с сервера — предмет удалён из инвентаря.");
            }
        } catch (Throwable ignored) {
        }
    }

    /** Убирает копья из инвентаря игрока, возвращает сколько удалено. */
    private int clean(Player player) {
        int removed = 0;
        try {
            PlayerInventory inv = player.getInventory();
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length; i++) {
                if (shouldRemove(contents[i])) {
                    inv.setItem(i, null);
                    removed++;
                }
            }
            EntityEquipment eq = player.getEquipment();
            if (eq != null) {
                if (shouldRemove(eq.getItemInMainHand())) {
                    eq.setItemInMainHand(null);
                    removed++;
                }
                if (shouldRemove(eq.getItemInOffHand())) {
                    eq.setItemInOffHand(null);
                    removed++;
                }
            }

            Inventory enderChest = player.getEnderChest();
            for (int i = 0; i < enderChest.getSize(); i++) {
                if (shouldRemove(enderChest.getItem(i))) {
                    enderChest.setItem(i, null);
                    removed++;
                }
            }

            if (shouldRemove(player.getItemOnCursor())) {
                player.setItemOnCursor(null);
                removed++;
            }
        } catch (Throwable ignored) {
        }
        return removed;
    }

    // ── команда ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nospears.admin")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            scheduleInventorySweep();
            sender.sendMessage("§aNoSpears: конфиг перезагружен.");
            return true;
        }

        int removed = 0;
        Collection<? extends Player> online = getServer().getOnlinePlayers();
        for (Player player : online) {
            removed += clean(player);
        }
        sender.sendMessage("§aNoSpears: удалено копий — " + removed + " (игроков: " + online.size() + ").");
        return true;
    }

    // ── утилиты ──────────────────────────────────────────────────────────────

    /**
     * Убирать ли предмет: копьё или (если включено) книгу с чарами Lunge,
     * которые появились вместе с копьями в 1.21.11.
     */
    private boolean shouldRemove(ItemStack item) {
        if (isSpear(item)) return true;
        return getConfig().getBoolean("block-lunge", true) && hasLunge(item);
    }

    /** Есть ли на предмете зачарование Lunge (1.21.11+, на 1.21.4 не отображается). */
    private static boolean hasLunge(ItemStack item) {
        if (item == null) return false;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;

            // Enchanted books store their enchantments separately from ordinary item meta.
            if (meta instanceof EnchantmentStorageMeta) {
                EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
                for (Enchantment enchantment : storageMeta.getStoredEnchants().keySet()) {
                    if (isLunge(enchantment)) return true;
                }
            }

            for (Enchantment enchantment : meta.getEnchants().keySet()) {
                if (isLunge(enchantment)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isLunge(Enchantment enchantment) {
        return enchantment != null
                && enchantment.getKey() != null
                && "lunge".equalsIgnoreCase(enchantment.getKey().getKey());
    }

    /** Копьё = любой материал, имя которого заканчивается на _SPEAR. */
    private static boolean isSpear(ItemStack item) {
        if (item == null) return false;
        try {
            Material type = item.getType();
            return type != null && isSpear(type);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSpear(Material type) {
        if (type == null) return false;
        try {
            String name = type.name();
            return name.equals("SPEAR") || name.endsWith("_SPEAR");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
