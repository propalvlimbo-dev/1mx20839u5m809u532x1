package ru.rooyzee.elytrixcasino.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.automate.Automate;
import ru.rooyzee.elytrixcasino.gui.GuiGuard;
import ru.rooyzee.elytrixcasino.gui.RouletteGui;
import ru.rooyzee.elytrixcasino.miner.Miner;
import ru.rooyzee.elytrixcasino.roulette.Roulette;
import ru.rooyzee.elytrixcasino.util.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CasinoListener implements Listener {

    private static final double REACH = 5.0D;
    private static final long COOLDOWN_MS = 250L;
    private static final double RAY_STEP = 0.2D;

    private static final Set<String> DECORATION_TYPES = new HashSet<>(Arrays.asList(
            "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING", "ARMOR_STAND",
            "INTERACTION", "ITEM_DISPLAY", "BLOCK_DISPLAY", "TEXT_DISPLAY"));

    private final Main plugin;
    private final RouletteGui gui;
    private final Map<UUID, Long> lastAction = new HashMap<>();
    private final Set<UUID> debugging = new HashSet<>();

    public CasinoListener(Main plugin, RouletteGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    public void onVirtualInteract(Player player, int entityId, boolean attack) {
        Roulette roulette = plugin.getRouletteManager().findByEntityId(entityId);
        if (roulette != null) {
            if (!passCooldown(player)) {
                return;
            }
            if (attack) {
                handleAttack(player, roulette);
            } else {
                handleRightClick(player, roulette);
            }
            return;
        }

        Miner miner = plugin.getMinerManager().findByEntityId(entityId);
        if (miner != null) {
            if (!passCooldown(player)) {
                return;
            }
            if (attack) {
                handleMinerAttack(player, miner);
            } else {
                handleMinerRightClick(player, miner);
            }
            return;
        }

        if (!attack) {
            handleUnknownEntityClick(player, entityId);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        Player player = event.getPlayer();

        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (plugin.getAutomateManager().isCreating(player)) {
                event.setCancelled(true);
                if (!passCooldown(player)) {
                    return;
                }
                Automate created = plugin.getAutomateManager().completeCreate(player, event.getClickedBlock());
                if (created == null) {
                    plugin.send(player, "automate-bind-failed");
                    return;
                }
                plugin.send(player, "created",
                        "%id%", created.getId(),
                        "%type%", "Автомат");
                return;
            }
            Automate automate = plugin.getAutomateManager().findByBlock(event.getClickedBlock().getLocation());
            if (automate == null) {
                automate = findThroughFrontBlock(player, event.getClickedBlock());
            }
            debug(player, "клик по блоку " + event.getClickedBlock().getType(), automate);
            if (automate != null) {
                event.setCancelled(true);
                if (passCooldown(player)) {
                    handleAutomateClick(player, automate);
                }
                return;
            }
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Miner miner = plugin.getMinerManager().findLookingAt(player, REACH);
        if (miner != null) {
            event.setCancelled(true);
            if (passCooldown(player)) {
                handleMinerRightClick(player, miner);
            }
            return;
        }

        Roulette roulette = plugin.getRouletteManager().findLookingAt(player, REACH);
        if (roulette != null) {
            event.setCancelled(true);
            if (passCooldown(player)) {
                handleRightClick(player, roulette);
            }
        }
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        if (!plugin.getRouletteManager().hasRemoveMode()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getRouletteManager().isRemoveMode(player)) {
            return;
        }
        Miner miner = plugin.getMinerManager().findLookingAt(player, REACH);
        if (miner != null) {
            if (passCooldown(player)) {
                handleMinerAttack(player, miner);
            }
            return;
        }
        Roulette roulette = plugin.getRouletteManager().findLookingAt(player, REACH);
        if (roulette != null && passCooldown(player)) {
            handleAttack(player, roulette);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Automate automate = plugin.getAutomateManager().findByBlock(event.getBlock().getLocation());
        if (automate == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.getRouletteManager().isRemoveMode(player) && player.hasPermission("elytrixcasino.admin")) {
            String id = automate.getId();
            plugin.getAutomateManager().remove(id);
            plugin.getRouletteManager().clearRemoveMode(player);
            plugin.send(player, "removed", "%id%", id);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        filterExplosion(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getAutomateManager().onChunkLoad(event.getChunk());
        plugin.getMinerManager().onChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getAutomateManager().onChunkUnload(event.getChunk());
        plugin.getMinerManager().onChunkUnload(event.getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.blockList().iterator());
    }

    private void filterExplosion(Iterator<Block> iterator) {
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plugin.getAutomateManager().findByBlock(block.getLocation()) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getAutomateGui().applyPending(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (GuiGuard.isMarked(plugin, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            GuiGuard.strip(plugin, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (GuiGuard.isCasinoHolder(event.getPlayer().getOpenInventory().getTopInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMinerGui() != null) {
            plugin.getMinerGui().handleQuit(player);
        }
        GuiGuard.strip(plugin, player);
        plugin.getRouletteManager().clearRemoveMode(player);
        plugin.getAutomateManager().cancelCreate(player);
        debugging.remove(player.getUniqueId());
        lastAction.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Automate automate = findThroughDecoration(player, event.getRightClicked());
        debug(player, "клик по сущности " + event.getRightClicked().getType(), automate);
        if (automate == null) {
            return;
        }
        event.setCancelled(true);
        if (passCooldown(player)) {
            handleAutomateClick(player, automate);
        }
    }

    private Automate findThroughDecoration(Player player, Entity entity) {
        if (entity == null || !isDecoration(entity)) {
            return null;
        }
        if (plugin.getRouletteManager().isRemoveMode(player)) {
            return null;
        }
        Block block = entity.getLocation().getBlock();
        if (entity instanceof Hanging) {
            try {
                block = block.getRelative(((Hanging) entity).getAttachedFace());
            } catch (Throwable ignored) {
            }
        }
        Automate automate = plugin.getAutomateManager().findByBlock(block.getLocation());
        if (automate != null) {
            return automate;
        }
        return plugin.getAutomateManager().findBehindBlock(player, block.getLocation());
    }

    private boolean isDecoration(Entity entity) {
        return entity instanceof Hanging || DECORATION_TYPES.contains(entity.getType().name());
    }

    private void handleUnknownEntityClick(Player player, int entityId) {
        if (plugin.getAutomateManager().getAll().isEmpty()) {
            return;
        }
        if (plugin.getRouletteManager().isRemoveMode(player)) {
            return;
        }
        if (isRealEntity(player, entityId)) {
            return;
        }
        Automate automate = findAutomateInSight(player);
        debug(player, "клик по пакетной сущности id=" + entityId + " (её нет на сервере)", automate);
        if (automate == null) {
            return;
        }
        if (passCooldown(player)) {
            handleAutomateClick(player, automate);
        }
    }

    private boolean isRealEntity(Player player, int entityId) {
        double radius = REACH + 1.0D;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.getEntityId() == entityId) {
                return true;
            }
        }
        return false;
    }

    private Automate findAutomateInSight(Player player) {
        Location point = player.getEyeLocation();
        Vector direction = point.getDirection();
        if (direction.lengthSquared() < 1.0E-6D) {
            return null;
        }
        Vector step = direction.normalize().multiply(RAY_STEP);
        point = point.clone();
        Block last = null;
        for (int i = 0; i < (int) (REACH / RAY_STEP); i++) {
            point.add(step);
            Block block = point.getBlock();
            if (block.equals(last)) {
                continue;
            }
            last = block;
            Automate automate = plugin.getAutomateManager().findByBlock(block.getLocation());
            if (automate != null) {
                return automate;
            }
        }
        return null;
    }

    private void handleAttack(Player player, Roulette roulette) {
        if (!plugin.getRouletteManager().isRemoveMode(player)) {
            return;
        }
        if (!player.hasPermission("elytrixcasino.admin")) {
            plugin.getRouletteManager().clearRemoveMode(player);
            return;
        }
        String id = roulette.getId();
        plugin.getRouletteManager().remove(id);
        plugin.getRouletteManager().clearRemoveMode(player);
        plugin.send(player, "removed", "%id%", id);
    }

    private void handleRightClick(Player player, Roulette roulette) {
        if (plugin.getRouletteManager().isRemoveMode(player) && player.hasPermission("elytrixcasino.admin")) {
            handleAttack(player, roulette);
            return;
        }
        if (!player.hasPermission("elytrixcasino.use")) {
            plugin.send(player, "no-perm");
            return;
        }
        if (roulette.isBusy()) {
            plugin.send(player, "busy");
            return;
        }
        gui.open(player, roulette);
    }

    private void handleMinerAttack(Player player, Miner miner) {
        if (plugin.getRouletteManager().isRemoveMode(player)) {
            if (!player.hasPermission("elytrixcasino.admin")) {
                plugin.getRouletteManager().clearRemoveMode(player);
                return;
            }
            String id = miner.getId();
            plugin.getMinerManager().remove(id);
            plugin.getRouletteManager().clearRemoveMode(player);
            plugin.send(player, "removed", "%id%", id);
            return;
        }
        // По обычному клику ЛКМ по NPC Минёр тоже удобно открывать меню игры!
        handleMinerRightClick(player, miner);
    }

    private void handleMinerRightClick(Player player, Miner miner) {
        if (plugin.getRouletteManager().isRemoveMode(player) && player.hasPermission("elytrixcasino.admin")) {
            String id = miner.getId();
            plugin.getMinerManager().remove(id);
            plugin.getRouletteManager().clearRemoveMode(player);
            plugin.send(player, "removed", "%id%", id);
            return;
        }
        if (!player.hasPermission("elytrixcasino.use")) {
            plugin.send(player, "no-perm");
            return;
        }
        plugin.getMinerGui().open(player, miner);
    }

    private Automate findThroughFrontBlock(Player player, Block block) {
        if (block == null || block.getType().isAir() || isInteractable(block.getType())) {
            return null;
        }
        if (plugin.getRouletteManager().isRemoveMode(player)) {
            return null;
        }
        return plugin.getAutomateManager().findBehindBlock(player, block.getLocation());
    }

    private boolean isInteractable(Material material) {
        try {
            return material.isInteractable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void handleAutomateClick(Player player, Automate automate) {
        if (plugin.getRouletteManager().isRemoveMode(player) && player.hasPermission("elytrixcasino.admin")) {
            String id = automate.getId();
            plugin.getAutomateManager().remove(id);
            plugin.getRouletteManager().clearRemoveMode(player);
            plugin.send(player, "removed", "%id%", id);
            return;
        }
        if (!player.hasPermission("elytrixcasino.use")) {
            plugin.send(player, "no-perm");
            return;
        }
        if (automate.isBusy()) {
            plugin.send(player, "automate-busy");
            return;
        }
        plugin.getAutomateGui().open(player, automate);
    }

    public boolean toggleDebug(Player player) {
        UUID id = player.getUniqueId();
        if (debugging.remove(id)) {
            return false;
        }
        debugging.add(id);
        return true;
    }

    private boolean isDebugging(Player player) {
        return !debugging.isEmpty() && debugging.contains(player.getUniqueId());
    }

    private void debug(Player player, String source, Automate automate) {
        if (!isDebugging(player)) {
            return;
        }
        player.sendMessage(Text.color("&8[EC] &7" + source + " &8-> &7автомат: "
                + (automate == null ? "&cне найден" : "&a" + automate.getId())));
    }

    private boolean passCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastAction.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        lastAction.put(player.getUniqueId(), now);
        return true;
    }
}
