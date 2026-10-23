package ru.elytrix.dungeons.listener;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;

public final class DungeonListener implements Listener {

    private final ElytrixDungeonsPlugin plugin;

    public DungeonListener(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Player player = event.getPlayer();

        // Admin builder wand handling
        ItemStack inHand = event.getItem();
        if (inHand != null && inHand.hasItemMeta()
                && inHand.getItemMeta().getPersistentDataContainer().has(plugin.getWandPdc(), PersistentDataType.BYTE)
                && player.hasPermission("elytrixdungeons.admin")) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            BlockPosKey pt = BlockPosKey.of(clicked);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.getCommandHandler().setPos1(player, pt);
                player.sendMessage(plugin.prefix() + "Точка 1 установлена: §f" + pt.x() + ", " + pt.y() + ", " + pt.z());
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                plugin.getCommandHandler().setPos2(player, pt);
                player.sendMessage(plugin.prefix() + "Точка 2 установлена: §f" + pt.x() + ", " + pt.y() + ", " + pt.z());
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        BlockPosKey pos = BlockPosKey.of(clicked);
        DungeonInstance dungeon = plugin.getDungeonManager().getBySpecialBlock(pos);
        if (dungeon == null) {
            return;
        }

        // Prevent vanilla VAULT / RESPAWN_ANCHOR explosion or key consumption on Altar and Seal blocks
        if (pos.equals(dungeon.getAltarPos())) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            plugin.getTrialController().startTrial(player, dungeon);
            return;
        }

        if (dungeon.getSealPositions().contains(pos)) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            plugin.getTrialController().tryActivateSeal(player, dungeon, pos);
            return;
        }

        if (dungeon.getRewardChests().contains(pos) && dungeon.getStatus() != DungeonStatus.CLEARED) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                player.sendMessage(plugin.msg("reward-chest-locked"));
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.9f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        BlockPosKey pos = BlockPosKey.of(event.getBlock());
        DungeonInstance dungeon = plugin.getDungeonManager().getBySpecialBlock(pos);
        if (dungeon != null && dungeon.isProtectedMechanismBlock(pos)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.msg("core-protected"));
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
            BlockPosKey pos = BlockPosKey.of(b);
            DungeonInstance d = plugin.getDungeonManager().getBySpecialBlock(pos);
            if (d != null && d.isProtectedMechanismBlock(pos)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            BlockPosKey pos = BlockPosKey.of(b);
            DungeonInstance d = plugin.getDungeonManager().getBySpecialBlock(pos);
            if (d != null && d.isProtectedMechanismBlock(pos)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        InventoryHolder holder = event.getSource().getHolder();
        if (holder instanceof org.bukkit.block.BlockState bs) {
            BlockPosKey pos = new BlockPosKey(bs.getWorld().getName(), bs.getX(), bs.getY(), bs.getZ());
            DungeonInstance d = plugin.getDungeonManager().getBySpecialBlock(pos);
            if (d != null && d.getRewardChests().contains(pos) && d.getStatus() != DungeonStatus.CLEARED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaimCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("protection.prevent-claims", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("elytrixdungeons.admin")) {
            return;
        }
        String clean = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("/")) clean = clean.substring(1);

        boolean matched = false;
        for (String prefix : plugin.getConfig().getStringList("protection.blocked-claim-commands")) {
            String p = prefix.toLowerCase(Locale.ROOT);
            if (clean.equals(p) || clean.startsWith(p + " ")) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return;
        }

        Location loc = player.getLocation();
        if (plugin.getDungeonManager().findContainingClaimZone(loc) != null) {
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
    public void onTrialMobDeath(EntityDeathEvent event) {
        if (plugin.getTrialController().isTrialMob(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(12);
            plugin.getTrialController().handleMobDeath(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrialMobCombust(EntityCombustEvent event) {
        if (plugin.getTrialController().isTrialMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getDungeonManager().handleChunkLoad(event.getChunk());
    }

    private void scrubProtectedBlocks(List<Block> blocks) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            BlockPosKey pos = BlockPosKey.of(b);
            DungeonInstance d = plugin.getDungeonManager().getBySpecialBlock(pos);
            if (d != null && d.isProtectedMechanismBlock(pos)) {
                it.remove();
            } else if (b.getType() == Material.VAULT) {
                it.remove();
            }
        }
    }
}
