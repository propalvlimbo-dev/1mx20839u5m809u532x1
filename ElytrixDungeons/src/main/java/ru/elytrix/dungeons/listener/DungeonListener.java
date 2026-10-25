package ru.elytrix.dungeons.listener;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.SurfaceShulkerRarity;

public final class DungeonListener implements Listener {

    private static final List<String> CLAIM_PREFIXES = List.of(
            "rg claim", "rg define", "rg d", "rg create",
            "region claim", "region define", "region create",
            "worldguard:rg claim", "worldguard:region claim",
            "claim", "ps", "cmi claim"
    );

    private final ElytrixDungeonsPlugin plugin;

    public DungeonListener(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Player player = event.getPlayer();

        ItemStack held = event.getItem();
        if (held != null && held.hasItemMeta()
                && held.getItemMeta().getPersistentDataContainer().has(plugin.getWandPdc(), PersistentDataType.BYTE)
                && player.hasPermission("elytrixdungeons.admin")) {
            if (event.getHand() != EquipmentSlot.HAND) return;
            event.setCancelled(true);
            BlockPosKey pt = BlockPosKey.of(clicked);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.getCommandHandler().setPos1(player, pt);
                player.sendMessage(plugin.prefix() + "Точка 1: §f" + pt.x() + ", " + pt.y() + ", " + pt.z());
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                plugin.getCommandHandler().setPos2(player, pt);
                player.sendMessage(plugin.prefix() + "Точка 2: §f" + pt.x() + ", " + pt.y() + ", " + pt.z());
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        BlockPosKey pos = BlockPosKey.of(clicked);
        DungeonInstance dungeon = plugin.getDungeonManager().getByProtectedContainer(pos);
        if (dungeon == null) return;

        if (dungeon.getCategory() == DungeonCategory.SURFACE
                && dungeon.getShulkerPositions().contains(pos)) {
            if (dungeon.isSurfaceShulkerLocked(pos)) {
                event.setCancelled(true);
                if (event.getHand() != EquipmentSlot.HAND) return;

                ItemStack key = player.getInventory().getItemInMainHand();
                EquipmentSlot keySlot = EquipmentSlot.HAND;
                String keyId = keyIdForItem(key);
                if (keyId == null) {
                    key = player.getInventory().getItemInOffHand();
                    keySlot = EquipmentSlot.OFF_HAND;
                    keyId = keyIdForItem(key);
                }
                SurfaceShulkerRarity lootTier = lootTierForKey(keyId);
                if (lootTier == null) {
                    player.sendMessage(plugin.prefix() + "Этот шалкер запечатан. Нужен любой ключ Хранилища.");
                    return;
                }
                if (plugin.getDungeonManager().openSurfaceShulkerWithKey(player, dungeon, pos, lootTier)) {
                    consumeOneKey(player, key, keySlot);
                }
                return;
            }
            if (dungeon.isSurfaceShulkerKeyUnlocked(pos)) return;
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                SurfaceShulkerRarity rarity = SurfaceShulkerRarity.fromName(
                        dungeon.getSurfaceShulkerRarities().get(pos));
                if (rarity == null) rarity = SurfaceShulkerRarity.COMMON;
                int progress = dungeon.getSurfaceShulkerBreakProgress().getOrDefault(pos, 0);
                player.sendActionBar("Добывайте шалкер киркой: " + progress + "/" + rarity.getRequiredBreakHits());
            }
            return;
        }

        if (dungeon.getCategory() != DungeonCategory.UNDERGROUND) return;
        Boolean shulkerUnlocked = dungeon.getShulkerUnlocked().get(pos);
        if (shulkerUnlocked == null || Boolean.TRUE.equals(shulkerUnlocked)) return;

        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (dungeon.getStatus() == DungeonStatus.CLEARED) {
            dungeon.getShulkerUnlocked().put(pos, Boolean.TRUE);
            plugin.getDungeonManager().updateShulkerHolograms(dungeon);
            if (clicked.getState() instanceof InventoryHolder holder) {
                player.openInventory(holder.getInventory());
            }
            return;
        }
        plugin.getTrialController().startUndergroundFight(player, dungeon);
    }

    private String keyIdForItem(ItemStack item) {
        String keyId = plugin.getItemsHook().getVaultKeyId(item);
        if (keyId != null || item == null) return keyId;
        if (item.getType() == Material.TRIAL_KEY) return "__trial_key";
        if (item.getType() == Material.OMINOUS_TRIAL_KEY) return "__ominous_key";
        return null;
    }

    private static SurfaceShulkerRarity lootTierForKey(String keyId) {
        if (keyId == null) return null;
        return switch (keyId.toLowerCase(Locale.ROOT)) {
            case "rusty" -> SurfaceShulkerRarity.COMMON;
            case "knowledge" -> SurfaceShulkerRarity.UNCOMMON;
            case "royal" -> SurfaceShulkerRarity.RARE;
            case "void" -> SurfaceShulkerRarity.EPIC;
            case "unique" -> SurfaceShulkerRarity.MYTHIC;
            case "__trial_key" -> SurfaceShulkerRarity.EPIC;
            case "__ominous_key" -> SurfaceShulkerRarity.LEGENDARY;
            default -> SurfaceShulkerRarity.COMMON;
        };
    }

