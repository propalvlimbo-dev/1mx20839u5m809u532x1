package ru.elytrix.dungeons.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonTemplate {

    private final String id;
    private String displayName;
    private DungeonCategory category;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> paletteIndex = new HashMap<>();
    private final short[] grid;

    private int[] entranceOffset;
    private int[] hologramAnchorOffset;
    private final List<int[]> chestOffsets = new ArrayList<>();
    private final List<int[]> barrelOffsets = new ArrayList<>();
    private final List<int[]> shulkerOffsets = new ArrayList<>();
    private final List<int[]> angelicShardOffsets = new ArrayList<>();
    private final List<int[]> mobSpawnOffsets = new ArrayList<>();

    public DungeonTemplate(
            String id,
            String displayName,
            DungeonCategory category,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.grid = new short[sizeX * sizeY * sizeZ];
        paletteId("minecraft:air");
        this.entranceOffset = new int[]{sizeX / 2, 1, sizeZ / 2};
        this.hologramAnchorOffset = new int[]{sizeX / 2, 2, sizeZ / 2};
    }

    public short paletteId(String blockDataString) {
        Short existing = paletteIndex.get(blockDataString);
        if (existing != null) {
            return existing;
        }
        short idVal = (short) palette.size();
        palette.add(blockDataString);
        paletteIndex.put(blockDataString, idVal);
        return idVal;
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    public void setBlock(int x, int y, int z, String blockDataString) {
        if (!inBounds(x, y, z)) {
            return;
        }
        grid[(y * sizeZ + z) * sizeX + x] = paletteId(blockDataString);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String blockDataString) {
        short pid = paletteId(blockDataString);
        int ax1 = Math.max(0, Math.min(x1, x2)), ax2 = Math.min(sizeX - 1, Math.max(x1, x2));
        int ay1 = Math.max(0, Math.min(y1, y2)), ay2 = Math.min(sizeY - 1, Math.max(y1, y2));
        int az1 = Math.max(0, Math.min(z1, z2)), az2 = Math.min(sizeZ - 1, Math.max(z1, z2));
        for (int y = ay1; y <= ay2; y++) {
            for (int z = az1; z <= az2; z++) {
                int base = (y * sizeZ + z) * sizeX;
                for (int x = ax1; x <= ax2; x++) {
                    grid[base + x] = pid;
                }
            }
        }
    }

    public String getBlock(int x, int y, int z) {
        if (!inBounds(x, y, z)) {
            return "minecraft:air";
        }
        int idx = grid[(y * sizeZ + z) * sizeX + x] & 0xFFFF;
        return idx < palette.size() ? palette.get(idx) : "minecraft:air";
    }

    public int getPaletteIdAt(int x, int y, int z) {
        return grid[(y * sizeZ + z) * sizeX + x] & 0xFFFF;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public DungeonCategory getCategory() { return category; }
    public void setCategory(DungeonCategory category) { this.category = category; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public List<String> getPalette() { return palette; }
    public int[] getEntranceOffset() { return entranceOffset; }
    public void setEntranceOffset(int x, int y, int z) { this.entranceOffset = new int[]{x, y, z}; }
    public int[] getHologramAnchorOffset() { return hologramAnchorOffset; }
    public void setHologramAnchorOffset(int x, int y, int z) { this.hologramAnchorOffset = new int[]{x, y, z}; }
    public List<int[]> getChestOffsets() { return chestOffsets; }
    public List<int[]> getBarrelOffsets() { return barrelOffsets; }
    public List<int[]> getShulkerOffsets() { return shulkerOffsets; }
    public List<int[]> getAngelicShardOffsets() { return angelicShardOffsets; }
    public List<int[]> getMobSpawnOffsets() { return mobSpawnOffsets; }
}
