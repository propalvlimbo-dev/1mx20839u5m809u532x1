package ru.elytrix.dungeons.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

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
    private UUID hologramUuid;

    private BlockPosKey hologramAnchor;
    private BlockPosKey entrancePos;
    private final List<BlockPosKey> chestPositions = new ArrayList<>();
    private final List<BlockPosKey> barrelPositions = new ArrayList<>();
    private final Set<BlockPosKey> openedContainers = new HashSet<>();
    private final Map<BlockPosKey, Boolean> shulkerUnlocked = new LinkedHashMap<>();
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
        if (this.category == DungeonCategory.UNDERGROUND) {
            for (int[] off : tpl.getShulkerOffsets()) {
                this.shulkerUnlocked.put(origin.add(off[0], off[1], off[2]), Boolean.FALSE);
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

    public Location teleportLocation() {
        return entrancePos != null ? entrancePos.floorLocation()
                : (hologramAnchor != null ? hologramAnchor.floorLocation() : origin.floorLocation());
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
    public List<BlockPosKey> getMobSpawns() { return mobSpawns; }
}