    private static void consumeOneKey(Player player, ItemStack key, EquipmentSlot slot) {
        if (key == null || key.getAmount() <= 1) {
            if (slot == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
            return;
        }
        key.setAmount(key.getAmount() - 1);
        if (slot == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(key);
        else player.getInventory().setItemInMainHand(key);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostRightClick(PlayerInteractEntityEvent event) {
        if (plugin.getGhostController().isGhost(event.getRightClicked())) {
            // Prevents vanilla Allay amethyst duplication, item-handing immunity & leads!
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostRegen(EntityRegainHealthEvent event) {
        if (plugin.getGhostController().isGhost(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSurfaceShulkerDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        BlockPosKey pos = BlockPosKey.of(block);
        DungeonInstance dungeon = plugin.getDungeonManager().getByProtectedContainer(pos);
        if (dungeon == null || dungeon.getCategory() != DungeonCategory.SURFACE
                || !dungeon.getShulkerPositions().contains(pos)
                || !block.getType().name().endsWith("_SHULKER_BOX")) return;

        // Stop vanilla mining completely: each start-dig click advances the custom rarity counter.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (dungeon.isSurfaceShulkerLocked(pos)) {
            player.sendActionBar("Запечатанный шалкер открывается ключом");
            return;
        }
        if (dungeon.isSurfaceShulkerKeyUnlocked(pos)) {
            player.sendActionBar("Этот шалкер открывается правым кликом");
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.getType().name().endsWith("_PICKAXE")) {
            player.sendActionBar("Для шалкера нужна кирка");
            return;
        }

        SurfaceShulkerRarity rarity = SurfaceShulkerRarity.fromName(
                dungeon.getSurfaceShulkerRarities().get(pos));
        if (rarity == null) rarity = SurfaceShulkerRarity.COMMON;
        int required = rarity.getRequiredBreakHits();
        int progress = dungeon.incrementSurfaceShulkerBreakProgress(pos);
        if (progress >= required) {
            if (plugin.getDungeonManager().breakSurfaceShulkerForLoot(player, dungeon, pos)) {
                player.sendActionBar("Шалкер разрушен — добыча выпала");
            } else {
                dungeon.setSurfaceShulkerBreakProgress(pos, required);
                plugin.getDungeonManager().saveDungeons();
            }
        } else {
            plugin.getDungeonManager().updateShulkerHologram(dungeon, pos);
            player.sendActionBar("Удары киркой: " + progress + "/" + required);
            if (progress % 5 == 0) plugin.getDungeonManager().saveDungeons();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        BlockPosKey pos = BlockPosKey.of(block);

        // Always protect dungeon chests, barrels & locked shulkers from being broken
        if (plugin.getDungeonManager().getByProtectedContainer(pos) != null) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.45f, 1.6f);
            player.sendMessage(plugin.msg("protected-block"));
            return;
        }

        if (!plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true)
                && !player.hasPermission("elytrixdungeons.admin")
                && plugin.getDungeonManager().findContainingStructureOrBuffer(block.getLocation()) != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("protected-zone"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true)
                && !player.hasPermission("elytrixdungeons.admin")
                && plugin.getDungeonManager().findContainingStructureOrBuffer(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("protected-zone"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        scrubProtectedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        scrubProtectedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getDungeonManager().getByProtectedContainer(BlockPosKey.of(b)) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getDungeonManager().getByProtectedContainer(BlockPosKey.of(b)) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        InventoryHolder holder = event.getSource().getHolder();
        if (holder instanceof BlockState bs) {
            BlockPosKey pos = new BlockPosKey(bs.getWorld().getName(), bs.getX(), bs.getY(), bs.getZ());
            if (plugin.getDungeonManager().getByProtectedContainer(pos) != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        BlockPosKey pos = null;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockState state) {
            pos = new BlockPosKey(state.getWorld().getName(), state.getX(), state.getY(), state.getZ());
        } else if (event.getInventory().getLocation() != null) {
            pos = BlockPosKey.of(event.getInventory().getLocation());
        }
        if (pos != null) plugin.getDungeonManager().markContainerOpened(pos);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaimCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("elytrixdungeons.admin")) return;

        String clean = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("/")) clean = clean.substring(1);

        boolean matched = false;
        for (String prefix : CLAIM_PREFIXES) {
            if (clean.equals(prefix) || clean.startsWith(prefix + " ")) {
                matched = true;
                break;
            }
        }
        if (!matched) return;

        if (plugin.getDungeonManager().findContainingClaimZone(player.getLocation()) != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("claim-blocked"));
            return;
        }
        CuboidRegion sel = plugin.getWorldEditHook().getPlayerSelection(player);
        if (sel != null && plugin.getDungeonManager().findIntersectingClaimZone(sel) != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("claim-blocked"));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (plugin.getGhostController().isGhost(event.getEntity())) {
            plugin.getGhostController().handleGhostDeath(event);
            return;
        }
        if (plugin.getTrialController().isTrialMob(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(18);
            plugin.getTrialController().handleMobDeath(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobCombust(EntityCombustEvent event) {
        if (plugin.getTrialController().isTrialMob(event.getEntity())
                || plugin.getGhostController().isGhost(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getDungeonManager().handleChunkLoad(event.getChunk());
    }

    private void scrubProtectedBlocks(List<Block> blocks) {
        boolean allowZoneModify = plugin.getConfig().getBoolean("protection.allow-block-modify-in-zone", true);
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (plugin.getDungeonManager().getByProtectedContainer(BlockPosKey.of(b)) != null) {
                it.remove();
            } else if (!allowZoneModify && plugin.getDungeonManager().findContainingStructureOrBuffer(b.getLocation()) != null) {
                it.remove();
            }
        }
    }
}
