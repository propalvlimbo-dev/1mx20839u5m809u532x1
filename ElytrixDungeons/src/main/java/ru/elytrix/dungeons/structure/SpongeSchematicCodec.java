package ru.elytrix.dungeons.structure;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonTemplate;

/**
 * Full Sponge Schematic v2 & v3 (WorldEdit / FAWE 1.21.x) reader & writer
 * with smart interior room/floor detection for Houses (Allay ghosts + chests)
 * and Underground Vaults (locked Boss Shulker + guard spawns + side containers).
 */
public final class SpongeSchematicCodec {

    private SpongeSchematicCodec() {}

    public static void writeSchem(DungeonTemplate tpl, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ByteArrayOutputStream blockBuf = new ByteArrayOutputStream(tpl.getSizeX() * tpl.getSizeY() * tpl.getSizeZ());
        for (int y = 0; y < tpl.getSizeY(); y++) {
            for (int z = 0; z < tpl.getSizeZ(); z++) {
                for (int x = 0; x < tpl.getSizeX(); x++) {
                    writeVarInt(blockBuf, tpl.paletteIndex(tpl.getBlock(x, y, z)));
                }
            }
        }
        byte[] blockBytes = blockBuf.toByteArray();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(targetFile))))) {
            out.writeByte(10);
            out.writeUTF("Schematic");

            writeIntTag(out, "Version", 2);
            writeIntTag(out, "DataVersion", 4189);
            writeShortTag(out, "Width", (short) tpl.getSizeX());
            writeShortTag(out, "Height", (short) tpl.getSizeY());
            writeShortTag(out, "Length", (short) tpl.getSizeZ());
            writeIntTag(out, "PaletteMax", tpl.getPalette().size());

            out.writeByte(10);
            out.writeUTF("Palette");
            List<String> pal = tpl.getPalette();
            for (int i = 0; i < pal.size(); i++) {
                writeIntTag(out, pal.get(i), i);
            }
            out.writeByte(0);

            out.writeByte(7);
            out.writeUTF("BlockData");
            out.writeInt(blockBytes.length);
            out.write(blockBytes);

            out.writeByte(0);
        }
    }

    @SuppressWarnings("unchecked")
    public static DungeonTemplate readSchem(File file, DungeonCategory category) throws IOException {
        Map<String, Object> root;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            int rootTag = in.readByte();
            if (rootTag != 10) {
                throw new IOException("Expected NBT compound tag in " + file.getName());
            }
            in.readUTF();
            root = readCompound(in);
        }

        // Unwrap Sponge v3 outer {"Schematic": {...}} if present
        if (root.get("Schematic") instanceof Map<?, ?> inner) {
            root = (Map<String, Object>) inner;
        }

        int width = toShortInt(root.get("Width"));
        int height = toShortInt(root.get("Height"));
        int length = toShortInt(root.get("Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Invalid .schem dimensions in " + file.getName());
        }

        Map<String, Object> paletteObj = null;
        byte[] blockData = null;

        // Sponge v2 direct fields
        if (root.get("Palette") instanceof Map<?, ?> p2) {
            paletteObj = (Map<String, Object>) p2;
        }
        if (root.get("BlockData") instanceof byte[] bd2) {
            blockData = bd2;
        }

        // Sponge v3 nested Blocks compound: Blocks -> { Palette: {...}, Data: byte[] }
        if ((paletteObj == null || blockData == null) && root.get("Blocks") instanceof Map<?, ?> blocksMap) {
            if (blocksMap.get("Palette") instanceof Map<?, ?> p3) {
                paletteObj = (Map<String, Object>) p3;
            }
            if (blocksMap.get("Data") instanceof byte[] bd3) {
                blockData = bd3;
            }
        }

        if (paletteObj == null || blockData == null) {
            throw new IOException("Missing Palette/BlockData in " + file.getName());
        }

        Map<Integer, String> rev = new HashMap<>();
        for (Map.Entry<String, Object> e : paletteObj.entrySet()) {
            rev.put(((Number) e.getValue()).intValue(), e.getKey());
        }

        String cleanId = file.getName().replaceAll("(?i)\\.(schem|schematic)$", "").toLowerCase(Locale.ROOT);
        String title = category == DungeonCategory.SURFACE ? "Дом призраков" : "Подземный данж";
        DungeonTemplate tpl = new DungeonTemplate(cleanId, title, category, width, height, length);

        int[] cursor = {0};
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int pid = readVarInt(blockData, cursor);
                    String state = rev.getOrDefault(pid, "minecraft:air");
                    tpl.setBlock(x, y, z, state);
                }
            }
        }

        analyzeAndPrepareMarkers(tpl, category);
        return tpl;
    }

    private static void analyzeAndPrepareMarkers(DungeonTemplate tpl, DungeonCategory category) {
        int sx = tpl.getSizeX(), sy = tpl.getSizeY(), sz = tpl.getSizeZ();
        int midX = sx / 2, midZ = sz / 2;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    String base = stripBlockProps(tpl.getBlock(x, y, z));
                    if (base.equals("chest") || base.equals("trapped_chest")) {
                        tpl.getChestOffsets().add(new int[]{x, y, z});
                    } else if (base.equals("barrel")) {
                        tpl.getBarrelOffsets().add(new int[]{x, y, z});
                    } else if (base.endsWith("shulker_box")) {
                        tpl.getShulkerOffsets().add(new int[]{x, y, z});
                    }
                }
            }
        }

        // Scan genuine 2-high walkable interior floor spaces (under ceiling for houses!)
        List<int[]> roofedSpots = new ArrayList<>();
        List<int[]> anyFloorSpots = new ArrayList<>();
        for (int y = 1; y < sy - 1; y++) {
            for (int z = 1; z < sz - 1; z++) {
                for (int x = 1; x < sx - 1; x++) {
                    if (isSolidFloor(tpl.getBlock(x, y - 1, z))
                            && isPassable(tpl.getBlock(x, y, z))
                            && isPassable(tpl.getBlock(x, y + 1, z))) {
                        int[] cell = new int[]{x, y, z};
                        anyFloorSpots.add(cell);
                        if (hasCeilingAbove(tpl, x, y + 2, z)) {
                            roofedSpots.add(cell);
                        }
                    }
                }
            }
        }

        List<int[]> interior = !roofedSpots.isEmpty() ? roofedSpots : anyFloorSpots;
        interior.sort(Comparator.comparingInt(a -> distSq2D(a[0], a[2], midX, midZ) + Math.abs(a[1] - 2) * 4));

        if (category == DungeonCategory.SURFACE) {
            // Ensure House always has loot chests even if builder forgot to add chests in .schem
            if (tpl.getChestOffsets().isEmpty() && tpl.getBarrelOffsets().isEmpty()) {
                int placed = 0;
                for (int i = interior.size() - 1; i >= 0 && placed < 3; i -= Math.max(1, interior.size() / 4)) {
                    int[] c = interior.get(i);
                    tpl.setBlock(c[0], c[1], c[2], "minecraft:chest[facing=north]");
                    tpl.getChestOffsets().add(new int[]{c[0], c[1], c[2]});
                    placed++;
                }
            }
            if (!interior.isEmpty()) {
                int[] centerRoom = interior.get(0);
                tpl.setHologramOffset(centerRoom[0], centerRoom[1], centerRoom[2]);
                int[] entry = interior.get(Math.min(interior.size() - 1, Math.max(0, interior.size() / 4)));
                tpl.setEntranceOffset(entry[0], entry[1], entry[2]);

                int step = Math.max(1, interior.size() / 8);
                for (int i = 0; i < interior.size() && tpl.getMobSpawnOffsets().size() < 8; i += step) {
                    tpl.getMobSpawnOffsets().add(interior.get(i));
                }
            }
        } else {
            // UNDERGROUND: ensure at least 1 locked Boss Shulker on interior floor
            if (tpl.getShulkerOffsets().isEmpty()) {
                int[] center = !interior.isEmpty() ? interior.get(0) : new int[]{midX, Math.max(1, sy / 3), midZ};
                tpl.setBlock(center[0], center[1], center[2], "minecraft:purple_shulker_box[facing=up]");
                tpl.getShulkerOffsets().add(center);
            }
            int[] mainShulker = tpl.getShulkerOffsets().get(0);
            tpl.setHologramOffset(mainShulker[0], mainShulker[1], mainShulker[2]);

            List<int[]> around = new ArrayList<>();
            for (int[] c : interior) {
                int d2 = distSq2D(c[0], c[2], mainShulker[0], mainShulker[2]);
                if (d2 >= 4 && d2 <= 81) {
                    around.add(c);
                }
            }
            List<int[]> source = !around.isEmpty() ? around : interior;
            if (!source.isEmpty()) {
                tpl.setEntranceOffset(source.get(0)[0], source.get(0)[1], source.get(0)[2]);
                int step = Math.max(1, source.size() / 8);
                for (int i = 0; i < source.size() && tpl.getMobSpawnOffsets().size() < 8; i += step) {
                    tpl.getMobSpawnOffsets().add(source.get(i));
                }
            } else {
                tpl.setEntranceOffset(Math.max(1, mainShulker[0] - 2), mainShulker[1], mainShulker[2]);
                tpl.getMobSpawnOffsets().add(new int[]{Math.max(1, mainShulker[0] - 2), mainShulker[1], mainShulker[2]});
            }
        }
    }

    private static boolean hasCeilingAbove(DungeonTemplate tpl, int x, int startY, int z) {
        for (int y = startY; y < tpl.getSizeY(); y++) {
            if (!isPassable(tpl.getBlock(x, y, z))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPassable(String rawState) {
        String s = stripBlockProps(rawState);
        return s.equals("air") || s.equals("cave_air") || s.equals("void_air")
                || s.equals("structure_void") || s.equals("light")
                || s.contains("carpet") || s.contains("torch") || s.equals("soul_lantern") || s.equals("lantern");
    }

    private static boolean isSolidFloor(String rawState) {
        String s = stripBlockProps(rawState);
        if (isPassable(rawState) || s.equals("water") || s.equals("lava")
                || s.endsWith("chest") || s.equals("barrel") || s.endsWith("shulker_box")
                || s.contains("leaves") || s.contains("fence") || s.contains("pane")
                || s.contains("wall") || s.contains("chain") || s.equals("cobweb")) {
            return false;
        }
        return true;
    }

    private static int distSq2D(int x1, int z1, int x2, int z2) {
        int dx = x1 - x2, dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private static String stripBlockProps(String state) {
        String s = state;
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        int bracket = s.indexOf('[');
        if (bracket >= 0) s = s.substring(0, bracket);
        return s.toLowerCase(Locale.ROOT);
    }

    private static void writeShortTag(DataOutputStream out, String name, short val) throws IOException {
        out.writeByte(2);
        out.writeUTF(name);
        out.writeShort(val);
    }

    private static void writeIntTag(DataOutputStream out, String name, int val) throws IOException {
        out.writeByte(3);
        out.writeUTF(name);
        out.writeInt(val);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & -128) != 0) {
            out.write((v & 127) | 128);
            v >>>= 7;
        }
        out.write(v);
    }

    private static int readVarInt(byte[] src, int[] cursor) {
        int value = 0, shift = 0;
        while (cursor[0] < src.length) {
            byte b = src[cursor[0]++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return value;
    }

    private static int toShortInt(Object num) {
        return num instanceof Number n ? (n.shortValue() & 0xFFFF) : 0;
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int type = in.readByte() & 0xFF;
            if (type == 0) break;
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
        return map;
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                yield b;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                int elemType = in.readByte() & 0xFF;
                int len = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) list.add(readPayload(in, elemType));
                yield list;
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int[] arr = new int[in.readInt()];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
                yield arr;
            }
            case 12 -> {
                long[] arr = new long[in.readInt()];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
                yield arr;
            }
            default -> throw new IOException("Unsupported NBT tag id: " + type);
        };
    }
}
