package ru.elytrix.dungeons.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class DungeonInstance {

    private final String id;
    private String displayName;
    private String templateId;
    private DungeonCategory category;
    private BlockPosKey origin;
    private CuboidRegion bounds;
    private int surfaceY;
    private DungeonStatus status = DungeonStatus.READY;
    private long nextResetEpochMs;
    private UUID hologramUuid;

    private BlockPosKey entrancePos;
    private BlockPosKey hologramAnchor;
    private final Set<BlockPosKey> chestPositions = new LinkedHashSet<>();
    private final Set<BlockPosKey> barrelPositions = new LinkedHashSet<>();
    // Shulkers: on SURFACE up to 3 Unique-Key shulkers (false = still locked, true = unlocked by key);
    // on UNDERGROUND main shulkers (false = locked until mob fight cleared, true = unlocked)
    private final Map<BlockPosKey, Boolean> shulkerUnlocked = new LinkedHashMap<>();
    // SKY Angelic Shards: map from block position -> number of explosions triggered so far (removed at 5)
    private final Map<BlockPosKey, Integer> shardExplosions = new LinkedHashMap<>();
    private int initialShardCount = 0;
    private final List<BlockPosKey> mobSpawns = new ArrayList<>();

    public DungeonInstance(
            String id,
            String displayName,
            String templateId,
            DungeonCategory category,
            BlockPosKey origin,
            CuboidRegion bounds,
            int surfaceY,
            long nextResetEpochMs
    ) {
        this.id = id;
        this.displayName = displayName;
        this.templateId = templateId;
        this.category = category;
        this.origin = origin;
        this.bounds = bounds;
        this.surfaceY = surfaceY;
        this.nextResetEpochMs = nextResetEpochMs;
    }

    public void applyTemplateMarkers(DungeonTemplate tpl, int maxSurfaceUniqueShulkers) {
        chestPositions.clear();
        barrelPositions.clear();
        shulkerUnlocked.clear();
        shardExplosions.clear();
        mobSpawns.clear();

        int[] ent = tpl.getEntranceOffset();
        this.entrancePos = origin.add(ent[0], ent[1], ent[2]);

        int[] holo = tpl.getHologramAnchorOffset();
        this.hologramAnchor = origin.add(holo[0], holo[1], holo[2]);

        for (int[] c : tpl.getChestOffsets()) {
            chestPositions.add(origin.add(c[0], c[1], c[2]));
        }
        for (int[] b : tpl.getBarrelOffsets()) {
            barrelPositions.add(origin.add(b[0], b[1], b[2]));
        }
        int addedShulkers = 0;
        for (int[] s : tpl.getShulkerOffsets()) {
            if (category == DungeonCategory.SURFACE && addedShulkers >= maxSurfaceUniqueShulkers) {
                break;
            }
            shulkerUnlocked.put(origin.add(s[0], s[1], s[2]), Boolean.FALSE);
            addedShulkers++;
        }
        for (int[] sh : tpl.getAngelicShardOffsets()) {
            shardExplosions.put(origin.add(sh[0], sh[1], sh[2]), 0);
        }
        this.initialShardCount = shardExplosions.size();
        for (int[] m : tpl.getMobSpawnOffsets()) {
            mobSpawns.add(origin.add(m[0], m[1], m[2]));
        }
    }

    public int countLockedShulkers() {
        int c = 0;
        for (Boolean unlocked : shulkerUnlocked.values()) {
            if (!Boolean.TRUE.equals(unlocked)) c++;
        }
        return c;
    }

    public CuboidRegion claimGuardRegion(int padding) {
        return bounds.expand(padding, 4);
    }

    public long secondsUntilReset() {
        return Math.max(0L, (nextResetEpochMs - System.currentTimeMillis()) / 1000L);
    }

    public Location teleportLocation() {
        World w = origin.world();
        if (w == null) {
            return null;
        }
        BlockPosKey p = entrancePos != null ? entrancePos : hologramAnchor;
        Location loc = new Location(w, p.x() + 0.5, p.y() + 0.05, p.z() + 0.5);
        if (hologramAnchor != null && !p.equals(hologramAnchor)) {
            double dx = hologramAnchor.x() - p.x();
            double dz = hologramAnchor.z() - p.z();
            loc.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        return loc;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getTemplateId() { return templateId; }
    public DungeonCategory getCategory() { return category; }
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
    public BlockPosKey getHologramAnchor() { return hologramAnchor; }
    public Set<BlockPosKey> getChestPositions() { return chestPositions; }
    public Set<BlockPosKey> getBarrelPositions() { return barrelPositions; }
    public Map<BlockPosKey, Boolean> getShulkerUnlocked() { return shulkerUnlocked; }
    public Map<BlockPosKey, Integer> getShardExplosions() { return shardExplosions; }
    public int getInitialShardCount() { return Math.max(initialShardCount, shardExplosions.size()); }
    public void setInitialShardCount(int initialShardCount) { this.initialShardCount = initialShardCount; }
    public List<BlockPosKey> getMobSpawns() { return mobSpawns; }
}
