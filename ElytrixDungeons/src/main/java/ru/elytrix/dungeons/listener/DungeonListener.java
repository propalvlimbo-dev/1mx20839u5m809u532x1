package ru.elytrix.dungeons.listener;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.CuboidRegion;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.model.LootPoolType;
import ru.elytrix.dungeons.util.TextUtil;

public final class DungeonListener implements Listener {

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
        DungeonInstance dungeon = plugin.getDungeonManager().getByInteractiveBlock(pos);
        if (dungeon == null) return;

        Boolean shulkerUnlocked = dungeon.getShulkerUnlocked().get(pos);
        if (shulkerUnlocked == null) return;

        // Already unlocked shulker -> let players freely open & loot until 12h reset
        if (Boolean.TRUE.equals(shulkerUnlocked)) {
            return;
        }

        // Still locked: always cancel vanilla shulker opening first (on BOTH HAND and OFF_HAND ticks!)
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // 1. SURFACE Dungeon: 3 Unique Shulkers -> 1 Unique Key ("unique") unlocks 1 Shulker
        if (dungeon.getCategory() == DungeonCategory.SURFACE) {
            String requiredKeyId = plugin.getConfig().getString("surface-dungeon.required-vault-key-id", "unique");
            EquipmentSlot keyHand = findKeyHand(player.getInventory(), requiredKeyId);
            if (keyHand == null) {
                player.sendMessage(plugin.msg("need-unique-key"));
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.9f, 1.0f);
                return;
            }
            consumeOne(player.getInventory(), keyHand);
            dungeon.getShulkerUnlocked().put(pos, Boolean.TRUE);

            World w = clicked.getWorld();
            int stacks = Math.max(2, plugin.getConfig().getInt("surface-dungeon.unique-shulker-stacks", 6));
            if (clicked.getState() instanceof InventoryHolder holder) {
                plugin.getLootRegistry().fillContainerRandomSlots(
                        holder.getInventory(),
                        LootPoolType.SURFACE_UNIQUE_SHULKER,
                        stacks
                );
                player.openInventory(holder.getInventory());
            }
            Location c = pos.centerLocation();
            w.playSound(c, Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.05f);
            w.playSound(c, Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, c.clone().add(0, 0.85, 0), 28, 0.35, 0.4, 0.35, 0.12);
            player.sendMessage(plugin.msg("unique-shulker-opened"));

