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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonTemplate;

public final class SpongeSchematicCodec {

    private SpongeSchematicCodec() {}

    @SuppressWarnings("unchecked")
    public static DungeonTemplate readSchem(
            File file,
            DungeonCategory category,
            int targetSurfaceShulkers,
            int minSkyShards,
            Set<String> shardMaterialNames
    ) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            byte rootType = in.readByte();
            if (rootType != 10) {
                throw new IOException("Invalid NBT root in schematic: " + file.getName());
            }
            readString(in);
            Map<String, Object> root = readCompound(in);
            if (root.containsKey("Schematic") && root.get("Schematic") instanceof Map<?, ?> nested) {
                root = (Map<String, Object>) nested;
            }

            int width = ((Number) root.getOrDefault("Width", 0)).intValue() & 0xFFFF;
            int height = ((Number) root.getOrDefault("Height", 0)).intValue() & 0xFFFF;
            int length = ((Number) root.getOrDefault("Length", 0)).intValue() & 0xFFFF;
            if (width <= 0 || height <= 0 || length <= 0) {
                throw new IOException("Empty dimensions in schematic: " + file.getName());
            }

            Map<String, Object> paletteMap = null;
            byte[] blockBytes = null;
            if (root.get("Blocks") instanceof Map<?, ?> blocksSection) {
                Map<String, Object> bs = (Map<String, Object>) blocksSection;
                if (bs.get("Palette") instanceof Map<?, ?> pm) paletteMap = (Map<String, Object>) pm;
                if (bs.get("Data") instanceof byte[] b) blockBytes = b;
            } else {
                if (root.get("Palette") instanceof Map<?, ?> pm) paletteMap = (Map<String, Object>) pm;
                if (root.get("BlockData") instanceof byte[] b) blockBytes = b;
            }

            String id = category.name().toLowerCase(Locale.ROOT) + "_"
                    + file.getName().replaceAll("\\.schem(atic)?$", "").toLowerCase(Locale.ROOT);
            String title = category.getTitle() + " данж";
            DungeonTemplate tpl = new DungeonTemplate(id, title, category, width, height, length);
            if (paletteMap == null || blockBytes == null) {
                return tpl;
            }

