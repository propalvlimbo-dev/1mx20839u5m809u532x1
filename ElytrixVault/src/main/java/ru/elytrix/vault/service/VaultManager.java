package ru.elytrix.vault.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.SculkShrieker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.elytrix.vault.ElytrixVaultPlugin;
import ru.elytrix.vault.model.BlockPosKey;
import ru.elytrix.vault.model.LootEntry;
import ru.elytrix.vault.model.Rarity;
import ru.elytrix.vault.model.VaultBlock;
import ru.elytrix.vault.model.VaultKeyType;
import ru.elytrix.vault.util.TextUtil;

public class VaultManager {

    public static final class OpeningSession {
        private final VaultBlock vault;
        private final UUID openerUuid;
        private final String openerName;
        private final VaultKeyType keyType;
        private final List<LootEntry> allRewards;
        private final Deque<LootEntry> remaining;
        private final List<Entity> tempEntities = new ArrayList<>();
        private BukkitTask task;

        OpeningSession(VaultBlock vault, Player opener, VaultKeyType keyType, List<LootEntry> rolled) {
            this.vault = vault;
            this.openerUuid = opener.getUniqueId();
            this.openerName = opener.getName();
            this.keyType = keyType;
            this.allRewards = List.copyOf(rolled);
            this.remaining = new ArrayDeque<>(rolled);
        }

        public String getOpenerName() {
            return openerName;
        }

        public UUID getOpenerUuid() {
            return openerUuid;
        }
    }

    private final ElytrixVaultPlugin plugin;
    private final NamespacedKey hologramPdc;
    private final NamespacedKey tempEntityPdc;
    private final Map<String, VaultBlock> vaultsById = new LinkedHashMap<>();
    private final Map<BlockPosKey, VaultBlock> vaultsByPos = new HashMap<>();
    private final Map<BlockPosKey, OpeningSession> activeSessions = new HashMap<>();
    private File dataFile;

    public VaultManager(ElytrixVaultPlugin plugin) {
        this.plugin = plugin;
        this.hologramPdc = new NamespacedKey(plugin, "vault_hologram");
        this.tempEntityPdc = new NamespacedKey(plugin, "vault_temp");
    }

