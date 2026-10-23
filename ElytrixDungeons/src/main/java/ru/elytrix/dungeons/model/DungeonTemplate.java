package ru.elytrix.dungeons.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DungeonTemplate {

    private final String id;
    private final String displayName;
    private final DungeonCategory category;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private final List<String> palette = new ArrayList<>();
    private final short[] grid;

    private int[] hologramOffset;
    private int[] entranceOffset;
    private final List<int[]> chestOffsets = new ArrayList<>();
    private final List<int[]> barrelOffsets = new ArrayList<>();
    private final List<int[]> shulkerOffsets = new ArrayList<>();
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
        this.palette.add("minecraft:air");
        Arrays.fill(this.grid, (short) 0);
        this.hologramOffset = new int[]{sizeX / 2, Math.max(2, sizeY / 3), sizeZ / 2};
        this.entranceOffset = new int[]{sizeX / 2, 1, sizeZ / 2};
    }

    public int paletteIndex(String blockDataString) {
        int idx = palette.indexOf(blockDataString);
        if (idx >= 0) return idx;
        palette.add(blockDataString);
        return palette.size() - 1;
    }

    public void setBlock(int x, int y, int z, String blockDataString) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return;
        grid[(y * sizeZ + z) * sizeX + x] = (short) paletteIndex(blockDataString);
    }

    public String getBlock(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return "minecraft:air";
        }
        int p = grid[(y * sizeZ + z) * sizeX + x] & 0xFFFF;
        return p < palette.size() ? palette.get(p) : "minecraft:air";
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public DungeonCategory getCategory() { return category; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public List<String> getPalette() { return palette; }

    public int[] getHologramOffset() { return hologramOffset; }
    public void setHologramOffset(int x, int y, int z) { this.hologramOffset = new int[]{x, y, z}; }

    public int[] getEntranceOffset() { return entranceOffset; }
    public void setEntranceOffset(int x, int y, int z) { this.entranceOffset = new int[]{x, y, z}; }

    public List<int[]> getChestOffsets() { return chestOffsets; }
    public List<int[]> getBarrelOffsets() { return barrelOffsets; }
    public List<int[]> getShulkerOffsets() { return shulkerOffsets; }
    public List<int[]> getMobSpawnOffsets() { return mobSpawnOffsets; }
}