            plugin.getDungeonManager().saveDungeons();
            plugin.getDungeonManager().updateDungeonHologram(dungeon);
            return;
        }

        // 2. UNDERGROUND Dungeon: Locked Boss Shulker -> spawns strong mobs; unlocks when all die
        if (dungeon.getCategory() == DungeonCategory.UNDERGROUND) {
            if (dungeon.getStatus() == DungeonStatus.CLEARED) {
                dungeon.getShulkerUnlocked().put(pos, Boolean.TRUE);
                if (clicked.getState() instanceof InventoryHolder holder) {
                    player.openInventory(holder.getInventory());
                }
                return;
            }
            plugin.getTrialController().startUndergroundFight(player, dungeon);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockPosKey pos = BlockPosKey.of(block);
        DungeonInstance dungeon = plugin.getDungeonManager().getByInteractiveBlock(pos);
        if (dungeon == null) {
            return;
        }

        // Prevent breaking dungeon Shulker Boxes directly (prevents farming shulker shells or bypassing keys/mobs)
        if (dungeon.getShulkerUnlocked().containsKey(pos)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.msg("protected-block"));
            return;
        }

        // SKY Dungeon: Mining Angelic Shards (20% loot/coins/exp, 30% explosion, disappears at 5 explosions!)
        if (dungeon.getCategory() == DungeonCategory.SKY && dungeon.getShardExplosions().containsKey(pos)) {
            event.setCancelled(true);
            handleAngelicShardMine(event.getPlayer(), block, pos, dungeon);
        }
    }

    private void handleAngelicShardMine(Player player, Block block, BlockPosKey pos, DungeonInstance dungeon) {
        World w = block.getWorld();
        Location center = pos.centerLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double rewardChance = plugin.getConfig().getDouble("sky-dungeon.reward-chance", 20.0);
        double explosionChance = plugin.getConfig().getDouble("sky-dungeon.explosion-chance", 30.0);
        int maxExplosions = Math.max(1, plugin.getConfig().getInt("sky-dungeon.max-explosions", 5));
        int currentExplosions = dungeon.getShardExplosions().getOrDefault(pos, 0);

        double roll = rng.nextDouble(100.0);

        if (roll < rewardChance) {
            int branch = rng.nextInt(3); // 0 = Loot item, 1 = Coins, 2 = Experience
            if (branch == 0) {
                ItemStack reward = plugin.getLootRegistry().rollSingleItem(LootPoolType.SKY_ANGELIC_SHARD);
                Map<Integer, ItemStack> left = player.getInventory().addItem(reward.clone());
                for (ItemStack rem : left.values()) {
                    w.dropItemNaturally(player.getLocation(), rem);
                }
                String txt = plugin.msg("shard-reward-item")
                        .replace("{item}", TextUtil.itemTitle(reward))
                        .replace("{amount}", String.valueOf(reward.getAmount()));
                player.sendMessage(txt);
                sendActionBar(player, txt);
            } else if (branch == 1) {
                int cMin = plugin.getConfig().getInt("sky-dungeon.coins-min", 300);
                int cMax = Math.max(cMin, plugin.getConfig().getInt("sky-dungeon.coins-max", 1200));
                int coins = rng.nextInt(cMin, cMax + 1);
                plugin.getItemsHook().depositCoins(player, coins,
                        plugin.getConfig().getString("sky-dungeon.economy-fallback-command", "eco give {player} {amount}"));
                String txt = plugin.msg("shard-reward-coins").replace("{amount}", String.valueOf(coins));
                player.sendMessage(txt);
                sendActionBar(player, txt);
            } else {
                int xMin = plugin.getConfig().getInt("sky-dungeon.exp-min", 45);
                int xMax = Math.max(xMin, plugin.getConfig().getInt("sky-dungeon.exp-max", 140));
                int exp = rng.nextInt(xMin, xMax + 1);
                player.giveExp(exp);
                String txt = plugin.msg("shard-reward-exp").replace("{amount}", String.valueOf(exp));
                player.sendMessage(txt);
                sendActionBar(player, txt);
            }
            w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.35f);
            w.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.25f);
            w.spawnParticle(Particle.END_ROD, center, 18, 0.3, 0.35, 0.3, 0.05);
            return;
        }

        if (roll < rewardChance + explosionChance) {
            int updated = currentExplosions + 1;
            double dmg = plugin.getConfig().getDouble("sky-dungeon.explosion-damage", 7.5);
            double kb = plugin.getConfig().getDouble("sky-dungeon.explosion-knockback", 1.05);

            w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.05f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0.0);

            for (Player near : w.getPlayers()) {
                if (near.getLocation().distanceSquared(center) <= 25.0) {
                    near.damage(dmg);
                    Vector dir = near.getLocation().toVector().subtract(center.toVector());
                    if (dir.lengthSquared() < 0.001) dir = new Vector(0, 0.5, 0);
                    dir.normalize().multiply(kb).setY(0.52);
                    near.setVelocity(dir);
                }
            }

            if (updated >= maxExplosions) {
                dungeon.getShardExplosions().remove(pos);
                plugin.getDungeonManager().unregisterSingleInteractiveBlock(pos);
                block.setType(Material.AIR, false);
                w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                String msg = plugin.msg("shard-shattered").replace("{max}", String.valueOf(maxExplosions));
                player.sendMessage(msg);
                sendActionBar(player, msg);
            } else {
                dungeon.getShardExplosions().put(pos, updated);
                String msg = plugin.msg("shard-exploded")
                        .replace("{count}", String.valueOf(updated))
                        .replace("{max}", String.valueOf(maxExplosions));
                player.sendMessage(msg);
                sendActionBar(player, msg);
            }
            plugin.getDungeonManager().saveDungeons();
            plugin.getDungeonManager().updateDungeonHologram(dungeon);
            return;
        }

        w.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1.0f, 1.15f);
        w.spawnParticle(Particle.WAX_OFF, center, 8, 0.25, 0.25, 0.25, 0.02);
        sendActionBar(player, plugin.msg("shard-empty-hit")
                .replace("{count}", String.valueOf(currentExplosions))
                .replace("{max}", String.valueOf(maxExplosions)));
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
            if (plugin.getDungeonManager().getByInteractiveBlock(BlockPosKey.of(b)) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (plugin.getDungeonManager().getByInteractiveBlock(BlockPosKey.of(b)) != null) {
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
            DungeonInstance d = plugin.getDungeonManager().getByInteractiveBlock(pos);
            if (d != null && Boolean.FALSE.equals(d.getShulkerUnlocked().get(pos))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClaimCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("protection.prevent-claims", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("elytrixdungeons.admin")) return;

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
    public void onTrialMobDeath(EntityDeathEvent event) {
        if (plugin.getTrialController().isTrialMob(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(18);
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

    private EquipmentSlot findKeyHand(PlayerInventory inv, String keyId) {
        if (plugin.getItemsHook().isVaultKey(inv.getItemInMainHand(), keyId)) {
            return EquipmentSlot.HAND;
        }
        if (plugin.getItemsHook().isVaultKey(inv.getItemInOffHand(), keyId)) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private static void consumeOne(PlayerInventory inv, EquipmentSlot slot) {
        ItemStack cur = slot == EquipmentSlot.HAND ? inv.getItemInMainHand() : inv.getItemInOffHand();
        if (cur == null) return;
        if (cur.getAmount() <= 1) {
            if (slot == EquipmentSlot.HAND) inv.setItemInMainHand(null);
            else inv.setItemInOffHand(null);
        } else {
            cur.setAmount(cur.getAmount() - 1);
        }
    }

    private static void sendActionBar(Player player, String legacyColored) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyColored));
    }

    private void scrubProtectedBlocks(List<Block> blocks) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (plugin.getDungeonManager().getByInteractiveBlock(BlockPosKey.of(b)) != null) {
                it.remove();
            }
        }
    }
}
