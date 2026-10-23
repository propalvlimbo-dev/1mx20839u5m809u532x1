package ru.elytrix.dungeons.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class DungeonInstance {

    private final String id;
    private String displayName;
    private String templateId;
    private DungeonCategory category;
    private int tier;
    private TrialType trialType;
    private BlockPosKey origin;
    private CuboidRegion bounds;
    private int surfaceY;
    private DungeonStatus status = DungeonStatus.READY;
    private long nextResetEpochMs;
    private UUID hologramUuid;

    private BlockPosKey entrancePos;
    private BlockPosKey altarPos;
    private final List<BlockPosKey> sealPositions = new ArrayList<>();
    private final Set<BlockPosKey> explorerChests = new HashSet<>();
    private final Set<BlockPosKey> rewardChests = new HashSet<>();
    private final List<BlockPosKey> mobSpawns = new ArrayList<>();

    public DungeonInstance(
            String id,
            String displayName,
            String templateId,
            DungeonCategory category,
            int tier,
            TrialType trialType,
            BlockPosKey origin,
            CuboidRegion bounds,
            int surfaceY,
            long nextResetEpochMs
    ) {
        this.id = id;
        this.displayName = displayName;
        this.templateId = templateId;
        this.category = category;
        this.tier = tier;
        this.trialType = trialType;
        this.origin = origin;
        this.bounds = bounds;
        this.surfaceY = surfaceY;
        this.nextResetEpochMs = nextResetEpochMs;
    }

    public void applyTemplateMarkers(DungeonTemplate tpl) {
        sealPositions.clear();
        explorerChests.clear();
        rewardChests.clear();
        mobSpawns.clear();

        int[] ent = tpl.getEntranceOffset();
        this.entrancePos = origin.add(ent[0], ent[1], ent[2]);

        int[] alt = tpl.getAltarOffset();
        this.altarPos = origin.add(alt[0], alt[1], alt[2]);

        for (int[] s : tpl.getSealOffsets()) {
            sealPositions.add(origin.add(s[0], s[1], s[2]));
        }
        for (int[] c : tpl.getExplorerChestOffsets()) {
            explorerChests.add(origin.add(c[0], c[1], c[2]));
        }
        for (int[] rc : tpl.getRewardChestOffsets()) {
            rewardChests.add(origin.add(rc[0], rc[1], rc[2]));
        }
        for (int[] m : tpl.getMobSpawnOffsets()) {
            mobSpawns.add(origin.add(m[0], m[1], m[2]));
        }
    }

    public CuboidRegion claimGuardRegion(int padding) {
        return bounds.expand(padding, 4);
    }

    public boolean isProtectedMechanismBlock(BlockPosKey key) {
        if (key.equals(altarPos)) {
            return true;
        }
        if (sealPositions.contains(key)) {
            return true;
        }
        return status != DungeonStatus.CLEARED && rewardChests.contains(key);
    }

    public long secondsUntilReset() {
        return Math.max(0L, (nextResetEpochMs - System.currentTimeMillis()) / 1000L);
    }

    public Location teleportLocation() {
        World w = origin.world();
        if (w == null) {
            return null;
        }
        BlockPosKey p = entrancePos != null ? entrancePos : altarPos;
        Location loc = new Location(w, p.x() + 0.5, p.y() + 0.05, p.z() + 0.5);
        if (altarPos != null && !p.equals(altarPos)) {
            double dx = (altarPos.x() - p.x());
            double dz = (altarPos.z() - p.z());
            loc.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        return loc;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public DungeonCategory getCategory() { return category; }
    public int getTier() { return tier; }
    public TrialType getTrialType() { return trialType; }
    public BlockPosKey getOrigin() { return origin; }
    public void setOrigin(BlockPosKey origin) { this.origin = origin; }
    public CuboidRegion getBounds() { return bounds; }
    public void setBounds(CuboidRegion bounds) { this.bounds = bounds; }
    public int getSurfaceY() { return surfaceY; }
    public void setSurfaceY(int surfaceY) { this.surfaceY = surfaceY; }
    public DungeonStatus getStatus() { return status; }
    public void setStatus(DungeonStatus status) { this.status = status; }
    public long getNextResetEpochMs() { return nextResetEpochMs; }
    public void setNextResetEpochMs(long nextResetEpochMs) { this.nextResetEpochMs = nextResetEpochMs; }
    public UUID getHologramUuid() { return hologramUuid; }
    public void setHologramUuid(UUID hologramUuid) { this.hologramUuid = hologramUuid; }
    public BlockPosKey getEntrancePos() { return entrancePos; }
    public void setEntrancePos(BlockPosKey entrancePos) { this.entrancePos = entrancePos; }
    public BlockPosKey getAltarPos() { return altarPos; }
    public void setAltarPos(BlockPosKey altarPos) { this.altarPos = altarPos; }
    public List<BlockPosKey> getSealPositions() { return sealPositions; }
    public Set<BlockPosKey> getExplorerChests() { return explorerChests; }
    public Set<BlockPosKey> getRewardChests() { return rewardChests; }
    public List<BlockPosKey> getMobSpawns() { return mobSpawns; }
}
