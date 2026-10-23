package ru.elytrix.vault.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.model.LootEntry;
import ru.elytrix.vault.model.VaultBlock;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.service.VaultManager;

public class VaultListener implements Listener {

    private final ElytrixVaultPlugin plugin;
    private final Map<UUID, Long> lastFeedbackMs = new HashMap<>();

    public VaultListener(ElytrixVaultPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        VaultBlock vault = plugin.getVaultManager().getVaultByBlock(clicked);
        if (vault == null) {
            Material type = clicked.getType();
            if (type == Material.VAULT || type == Material.TRIAL_SPAWNER) {
                ItemStack inHand = event.getItem();
                if (plugin.getKeyRegistry().isAnyVaultKey(inHand)) {
                    event.setCancelled(true);
                    if (allowFeedback(player)) {
                        player.sendMessage(plugin.msg("wrong-vanilla-vault"));
                    }
                }
            }
            return;
        }

        event.setCancelled(true);

        PlayerInventory inv = player.getInventory();
        ItemStack mainItem = inv.getItemInMainHand();
        VaultKeyType mainKey = plugin.getKeyRegistry().resolveKeyItem(mainItem);

        EquipmentSlot usedHand = null;
        VaultKeyType chosenKey = null;
        if (mainKey != null) {
            usedHand = EquipmentSlot.HAND;
            chosenKey = mainKey;
        } else {
            ItemStack offItem = inv.getItemInOffHand();
            VaultKeyType offKey = plugin.getKeyRegistry().resolveKeyItem(offItem);
            if (offKey != null) {
                usedHand = EquipmentSlot.OFF_HAND;
                chosenKey = offKey;
            }
        }

        if (chosenKey == null) {
            if (action == Action.LEFT_CLICK_BLOCK && player.hasPermission("elytrixvault.admin") && player.isSneaking()) {
                if (allowFeedback(player)) {
                    player.sendMessage(plugin.msg("cannot-break"));
                }
                return;
            }
            pushPlayerBack(player, vault);
            return;
        }

        if (event.getHand() != null && event.getHand() != usedHand) {
            return;
        }

        VaultManager.OpeningSession running = plugin.getVaultManager().getActiveSession(vault.getPos());
        if (running != null) {
            if (allowFeedback(player)) {
                player.sendMessage(plugin.msg("vault-busy").replace("{player}", running.getOpenerName()));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.9f);
            }
            return;
        }

        if (chosenKey.getEntries().isEmpty()) {
            if (allowFeedback(player)) {
                player.sendMessage(plugin.msg("empty-pool"));
            }
            return;
        }

        List<LootEntry> rolled = chosenKey.rollRewards(plugin.getKeyRegistry().getRarityWeights());
        if (rolled.isEmpty()) {
            return;
        }

        if (!consumeOneItem(inv, usedHand)) {
            return;
        }

        if (!plugin.getVaultManager().startOpening(player, vault, chosenKey, rolled)) {
            plugin.getKeyRegistry().giveOrDropProtected(player, chosenKey.createKeyItem(plugin.getKeyRegistry().getKeyPdc(), 1));
        }
    }

    private boolean consumeOneItem(PlayerInventory inv, EquipmentSlot slot) {
        ItemStack stack = slot == EquipmentSlot.OFF_HAND ? inv.getItemInOffHand() : inv.getItemInMainHand();
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1) {
            return false;
        }
        if (stack.getAmount() == 1) {
            if (slot == EquipmentSlot.OFF_HAND) {
                inv.setItemInOffHand(null);
            } else {
                inv.setItemInMainHand(null);
            }
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
        return true;
    }

    private void pushPlayerBack(Player player, VaultBlock vault) {
        if (!allowFeedback(player)) {
            return;
        }
        player.sendMessage(plugin.msg("need-key"));
        player.playSound(player.getLocation(), Sound.BLOCK_VAULT_REJECT_REWARDED_PLAYER, 0.85f, 1.0f);

        if (!plugin.getConfig().getBoolean("settings.pushback.enabled", true)) {
            return;
        }
        Location center = vault.getCenterLocation();
        if (center == null) {
            return;
        }
        Vector dir = player.getLocation().toVector().subtract(center.toVector()).setY(0.0);
        if (dir.lengthSquared() < 1e-4) {
            dir = player.getLocation().getDirection().multiply(-1.0).setY(0.0);
            if (dir.lengthSquared() < 1e-4) {
                dir = new Vector(0.0, 0.0, 1.0);
            }
        }
        dir.normalize();
        double horiz = plugin.getConfig().getDouble("settings.pushback.horizontal", 0.55);
        double vert = plugin.getConfig().getDouble("settings.pushback.vertical", 0.26);
        player.setVelocity(dir.multiply(horiz).setY(vert));
    }

    private boolean allowFeedback(Player player) {
        long now = System.currentTimeMillis();
        Long prev = lastFeedbackMs.get(player.getUniqueId());
        if (prev != null && now - prev < 350L) {
            return false;
        }
        lastFeedbackMs.put(player.getUniqueId(), now);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onReceiveGameEvent(BlockReceiveGameEvent event) {
        Block block = event.getBlock();
        if (plugin.getVaultManager().isVaultBlock(block)) {
            event.setCancelled(true);
            plugin.getVaultManager().applySilentShrieker(block);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onGenericGameEvent(GenericGameEvent event) {
        Location loc = event.getLocation();
        if (loc == null) return;
        if (event.getEvent() == GameEvent.SHRIEK && plugin.getVaultManager().isNearAnyVault(loc, 9)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getVaultManager().isVaultBlock(loc.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (plugin.getVaultManager().isVaultBlock(block)) {
            plugin.getVaultManager().applySilentShrieker(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getVaultManager().isVaultBlock(event.getBlock())) {
            event.setCancelled(true);
            if (event.getPlayer().hasPermission("elytrixvault.admin")) {
                event.getPlayer().sendMessage(plugin.msg("cannot-break"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (plugin.getVaultManager().isVaultBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.getVaultManager().isVaultBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (plugin.getVaultManager().isVaultBlock(event.getToBlock())
                || plugin.getVaultManager().isVaultBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (plugin.getVaultManager().isVaultBlock(event.getBlockClicked())
                || plugin.getVaultManager().isVaultBlock(target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.getVaultManager().isVaultBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(plugin.getVaultManager()::isVaultBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(plugin.getVaultManager()::isVaultBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getVaultManager().isVaultBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getVaultManager().isVaultBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareInventoryResultEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            if (plugin.getKeyRegistry().isAnyVaultKey(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        Item item = event.getItem();
        if (isVisualTempEntity(item) || plugin.getKeyRegistry().isProtectedDrop(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (isVisualTempEntity(item)) {
            event.setCancelled(true);
            return;
        }
        UUID pickerId = event.getEntity() instanceof Player p ? p.getUniqueId() : null;
        if (plugin.getKeyRegistry().isProtectedDropForOther(item, pickerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity e = event.getEntity();
        if (isVisualTempEntity(e)) {
            event.setCancelled(true);
            return;
        }
        if (e instanceof Item item && plugin.getKeyRegistry().isProtectedDrop(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getVaultManager().handleChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getVaultManager().refreshAllVaultBlocksAndHolograms();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getKeyRegistry().flushPendingFor(player), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedbackMs.remove(event.getPlayer().getUniqueId());
        plugin.getVaultManager().handlePlayerQuit(event.getPlayer());
    }

    private boolean isVisualTempEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(
                plugin.getVaultManager().getTempEntityPdc(),
                PersistentDataType.BYTE
        );
    }
}
