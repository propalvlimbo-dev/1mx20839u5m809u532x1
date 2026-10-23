package ru.elytrix.dungeons.structure;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonTemplate;
import ru.elytrix.dungeons.model.TrialType;

public final class SpongeSchematicReader {

    private SpongeSchematicReader() {}

    @SuppressWarnings("unchecked")
    public static DungeonTemplate readSchem(File file, DungeonCategory defaultCategory, int defaultTier) throws IOException {
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

            String id = file.getName().replaceAll("\\.schem(atic)?$", "").toLowerCase();
            DungeonTemplate tpl = new DungeonTemplate(id, id, defaultCategory, defaultTier, TrialType.WAVES, width, height, length);
            if (paletteMap == null || blockBytes == null) {
                return tpl;
            }

            Map<Integer, String> idToState = new HashMap<>();
            for (Map.Entry<String, Object> e : paletteMap.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    idToState.put(n.intValue(), e.getKey());
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
                if (state.startsWith("minecraft:chest") || state.startsWith("minecraft:barrel")) {
                    tpl.getExplorerChestOffsets().add(new int[]{x, y, z});
                } else if (state.startsWith("minecraft:vault") || state.startsWith("minecraft:respawn_anchor")) {
                    tpl.setAltarOffset(x, y, z);
                }
            }
            return tpl;
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            byte tagType = in.readByte();
            if (tagType == 0) {
                break;
            }
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
                for (int i = 0; i < count; i++) {
                    list[i] = readPayload(in, elemType);
                }
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
                long[] arr = new long[len];
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
