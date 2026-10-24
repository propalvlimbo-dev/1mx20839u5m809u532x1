package ru.elytrix.dungeons.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class DungeonInstance {

    private final String id;
    private final String displayName;
    private String templateId;
    private final DungeonCategory category;

    private BlockPosKey origin;
    private CuboidRegion structureBounds;
    private CuboidRegion snapshotBounds;
    private int surfaceY;

    private DungeonStatus status = DungeonStatus.READY;
    private long nextResetEpochMs;
    private long ghostRespawnAtMs;
    private UUID hologramUuid;

    private BlockPosKey hologramAnchor;
    private BlockPosKey entrancePos;
    private final List<BlockPosKey> chestPositions = new ArrayList<>();
    private final List<BlockPosKey> barrelPositions = new ArrayList<>();
    private final Set<BlockPosKey> openedContainers = new HashSet<>();
    private final Map<BlockPosKey, Boolean> shulkerUnlocked = new LinkedHashMap<>();
    private final List<BlockPosKey> shulkerPositions = new ArrayList<>();
    private final Map<BlockPosKey, String> surfaceShulkerRarities = new LinkedHashMap<>();
    private final Map<BlockPosKey, Long> surfaceShulkerRefreshAtMs = new LinkedHashMap<>();
    private final Set<BlockPosKey> pendingSurfaceShulkerRefresh = new HashSet<>();
    private final List<BlockPosKey> mobSpawns = new ArrayList<>();

    public DungeonInstance(
            String id,
            String displayName,
            String templateId,
            DungeonCategory category,
            BlockPosKey origin,
            CuboidRegion structureBounds,
            CuboidRegion snapshotBounds,
            int surfaceY,
            long nextResetEpochMs
    ) {
        this.id = id;
        this.displayName = displayName;
        this.templateId = templateId;
        this.category = category;
        this.origin = origin;
        this.structureBounds = structureBounds.normalized();
        this.snapshotBounds = snapshotBounds.normalized();
        this.surfaceY = surfaceY;
        this.nextResetEpochMs = nextResetEpochMs;
    }

    public void applyTemplateMarkers(DungeonTemplate tpl) {
        this.templateId = tpl.getId();
        int[] holo = tpl.getHologramOffset();
        this.hologramAnchor = origin.add(holo[0], holo[1], holo[2]);
        int[] ent = tpl.getEntranceOffset();
        this.entrancePos = origin.add(ent[0], ent[1], ent[2]);

        this.chestPositions.clear();
        for (int[] off : tpl.getChestOffsets()) {
            this.chestPositions.add(origin.add(off[0], off[1], off[2]));
        }
        this.barrelPositions.clear();
        for (int[] off : tpl.getBarrelOffsets()) {
            this.barrelPositions.add(origin.add(off[0], off[1], off[2]));
        }
        this.openedContainers.clear();

        this.shulkerUnlocked.clear();
        this.shulkerPositions.clear();
        this.surfaceShulkerRarities.clear();
        this.surfaceShulkerRefreshAtMs.clear();
        this.pendingSurfaceShulkerRefresh.clear();
        for (int[] off : tpl.getShulkerOffsets()) {
            BlockPosKey shulker = origin.add(off[0], off[1], off[2]);
            this.shulkerPositions.add(shulker);
            if (this.category == DungeonCategory.UNDERGROUND) {
                this.shulkerUnlocked.put(shulker, Boolean.FALSE);
            }
        }

        this.mobSpawns.clear();
        for (int[] off : tpl.getMobSpawnOffsets()) {
            this.mobSpawns.add(origin.add(off[0], off[1], off[2]));
        }
    }

    public CuboidRegion claimGuardRegion(int padding) {
        return structureBounds.expand(padding, 16);
    }

    /**
     * Returns a centered, walkable ground-floor arrival point. The schematic analyzer sets the
     * preferred point in the connected entrance/corridor network; this runtime check handles a
     * block changed by a player without ever falling back to a hologram anchor.
     */
    public Location teleportLocation() {
        BlockPosKey preferred = entrancePos != null ? entrancePos : origin;
        World world = preferred.world();
        if (world == null || !world.isChunkLoaded(preferred.x() >> 4, preferred.z() >> 4)) {
            return preferred.floorLocation();
        }
        if (isSafeArrival(world, preferred)) return preferred.floorLocation();

        for (int radius = 1; radius <= 8; radius++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPosKey candidate = preferred.add(dx, dy, dz);
                        if (getBounds().containsBlock(candidate.worldName(), candidate.x(), candidate.y(), candidate.z())
                                && world.isChunkLoaded(candidate.x() >> 4, candidate.z() >> 4)
                                && isSafeArrival(world, candidate)) {
                            return candidate.floorLocation();
                        }
                    }
                }
            }
        }
        return preferred.floorLocation();
    }

    private static boolean isSafeArrival(World world, BlockPosKey point) {
        if (point.y() <= world.getMinHeight() || point.y() + 1 >= world.getMaxHeight()) return false;
        return world.getBlockAt(point.x(), point.y() - 1, point.z()).isSolid()
                && world.getBlockAt(point.x(), point.y(), point.z()).isPassable()
                && world.getBlockAt(point.x(), point.y() + 1, point.z()).isPassable();
    }

    public long secondsUntilReset() {
        return Math.max(0L, (nextResetEpochMs - System.currentTimeMillis()) / 1000L);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getTemplateId() { return templateId; }
    public DungeonCategory getCategory() { return category; }
    public BlockPosKey getOrigin() { return origin; }
    public void setOrigin(BlockPosKey origin) { this.origin = origin; }

    public CuboidRegion getBounds() { return structureBounds; }
    public void setBounds(CuboidRegion bounds) { this.structureBounds = bounds.normalized(); }

    public CuboidRegion getSnapshotBounds() { return snapshotBounds; }
    public void setSnapshotBounds(CuboidRegion snapshotBounds) { this.snapshotBounds = snapshotBounds.normalized(); }

    public int getSurfaceY() { return surfaceY; }
    public void setSurfaceY(int surfaceY) { this.surfaceY = surfaceY; }

    public DungeonStatus getStatus() { return status; }
    public void setStatus(DungeonStatus status) { this.status = status; }

    public long getNextResetEpochMs() { return nextResetEpochMs; }
    public void setNextResetEpochMs(long ms) { this.nextResetEpochMs = ms; }

    public long getGhostRespawnAtMs() { return ghostRespawnAtMs; }
    public void setGhostRespawnAtMs(long ms) { this.ghostRespawnAtMs = Math.max(0L, ms); }

    public UUID getHologramUuid() { return hologramUuid; }
    public void setHologramUuid(UUID hologramUuid) { this.hologramUuid = hologramUuid; }

    public BlockPosKey getHologramAnchor() { return hologramAnchor; }
    public BlockPosKey getEntrancePos() { return entrancePos; }
    public List<BlockPosKey> getChestPositions() { return chestPositions; }
    public List<BlockPosKey> getBarrelPositions() { return barrelPositions; }
    public Set<BlockPosKey> getOpenedContainers() { return openedContainers; }
    public boolean isContainerOpened(BlockPosKey pos) { return openedContainers.contains(pos); }
    public void markContainerOpened(BlockPosKey pos) { openedContainers.add(pos); }
    public void clearOpenedContainers() { openedContainers.clear(); }
    public Map<BlockPosKey, Boolean> getShulkerUnlocked() { return shulkerUnlocked; }
    public List<BlockPosKey> getShulkerPositions() { return shulkerPositions; }
    public Map<BlockPosKey, String> getSurfaceShulkerRarities() { return surfaceShulkerRarities; }
    public Map<BlockPosKey, Long> getSurfaceShulkerRefreshAtMs() { return surfaceShulkerRefreshAtMs; }
    public Set<BlockPosKey> getPendingSurfaceShulkerRefresh() { return pendingSurfaceShulkerRefresh; }
    public List<BlockPosKey> getMobSpawns() { return mobSpawns; }
}