    public NamespacedKey getTempEntityPdc() {
        return tempEntityPdc;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "vaults.yml");
        vaultsById.clear();
        vaultsByPos.clear();
        if (dataFile.exists()) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection sec = cfg.getConfigurationSection("vaults");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ConfigurationSection vs = sec.getConfigurationSection(id);
                    if (vs == null) continue;
                    String world = vs.getString("world");
                    if (world == null || world.isEmpty()) continue;
                    BlockPosKey pos = new BlockPosKey(world, vs.getInt("x"), vs.getInt("y"), vs.getInt("z"));
                    VaultBlock vb = new VaultBlock(id.toLowerCase(Locale.ROOT), pos);
                    vaultsById.put(vb.getId(), vb);
                    vaultsByPos.put(pos, vb);
                }
            }
        }
        cleanupAllTaggedEntities();
        refreshAllVaultBlocksAndHolograms();
    }

    public synchronized void save() {
        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "vaults.yml");
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (VaultBlock vb : vaultsById.values()) {
            String p = "vaults." + vb.getId() + ".";
            cfg.set(p + "world", vb.getPos().getWorldName());
            cfg.set(p + "x", vb.getPos().getX());
            cfg.set(p + "y", vb.getPos().getY());
            cfg.set(p + "z", vb.getPos().getZ());
        }
        try {
            dataFile.getParentFile().mkdirs();
            cfg.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить vaults.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (OpeningSession session : new ArrayList<>(activeSessions.values())) {
            abortAndDeliverRemaining(session);
        }
        activeSessions.clear();
        for (VaultBlock vb : vaultsById.values()) {
            removeHologram(vb);
        }
        cleanupAllTaggedEntities();
    }

    public Collection<VaultBlock> getAllVaults() {
        return Collections.unmodifiableCollection(vaultsById.values());
    }

    public VaultBlock getVaultByPos(BlockPosKey pos) {
        return pos == null ? null : vaultsByPos.get(pos);
    }

    public VaultBlock getVaultByBlock(Block block) {
        return block == null ? null : vaultsByPos.get(BlockPosKey.of(block));
    }

    public VaultBlock getVaultById(String id) {
        return id == null ? null : vaultsById.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean isVaultBlock(Block block) {
        return getVaultByBlock(block) != null;
    }

    public boolean isNearAnyVault(Location loc, int radiusBlocks) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        String w = loc.getWorld().getName();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        int r2 = radiusBlocks * radiusBlocks;
        for (VaultBlock vb : vaultsById.values()) {
            BlockPosKey p = vb.getPos();
            if (!p.getWorldName().equals(w)) continue;
            int dx = p.getX() - bx;
            int dy = p.getY() - by;
            int dz = p.getZ() - bz;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    public String nextAvailableId() {
        int n = 1;
        while (vaultsById.containsKey("vault" + n)) {
            n++;
        }
        return "vault" + n;
    }

    public VaultBlock createVault(String id, Block targetBlock) {
        String cleanId = (id == null || id.isBlank()) ? nextAvailableId() : id.trim().toLowerCase(Locale.ROOT);
        VaultBlock existing = vaultsById.remove(cleanId);
        if (existing != null) {
            vaultsByPos.remove(existing.getPos());
            removeHologram(existing);
        }

        BlockPosKey pos = BlockPosKey.of(targetBlock);
        VaultBlock vault = new VaultBlock(cleanId, pos);
        vaultsById.put(cleanId, vault);
        vaultsByPos.put(pos, vault);

        applySilentShrieker(targetBlock);
        ensureHologram(vault, buildIdleText());
        save();
        return vault;
    }

    public boolean removeVault(VaultBlock vault, boolean breakBlock) {
        if (vault == null) {
            return false;
        }
        OpeningSession active = activeSessions.remove(vault.getPos());
        if (active != null) {
            abortAndDeliverRemaining(active);
        }
        removeHologram(vault);
        vaultsById.remove(vault.getId());
        vaultsByPos.remove(vault.getPos());
        if (breakBlock) {
            Block block = vault.getBlock();
            if (block != null && block.getType() == Material.SCULK_SHRIEKER) {
                block.setType(Material.AIR, false);
            }
        }
        save();
        return true;
    }

    public void applySilentShrieker(Block block) {
        if (block == null) {
            return;
        }
        BlockData raw = block.getType() == Material.SCULK_SHRIEKER
                ? block.getBlockData()
                : Bukkit.createBlockData(Material.SCULK_SHRIEKER);
        if (raw instanceof SculkShrieker shrieker) {
            if (shrieker.isCanSummon() || !shrieker.isShrieking() || shrieker.isWaterlogged()) {
                shrieker.setCanSummon(false);
                shrieker.setShrieking(true);
                shrieker.setWaterlogged(false);
                block.setBlockData(shrieker, false);
            } else if (block.getType() != Material.SCULK_SHRIEKER) {
                block.setBlockData(shrieker, false);
            }
        } else if (block.getType() != Material.SCULK_SHRIEKER) {
            block.setType(Material.SCULK_SHRIEKER, false);
        }
    }

    public void refreshAllVaultBlocksAndHolograms() {
        for (VaultBlock vb : vaultsById.values()) {
            World world = vb.getWorld();
            if (world == null) continue;
            if (!world.isChunkLoaded(vb.getPos().getX() >> 4, vb.getPos().getZ() >> 4)) {
                continue;
            }
            Block block = vb.getBlock();
            if (block != null) {
                applySilentShrieker(block);
            }
            OpeningSession session = activeSessions.get(vb.getPos());
            ensureHologram(vb, session == null ? buildIdleText() : buildActiveText(session));
        }
    }

    public void handleChunkLoad(Chunk chunk) {
        cleanupChunkEntities(chunk);
        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        for (VaultBlock vb : vaultsById.values()) {
            BlockPosKey p = vb.getPos();
            if (p.getWorldName().equals(worldName) && (p.getX() >> 4) == cx && (p.getZ() >> 4) == cz) {
                Block b = vb.getBlock();
                if (b != null) {
                    applySilentShrieker(b);
                }
                OpeningSession session = activeSessions.get(p);
                ensureHologram(vb, session == null ? buildIdleText() : buildActiveText(session));
            }
        }
    }

    public OpeningSession getActiveSession(BlockPosKey pos) {
        return activeSessions.get(pos);
    }

    public void handlePlayerQuit(Player player) {
        UUID uid = player.getUniqueId();
        for (OpeningSession session : new ArrayList<>(activeSessions.values())) {
            if (session.openerUuid.equals(uid)) {
                activeSessions.remove(session.vault.getPos());
                abortAndDeliverRemaining(session);
                ensureHologram(session.vault, buildIdleText());
            }
        }
    }

    public boolean startOpening(Player player, VaultBlock vault, VaultKeyType keyType, List<LootEntry> rolled) {
        if (activeSessions.containsKey(vault.getPos())) {
            return false;
        }
        OpeningSession session = new OpeningSession(vault, player, keyType, rolled);
        activeSessions.put(vault.getPos(), session);

        ensureHologram(vault, buildActiveText(session));

        Location center = vault.getCenterLocation();
        World world = center.getWorld();
        float yawToPlayer = computeYawToward(center, player.getLocation());

        int insertTicks = Math.max(8, plugin.getConfig().getInt("animation.key-insert-ticks", 12));
        int closeDelayTicks = Math.max(10, plugin.getConfig().getInt("animation.close-delay-ticks", 16));
        int visualItemLife = Math.max(14, plugin.getConfig().getInt("animation.visual-item-lifetime-ticks", 22));
        int stepInterval = rolled.size() >= 5 ? 5 : (rolled.size() >= 3 ? 6 : 8);

        Location keyLoc = center.clone().add(0.0, 0.85, 0.0);
        keyLoc.setYaw(yawToPlayer);
        keyLoc.setPitch(0.0f);
        ItemDisplay keyDisplay = spawnKeyDisplay(world, keyLoc, keyType, insertTicks);
        if (keyDisplay != null) {
            session.tempEntities.add(keyDisplay);
        }

        world.playSound(center, Sound.BLOCK_VAULT_INSERT_ITEM, 1.0f, 1.0f);
        world.playSound(center, keyType.getLevel() >= 4
                ? Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE
                : Sound.BLOCK_VAULT_ACTIVATE, 0.75f, 1.15f);
        world.spawnParticle(
                keyType.getLevel() >= 4 ? Particle.TRIAL_SPAWNER_DETECTION_OMINOUS : Particle.TRIAL_SPAWNER_DETECTION,
                center.clone().add(0.0, 0.55, 0.0),
                8, 0.22, 0.12, 0.22, 0.01
        );

        session.task = new BukkitRunnable() {
            private int tick = 0;
            private int nextEjectTick = insertTicks + 2;
            private int finishTick = -1;
            private int ejectedIndex = 0;
            private TextDisplay currentItemLabel;

            @Override
            public void run() {
                tick++;

                if (tick == insertTicks) {
                    if (keyDisplay != null && keyDisplay.isValid()) {
                        keyDisplay.remove();
                    }
                    world.playSound(center, Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.95f, 1.05f);
                    world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0.0, 0.45, 0.0), 5, 0.16, 0.08, 0.16, 0.015);
                }

                if (finishTick < 0 && tick >= nextEjectTick) {
                    LootEntry next = session.remaining.pollFirst();
                    if (next != null) {
                        Player opener = Bukkit.getPlayer(session.openerUuid);
                        if (opener != null && opener.isOnline()) {
                            plugin.getKeyRegistry().deliverReward(opener, next);
                        } else {
                            plugin.getKeyRegistry().enqueuePendingReward(session.openerUuid, next);
                        }

                        spawnVisualEjectedReward(world, center, opener, next, ejectedIndex, session.allRewards.size(), visualItemLife, session);
                        if (currentItemLabel != null && currentItemLabel.isValid()) {
                            currentItemLabel.remove();
                        }
                        currentItemLabel = spawnFloatingRewardLabel(world, center.clone().add(0.0, 1.05, 0.0), next);
                        if (currentItemLabel != null) {
                            session.tempEntities.add(currentItemLabel);
                        }

                        ejectedIndex++;
                        nextEjectTick = tick + stepInterval;
                        if (session.remaining.isEmpty()) {
                            finishTick = tick + closeDelayTicks;
                        }
                    } else {
                        finishTick = tick + closeDelayTicks;
                    }
                }

                if (finishTick > 0 && tick >= finishTick) {
                    cancel();
                    if (currentItemLabel != null && currentItemLabel.isValid()) {
                        currentItemLabel.remove();
                    }
                    cleanupSessionEntities(session);
                    activeSessions.remove(vault.getPos());
                    world.playSound(center, Sound.BLOCK_VAULT_CLOSE_SHUTTER, 0.85f, 1.0f);
                    ensureHologram(vault, buildIdleText());

                    Player opener = Bukkit.getPlayer(session.openerUuid);
                    if (opener != null && opener.isOnline()) {
                        sendSummaryMessage(opener, session);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private void spawnVisualEjectedReward(
            World world,
            Location center,
            Player opener,
            LootEntry entry,
            int index,
            int total,
            int lifetimeTicks,
            OpeningSession session
    ) {
        ItemStack visualStack = entry.getPreviewItem();
        Location spawnAt = center.clone().add(0.0, 0.48, 0.0);

        Vector horiz;
        if (opener != null && opener.isOnline() && opener.getWorld().equals(world)) {
            horiz = opener.getLocation().toVector().subtract(center.toVector()).setY(0.0);
            if (horiz.lengthSquared() < 1e-4) {
                horiz = new Vector(0.0, 0.0, 1.0);
            } else {
                horiz.normalize();
            }
        } else {
            horiz = new Vector(0.0, 0.0, 1.0);
        }

        if (total > 1) {
            double spreadRad = Math.toRadians(((index - (total - 1) / 2.0) * 16.0));
            double cos = Math.cos(spreadRad);
            double sin = Math.sin(spreadRad);
            double nx = horiz.getX() * cos - horiz.getZ() * sin;
            double nz = horiz.getX() * sin + horiz.getZ() * cos;
            horiz = new Vector(nx, 0.0, nz);
        }
        Vector velocity = horiz.multiply(0.115).setY(0.30);

        Item dropped = world.dropItem(spawnAt, visualStack, item -> {
            item.setPickupDelay(32767);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setPersistent(false);
            item.setInvulnerable(true);
            item.setVelocity(velocity);
            item.getPersistentDataContainer().set(tempEntityPdc, PersistentDataType.BYTE, (byte) 1);
        });
        session.tempEntities.add(dropped);

        Rarity rarity = entry.getRarity();
        world.playSound(center, Sound.BLOCK_VAULT_EJECT_ITEM, 0.95f, 0.95f + Math.min(0.35f, index * 0.05f));
        if (rarity == Rarity.LEGENDARY) {
            world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.65f, 1.25f);
        } else if (rarity == Rarity.EPIC) {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dropped.isValid()) {
                Location end = dropped.getLocation();
                end.getWorld().spawnParticle(Particle.CLOUD, end, 2, 0.05, 0.05, 0.05, 0.01);
                dropped.remove();
            }
        }, lifetimeTicks);
    }

    private ItemDisplay spawnKeyDisplay(World world, Location loc, VaultKeyType keyType, int insertTicks) {
        Entity spawned = world.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        if (!(spawned instanceof ItemDisplay display)) {
            spawned.remove();
            return null;
        }
        display.setPersistent(false);
        display.getPersistentDataContainer().set(tempEntityPdc, PersistentDataType.BYTE, (byte) 1);
        display.setItemStack(new ItemStack(keyType.getKeyMaterial(), 1));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.22f, 0.0f),
                new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                new Vector3f(0.62f, 0.62f, 0.62f),
                new AxisAngle4f((float) Math.toRadians(90.0), 0.0f, 0.0f, 1.0f)
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(Math.max(4, insertTicks - 2));
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, -0.10f, 0.0f),
                    new AxisAngle4f((float) Math.toRadians(90.0), 0.0f, 1.0f, 0.0f),
                    new Vector3f(0.56f, 0.56f, 0.56f),
                    new AxisAngle4f((float) Math.toRadians(90.0), 0.0f, 0.0f, 1.0f)
            ));
        }, 1L);
        return display;
    }

    private TextDisplay spawnFloatingRewardLabel(World world, Location loc, LootEntry entry) {
        Entity spawned = world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        if (!(spawned instanceof TextDisplay td)) {
            spawned.remove();
            return null;
        }
        ItemStack stack = entry.getPreviewItem();
        String color = plugin.getKeyRegistry().getRarityColor(entry.getRarity());
        String title = TextUtil.cleanItemTitle(stack);
        String label = TextUtil.color(color + title + (stack.getAmount() > 1 ? " &7x" + stack.getAmount() : ""));

        td.setPersistent(false);
        td.getPersistentDataContainer().set(tempEntityPdc, PersistentDataType.BYTE, (byte) 1);
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setDefaultBackground(false);
        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        td.setShadowed(true);
        td.setSeeThrough(false);
        td.setLineWidth(200);
        td.setViewRange(0.45f);
        td.setText(label);
        return td;
    }

    private void sendSummaryMessage(Player opener, OpeningSession session) {
        String header = plugin.rawMsg("opened-summary-header")
                .replace("{key}", TextUtil.color(session.keyType.getDisplayName()));
        opener.sendMessage(TextUtil.color(header));
        String lineTpl = plugin.rawMsg("opened-summary-line");
        for (LootEntry entry : session.allRewards) {
            ItemStack st = entry.getPreviewItem();
            String line = lineTpl
                    .replace("{rarity_color}", plugin.getKeyRegistry().getRarityColor(entry.getRarity()))
                    .replace("{rarity}", plugin.getKeyRegistry().getRarityName(entry.getRarity()))
                    .replace("{item}", TextUtil.cleanItemTitle(st))
                    .replace("{amount}", String.valueOf(st.getAmount()));
            opener.sendMessage(TextUtil.color(line));
        }
    }

    private void abortAndDeliverRemaining(OpeningSession session) {
        if (session.task != null) {
            session.task.cancel();
            session.task = null;
        }
        Player opener = Bukkit.getPlayer(session.openerUuid);
        while (!session.remaining.isEmpty()) {
            LootEntry next = session.remaining.pollFirst();
            if (next == null) continue;
            if (opener != null && opener.isOnline()) {
                plugin.getKeyRegistry().deliverReward(opener, next);
            } else {
                plugin.getKeyRegistry().enqueuePendingReward(session.openerUuid, next);
            }
        }
        cleanupSessionEntities(session);
    }

    private void cleanupSessionEntities(OpeningSession session) {
        for (Entity e : session.tempEntities) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        session.tempEntities.clear();
    }

    private String buildIdleText() {
        List<String> lines = plugin.getConfig().getStringList("hologram.idle-lines");
        if (lines.isEmpty()) {
            lines = List.of("&#F8BEFBХранилище", "&7ПКМ ключом для открытия");
        }
        return joinColored(lines);
    }

    private String buildActiveText(OpeningSession session) {
        List<String> lines = plugin.getConfig().getStringList("hologram.active-lines");
        if (lines.isEmpty()) {
            lines = List.of("&#F8BEFBХранилище", "&fОткрывает: &#F8BEFB{player}", "&7Ключ: {key}");
        }
        List<String> replaced = new ArrayList<>(lines.size());
        for (String line : lines) {
            replaced.add(line
                    .replace("{player}", session.openerName)
                    .replace("{key}", session.keyType.getDisplayName()));
        }
        return joinColored(replaced);
    }

    private static String joinColored(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(TextUtil.color(lines.get(i)));
        }
        return sb.toString();
    }

    private void ensureHologram(VaultBlock vault, String coloredText) {
        if (!plugin.getConfig().getBoolean("hologram.enabled", true)) {
            removeHologram(vault);
            return;
        }
        World world = vault.getWorld();
        if (world == null || !world.isChunkLoaded(vault.getPos().getX() >> 4, vault.getPos().getZ() >> 4)) {
            return;
        }
        double offsetY = plugin.getConfig().getDouble("hologram.height-offset", 1.45);
        Location holoLoc = new Location(
                world,
                vault.getPos().getX() + 0.5,
                vault.getPos().getY() + offsetY,
                vault.getPos().getZ() + 0.5
        );

        TextDisplay display = findExistingHologram(vault, holoLoc);
        if (display == null) {
            Entity spawned = world.spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
            if (!(spawned instanceof TextDisplay created)) {
                spawned.remove();
                return;
            }
            display = created;
            vault.setHologramUuid(display.getUniqueId());
        } else {
            display.teleport(holoLoc);
        }

        display.setPersistent(false);
        display.getPersistentDataContainer().set(hologramPdc, PersistentDataType.STRING, vault.getId());
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setLineWidth(plugin.getConfig().getInt("hologram.line-width", 220));
        display.setViewRange((float) plugin.getConfig().getDouble("hologram.view-range", 0.65));
        display.setText(coloredText);
    }

    private TextDisplay findExistingHologram(VaultBlock vault, Location center) {
        World world = center.getWorld();
        if (world == null) return null;
        UUID known = vault.getHologramUuid();
        TextDisplay keeper = null;
        for (Entity entity : world.getNearbyEntities(center, 2.5, 3.0, 2.5)) {
            if (!(entity instanceof TextDisplay td)) continue;
            String tag = td.getPersistentDataContainer().get(hologramPdc, PersistentDataType.STRING);
            if (tag == null) continue;
            if (keeper == null && (tag.equals(vault.getId()) || td.getUniqueId().equals(known))) {
                keeper = td;
            } else if (tag.equals(vault.getId())) {
                td.remove();
            }
        }
        return keeper;
    }

    private void removeHologram(VaultBlock vault) {
        World world = vault.getWorld();
        if (world == null) return;
        if (!world.isChunkLoaded(vault.getPos().getX() >> 4, vault.getPos().getZ() >> 4)) {
            return;
        }
        Location center = vault.getCenterLocation();
        for (Entity entity : world.getNearbyEntities(center, 3.0, 4.0, 3.0)) {
            if (entity.getPersistentDataContainer().has(hologramPdc, PersistentDataType.STRING)) {
                String id = entity.getPersistentDataContainer().get(hologramPdc, PersistentDataType.STRING);
                if (vault.getId().equals(id)) {
                    entity.remove();
                }
            }
        }
        vault.setHologramUuid(null);
    }

    private void cleanupAllTaggedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                cleanupChunkEntities(chunk);
            }
        }
    }

    private void cleanupChunkEntities(Chunk chunk) {
        for (Entity e : chunk.getEntities()) {
            if (e.getPersistentDataContainer().has(tempEntityPdc, PersistentDataType.BYTE)
                    || e.getPersistentDataContainer().has(hologramPdc, PersistentDataType.STRING)) {
                e.remove();
            }
        }
    }

    private static float computeYawToward(Location from, Location target) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        if (Math.abs(dx) < 1e-4 && Math.abs(dz) < 1e-4) {
            return 0.0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