            Map<Integer, String> idToState = new HashMap<>();
            for (Map.Entry<String, Object> e : paletteMap.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    idToState.put(n.intValue(), normalizeBlockState(e.getKey()));
                }
            }

            int cursor = 0;
            int total = width * height * length;
            for (int index = 0; index < total && cursor < blockBytes.length; index++) {
                int value = 0;
                int shift = 0;
                while (cursor < blockBytes.length) {
                    int b = blockBytes[cursor++] & 0xFF;
                    value |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) break;
                    shift += 7;
                }
                int y = index / (width * length);
                int rem = index % (width * length);
                int z = rem / width;
                int x = rem % width;
                String state = idToState.getOrDefault(value, "minecraft:air");
                tpl.setBlock(x, y, z, state);
            }

            enrichAndScanTemplate(tpl, targetSurfaceShulkers, minSkyShards, shardMaterialNames);
            return tpl;
        }
    }

    public static void writeSchem(DungeonTemplate tpl, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        int width = tpl.getSizeX(), height = tpl.getSizeY(), length = tpl.getSizeZ();
        List<String> pal = tpl.getPalette();

        ByteArrayOutputStream blockBuf = new ByteArrayOutputStream(width * height * length);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    writeVarInt(blockBuf, tpl.getPaletteIdAt(x, y, z));
                }
            }
        }
        byte[] rawBlockData = blockBuf.toByteArray();

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            out.writeByte(10);
            writeString(out, "Schematic");

            writeNamedInt(out, "Version", 2);
            writeNamedInt(out, "DataVersion", 4189);
            writeNamedShort(out, "Width", (short) width);
            writeNamedShort(out, "Height", (short) height);
            writeNamedShort(out, "Length", (short) length);
            writeNamedInt(out, "PaletteMax", pal.size());

            out.writeByte(10);
            writeString(out, "Palette");
            for (int i = 0; i < pal.size(); i++) {
                writeNamedInt(out, pal.get(i), i);
            }
            out.writeByte(0);

            out.writeByte(7);
            writeString(out, "BlockData");
            out.writeInt(rawBlockData.length);
            out.write(rawBlockData);

            out.writeByte(0);
        }
    }

    public static void enrichAndScanTemplate(
            DungeonTemplate tpl,
            int targetSurfaceShulkers,
            int minSkyShards,
            Set<String> shardMaterialNames
    ) {
        tpl.getChestOffsets().clear();
        tpl.getBarrelOffsets().clear();
        tpl.getShulkerOffsets().clear();
        tpl.getAngelicShardOffsets().clear();
        tpl.getMobSpawnOffsets().clear();

        List<int[]> openFloorSpots = new ArrayList<>();
        int cx = tpl.getSizeX() / 2;
        int cz = tpl.getSizeZ() / 2;

        for (int y = 0; y < tpl.getSizeY(); y++) {
            for (int z = 0; z < tpl.getSizeZ(); z++) {
                for (int x = 0; x < tpl.getSizeX(); x++) {
                    String st = tpl.getBlock(x, y, z);
                    String matUpper = extractMaterialUpper(st);
                    if (matUpper.equals("CHEST") || matUpper.equals("TRAPPED_CHEST")) {
                        tpl.getChestOffsets().add(new int[]{x, y, z});
                    } else if (matUpper.equals("BARREL")) {
                        tpl.getBarrelOffsets().add(new int[]{x, y, z});
                    } else if (matUpper.endsWith("SHULKER_BOX")) {
                        tpl.getShulkerOffsets().add(new int[]{x, y, z});
                    } else if (shardMaterialNames.contains(matUpper)) {
                        tpl.getAngelicShardOffsets().add(new int[]{x, y, z});
                    }

                    if (isAirState(st) && y >= 1 && y + 1 < tpl.getSizeY()
                            && !isAirOrFluid(tpl.getBlock(x, y - 1, z))
                            && isAirState(tpl.getBlock(x, y + 1, z))) {
                        openFloorSpots.add(new int[]{x, y, z});
                    }
                }
            }
        }

        openFloorSpots.sort((a, b) -> Double.compare(
                Math.hypot(a[0] - cx, a[2] - cz),
                Math.hypot(b[0] - cx, b[2] - cz)
        ));

        // Ensure required interactive objects exist even if user dropped an unmodified downloaded .schem
        if (tpl.getCategory() == DungeonCategory.SURFACE) {
            int idx = 0;
            while (tpl.getShulkerOffsets().size() < targetSurfaceShulkers && idx < openFloorSpots.size()) {
                int[] spot = openFloorSpots.get(idx++);
                if (tooClose(spot, tpl.getShulkerOffsets(), 2.2)) continue;
                tpl.setBlock(spot[0], spot[1], spot[2], "minecraft:purple_shulker_box[facing=up]");
                tpl.getShulkerOffsets().add(new int[]{spot[0], spot[1], spot[2]});
            }
        } else if (tpl.getCategory() == DungeonCategory.UNDERGROUND) {
            if (tpl.getShulkerOffsets().isEmpty() && !openFloorSpots.isEmpty()) {
                int[] center = openFloorSpots.get(0);
                tpl.setBlock(center[0], center[1], center[2], "minecraft:purple_shulker_box[facing=up]");
                tpl.getShulkerOffsets().add(new int[]{center[0], center[1], center[2]});
            }
        } else if (tpl.getCategory() == DungeonCategory.SKY) {
            int idx = 0;
            while (tpl.getAngelicShardOffsets().size() < minSkyShards && idx < openFloorSpots.size()) {
                int[] spot = openFloorSpots.get(idx++);
                if (tooClose(spot, tpl.getAngelicShardOffsets(), 3.0)) continue;
                tpl.setBlock(spot[0], spot[1], spot[2], "minecraft:amethyst_cluster[facing=up]");
                tpl.getAngelicShardOffsets().add(new int[]{spot[0], spot[1], spot[2]});
            }
        }

        // Choose safe entrance + mob spawns + hologram anchor
        for (int[] sp : openFloorSpots) {
            if (!isAirState(tpl.getBlock(sp[0], sp[1], sp[2]))) continue;
            if (tpl.getMobSpawnOffsets().size() < 8 && !tooClose(sp, tpl.getMobSpawnOffsets(), 2.5)) {
                tpl.getMobSpawnOffsets().add(new int[]{sp[0], sp[1], sp[2]});
            }
        }

        for (int i = openFloorSpots.size() - 1; i >= 0; i--) {
            int[] sp = openFloorSpots.get(i);
            if (isAirState(tpl.getBlock(sp[0], sp[1], sp[2]))) {
                tpl.setEntranceOffset(sp[0], sp[1], sp[2]);
                break;
            }
        }

        if (!tpl.getShulkerOffsets().isEmpty()) {
            int[] s = tpl.getShulkerOffsets().get(0);
            tpl.setHologramAnchorOffset(s[0], s[1], s[2]);
        } else if (!openFloorSpots.isEmpty()) {
            int[] s = openFloorSpots.get(0);
            tpl.setHologramAnchorOffset(s[0], s[1], s[2]);
        }
    }

    private static boolean tooClose(int[] pt, List<int[]> others, double dist) {
        for (int[] o : others) {
            double dx = pt[0] - o[0], dy = pt[1] - o[1], dz = pt[2] - o[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < dist) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeBlockState(String state) {
        if (state == null || state.isEmpty()) return "minecraft:air";
        if (state.startsWith("minecraft:chain[")) {
            return "minecraft:iron_chain[" + state.substring("minecraft:chain[".length());
        }
        if (state.equals("minecraft:chain")) {
            return "minecraft:iron_chain";
        }
        return state.contains(":") ? state : "minecraft:" + state;
    }

    private static String extractMaterialUpper(String state) {
        String s = state.contains(":") ? state.substring(state.indexOf(':') + 1) : state;
        int br = s.indexOf('[');
        return (br >= 0 ? s.substring(0, br) : s).toUpperCase(Locale.ROOT);
    }

    private static boolean isAirState(String s) {
        return s.equals("minecraft:air") || s.equals("minecraft:cave_air") || s.equals("minecraft:void_air");
    }

    private static boolean isAirOrFluid(String s) {
        return isAirState(s) || s.startsWith("minecraft:water") || s.startsWith("minecraft:lava");
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    private static void writeNamedInt(DataOutputStream out, String name, int val) throws IOException {
        out.writeByte(3);
        writeString(out, name);
        out.writeInt(val);
    }

    private static void writeNamedShort(DataOutputStream out, String name, short val) throws IOException {
        out.writeByte(2);
        writeString(out, name);
        out.writeShort(val);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            byte tagType = in.readByte();
            if (tagType == 0) break;
            String name = readString(in);
            map.put(name, readPayload(in, tagType));
        }
        return map;
    }

    private static Object readPayload(DataInputStream in, byte tagType) throws IOException {
        return switch (tagType) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                int len = in.readInt();
                byte[] arr = new byte[len];
                in.readFully(arr);
                yield arr;
            }
            case 8 -> readString(in);
            case 9 -> {
                byte elemType = in.readByte();
                int count = in.readInt();
                Object[] list = new Object[Math.max(0, count)];
                for (int i = 0; i < count; i++) list[i] = readPayload(in, elemType);
                yield list;
            }
            case 10 -> readCompound(in);
            case 11 -> {
                int len = in.readInt();
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) arr[i] = in.readInt();
                yield arr;
            }
            case 12 -> {
                int len = in.readInt();
                long[] arr = new int[0].length == 0 ? new long[len] : new long[0];
                for (int i = 0; i < len; i++) arr[i] = in.readLong();
                yield arr;
            }
            default -> throw new IOException("Unknown NBT tag id: " + tagType);
        };
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
