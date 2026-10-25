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
                        // Any ordinary colored shulker in the .schem is a spawn/loot marker.
                        // Keep its exact coordinates; runtime replaces/fills it with the configured loot shulker.
                        tpl.getShulkerOffsets().add(new int[]{x, y, z});
                    }
                }
            }
        }

        // Flood from the schematic edges so roofed porches and balconies are not mistaken for rooms.
        boolean[] exterior = findExteriorPassableCells(tpl);
        int layer = sx * sz;
        List<int[]> enclosedFloorSpots = new ArrayList<>();
        List<int[]> roofedSpots = new ArrayList<>();
        List<int[]> anyFloorSpots = new ArrayList<>();
        List<int[]> connectedFloorSpots = new ArrayList<>();
        List<int[]> corridorSpots = new ArrayList<>();
        List<int[]> hologramSpots = new ArrayList<>();
        for (int y = 1; y < sy - 1; y++) {
            for (int z = 1; z < sz - 1; z++) {
                for (int x = 1; x < sx - 1; x++) {
                    if (!isSolidFloor(tpl.getBlock(x, y - 1, z))
                            || !isPassable(tpl.getBlock(x, y, z))
                            || !isPassable(tpl.getBlock(x, y + 1, z))) {
                        continue;
                    }

                    int[] cell = new int[]{x, y, z};
                    anyFloorSpots.add(cell);
                    int index = indexOf(x, y, z, sx, sz);
                    boolean enclosed = !exterior[index] && !exterior[index + layer];
                    boolean connectedToEntrance = exterior[index] && exterior[index + layer];
                    boolean roofed = hasCeilingAbove(tpl, x, y + 2, z);
                    if (enclosed) {
                        enclosedFloorSpots.add(cell);
                    }
                    if (roofed) {
                        roofedSpots.add(cell);
                    }
                    if (connectedToEntrance) {
                        connectedFloorSpots.add(cell);
                        if (roofed) corridorSpots.add(cell);
                    }
                    if (isPassable(tpl.getBlock(x, y + 2, z)) && enclosed) {
                        hologramSpots.add(cell);
                    }
                }
            }
        }

        List<int[]> interior;
        if (!enclosedFloorSpots.isEmpty()) {
            interior = enclosedFloorSpots;
        } else if (!roofedSpots.isEmpty()) {
            interior = roofedSpots;
        } else {
            interior = anyFloorSpots;
        }

        // If a house has an open entrance and no fully sealed room, bias ghost markers toward
        // the central footprint instead of exposed outer decks and verandas.
        if (category == DungeonCategory.SURFACE && enclosedFloorSpots.isEmpty() && !interior.isEmpty()) {
            int centerRadius = Math.max(4, Math.min(sx, sz) / 3);
            int maxDistance = centerRadius * centerRadius;
            List<int[]> central = new ArrayList<>();
            for (int[] spot : interior) {
                if (distSq2D(spot[0], spot[2], midX, midZ) <= maxDistance) {
                    central.add(spot);
                }
            }
            if (!central.isEmpty()) {
                interior = central;
            }
        }

        int firstFloorY = sy;
        for (int[] spot : interior) {
            firstFloorY = Math.min(firstFloorY, spot[1]);
        }
        final int floorY = firstFloorY;
        interior.sort(Comparator.comparingInt(a -> Math.abs(a[1] - floorY) * 10000
                + distSq2D(a[0], a[2], midX, midZ)));
        hologramSpots.removeIf(spot -> category == DungeonCategory.SURFACE
                && enclosedFloorSpots.isEmpty()
                && distSq2D(spot[0], spot[2], midX, midZ) > Math.max(4, Math.min(sx, sz) / 3)
                        * Math.max(4, Math.min(sx, sz) / 3));
        List<int[]> holoCandidates = hologramSpots.isEmpty() ? interior : hologramSpots;

        if (category == DungeonCategory.SURFACE) {
            // Container positions are intentional markers in the uploaded .schem: never invent chests.
            if (!interior.isEmpty()) {
                int[] centerRoom = selectBestHologramSpot(tpl, holoCandidates, midX, midZ);
                if (centerRoom == null) centerRoom = selectBestHologramSpot(tpl, interior, midX, midZ);
                if (centerRoom == null) centerRoom = findOpenHologramSpot(tpl, midX, midZ);
                if (centerRoom != null) tpl.setHologramOffset(centerRoom[0], centerRoom[1], centerRoom[2]);
            } else {
                int[] openSpot = findOpenHologramSpot(tpl, midX, midZ);
                if (openSpot != null) tpl.setHologramOffset(openSpot[0], openSpot[1], openSpot[2]);
            }

            // Teleports use the first-floor corridor; ghosts can spawn across the first two
            // connected, roofed floor bands. Sealed bedrooms are intentionally excluded.
            List<int[]> corridorNetwork = preferCentralSpots(corridorSpots, sx, sz, midX, midZ);
            if (corridorNetwork.isEmpty()) {
                corridorNetwork = preferCentralSpots(connectedFloorSpots, sx, sz, midX, midZ);
            }
            if (corridorNetwork.isEmpty()) corridorNetwork = interior;
            List<int[]> groundFloorNetwork = lowestFloorBand(corridorNetwork, 1);
            int[] entry = selectEntranceSpot(groundFloorNetwork, sx, sz, midX, midZ);
            if (entry != null) {
                tpl.setEntranceOffset(entry[0], entry[1], entry[2]);
                addDistributedMobSpawnOffsets(tpl, firstTwoFloorBands(corridorNetwork), 64,
                        entry[0], entry[2]);
            }
        } else {
            // An ordinary shulker block in the uploaded .schem marks the exact loot location.
            // If none was supplied, place one on an interior floor as before.
            if (tpl.getShulkerOffsets().isEmpty()) {
                int[] center = !interior.isEmpty() ? interior.get(0) : new int[]{midX, Math.max(1, sy / 3), midZ};
                tpl.setBlock(center[0], center[1], center[2], "minecraft:purple_shulker_box[facing=up]");
                tpl.getShulkerOffsets().add(center);
            }
            int[] mainShulker = tpl.getShulkerOffsets().get(0);
            int[] hologramSpot = selectBestHologramSpot(tpl, holoCandidates, mainShulker[0], mainShulker[2]);
            if (hologramSpot == null) {
                hologramSpot = selectBestHologramSpot(tpl, interior, midX, midZ);
            }
            if (hologramSpot == null) {
                hologramSpot = findOpenHologramSpot(tpl, midX, midZ);
            }
            if (hologramSpot != null) {
                tpl.setHologramOffset(hologramSpot[0], hologramSpot[1], hologramSpot[2]);
            }

            List<int[]> around = new ArrayList<>();
            List<int[]> corridorAround = new ArrayList<>();
            for (int[] c : interior) {
                int d2 = distSq2D(c[0], c[2], mainShulker[0], mainShulker[2]);
                if (d2 >= 4 && d2 <= 81) around.add(c);
            }
            for (int[] c : corridorSpots) {
                int d2 = distSq2D(c[0], c[2], mainShulker[0], mainShulker[2]);
                if (d2 <= 144) corridorAround.add(c);
            }
            List<int[]> source = !corridorAround.isEmpty() ? corridorAround
                    : (!around.isEmpty() ? around : interior);

            List<int[]> entranceNetwork = !corridorSpots.isEmpty() ? corridorSpots : connectedFloorSpots;
            if (entranceNetwork.isEmpty()) entranceNetwork = source;
            int[] entry = selectEntranceSpot(lowestFloorBand(entranceNetwork, 1), sx, sz, midX, midZ);
            if (entry == null && !source.isEmpty()) entry = selectEntranceSpot(source, sx, sz, midX, midZ);
            if (entry != null) {
                tpl.setEntranceOffset(entry[0], entry[1], entry[2]);
                addDistributedMobSpawnOffsets(tpl, source, 8, entry[0], entry[2]);
            } else {
                int[] fallback = new int[]{Math.max(1, mainShulker[0] - 2), mainShulker[1], mainShulker[2]};
                tpl.setEntranceOffset(fallback[0], fallback[1], fallback[2]);
                tpl.getMobSpawnOffsets().add(fallback);
            }
        }
    }

    private static List<int[]> preferCentralSpots(
            List<int[]> candidates, int sizeX, int sizeZ, int centerX, int centerZ
    ) {
        if (candidates.isEmpty()) return new ArrayList<>();
        int radius = Math.max(4, Math.min(sizeX, sizeZ) / 3);
        int radiusSquared = radius * radius;
        List<int[]> central = new ArrayList<>();
        for (int[] candidate : candidates) {
            if (distSq2D(candidate[0], candidate[2], centerX, centerZ) <= radiusSquared) {
                central.add(candidate);
            }
        }
        return central.isEmpty() ? new ArrayList<>(candidates) : central;
    }

    private static List<int[]> lowestFloorBand(List<int[]> candidates, int yAllowance) {
        if (candidates.isEmpty()) return new ArrayList<>();
        int lowestY = Integer.MAX_VALUE;
        for (int[] candidate : candidates) lowestY = Math.min(lowestY, candidate[1]);
        List<int[]> lowest = new ArrayList<>();
        for (int[] candidate : candidates) {
            if (candidate[1] <= lowestY + Math.max(0, yAllowance)) lowest.add(candidate);
        }
        return lowest;
    }

    private static List<int[]> firstTwoFloorBands(List<int[]> candidates) {
        if (candidates.isEmpty()) return new ArrayList<>();
        int firstFloorY = Integer.MAX_VALUE;
        for (int[] candidate : candidates) firstFloorY = Math.min(firstFloorY, candidate[1]);
        List<int[]> floors = new ArrayList<>();
        for (int[] candidate : candidates) {
            if (candidate[1] <= firstFloorY + 1) floors.add(candidate);
        }
        int secondFloorY = Integer.MAX_VALUE;
        for (int[] candidate : candidates) {
            if (candidate[1] >= firstFloorY + 3) secondFloorY = Math.min(secondFloorY, candidate[1]);
        }
        if (secondFloorY == Integer.MAX_VALUE) return floors;
        for (int[] candidate : candidates) {
            if (candidate[1] >= secondFloorY && candidate[1] <= secondFloorY + 1) floors.add(candidate);
        }
        return floors;
    }

    private static int[] selectEntranceSpot(
            List<int[]> candidates, int sizeX, int sizeZ, int centerX, int centerZ
    ) {
        if (candidates.isEmpty()) return null;
        int lowestY = Integer.MAX_VALUE;
        for (int[] candidate : candidates) lowestY = Math.min(lowestY, candidate[1]);

        int[] best = null;
        long bestScore = Long.MAX_VALUE;
        for (int[] candidate : candidates) {
            if (candidate[1] > lowestY + 1) continue;
            int edgeDepth = Math.min(Math.min(candidate[0], sizeX - 1 - candidate[0]),
                    Math.min(candidate[2], sizeZ - 1 - candidate[2]));
            long centerDistance = distSq2D(candidate[0], candidate[2], centerX, centerZ);
            long score = (long) edgeDepth * 1_000_000L + centerDistance;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static int[] selectBestHologramSpot(
            DungeonTemplate tpl, List<int[]> candidates, int referenceX, int referenceZ
    ) {
        int[] best = null;
        int bestClearance = Integer.MIN_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (int[] candidate : candidates) {
            if (!isPassable(tpl.getBlock(candidate[0], candidate[1], candidate[2]))
                    || !isPassable(tpl.getBlock(candidate[0], candidate[1] + 1, candidate[2]))
                    || !isPassable(tpl.getBlock(candidate[0], candidate[1] + 2, candidate[2]))) {
                continue;
            }
            int clearance = hologramClearanceScore(tpl, candidate);
            int distance = distSq2D(candidate[0], candidate[2], referenceX, referenceZ);
            if (clearance > bestClearance || clearance == bestClearance && distance < bestDistance) {
                best = candidate;
                bestClearance = clearance;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int[] findOpenHologramSpot(DungeonTemplate tpl, int referenceX, int referenceZ) {
        List<int[]> candidates = new ArrayList<>();
        for (int y = 1; y < tpl.getSizeY() - 2; y++) {
            for (int z = 1; z < tpl.getSizeZ() - 1; z++) {
                for (int x = 1; x < tpl.getSizeX() - 1; x++) {
                    if (isPassable(tpl.getBlock(x, y, z))
                            && isPassable(tpl.getBlock(x, y + 1, z))
                            && isPassable(tpl.getBlock(x, y + 2, z))) {
                        candidates.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return selectBestHologramSpot(tpl, candidates, referenceX, referenceZ);
    }

    private static int hologramClearanceScore(DungeonTemplate tpl, int[] spot) {
        int score = 0;
        for (int y = spot[1]; y < Math.min(tpl.getSizeY(), spot[1] + 5); y++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = spot[0] + dx, z = spot[2] + dz;
                    if (x >= 0 && x < tpl.getSizeX() && z >= 0 && z < tpl.getSizeZ()
                            && isPassable(tpl.getBlock(x, y, z))) {
                        score++;
                    }
                }
            }
        }
        return score;
    }

    private static void addDistributedMobSpawnOffsets(
            DungeonTemplate tpl, List<int[]> candidates, int maxSpawns, int entranceX, int entranceZ
    ) {
        if (candidates.isEmpty()) return;

        List<int[]> separatedFromEntrance = new ArrayList<>();
        for (int[] candidate : candidates) {
            if (distSq2D(candidate[0], candidate[2], entranceX, entranceZ) >= 9) {
                separatedFromEntrance.add(candidate);
            }
        }
        List<int[]> remaining = new ArrayList<>(separatedFromEntrance.isEmpty() ? candidates : separatedFromEntrance);
        List<int[]> selected = new ArrayList<>();
        int centerX = tpl.getSizeX() / 2, centerZ = tpl.getSizeZ() / 2;

        while (!remaining.isEmpty() && selected.size() < maxSpawns) {
            int bestIndex = 0;
            int bestDistance = Integer.MIN_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                int[] candidate = remaining.get(i);
                int distance;
                if (selected.isEmpty()) {
                    distance = distSq2D(candidate[0], candidate[2], entranceX, entranceZ);
                } else {
                    distance = Integer.MAX_VALUE;
                    for (int[] existing : selected) {
                        int dx = candidate[0] - existing[0];
                        int dy = candidate[1] - existing[1];
                        int dz = candidate[2] - existing[2];
                        distance = Math.min(distance, dx * dx + dy * dy * 2 + dz * dz);
                    }
                }
                if (distance > bestDistance || distance == bestDistance
                        && distSq2D(candidate[0], candidate[2], centerX, centerZ)
                        < distSq2D(remaining.get(bestIndex)[0], remaining.get(bestIndex)[2], centerX, centerZ)) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }
            selected.add(remaining.remove(bestIndex));
        }
        tpl.getMobSpawnOffsets().addAll(selected);
    }

    private static boolean[] findExteriorPassableCells(DungeonTemplate tpl) {
        int sx = tpl.getSizeX(), sy = tpl.getSizeY(), sz = tpl.getSizeZ();
        int volume = sx * sy * sz;
        boolean[] exterior = new boolean[volume];
        int[] queue = new int[volume];
        int tail = 0;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (x != 0 && x != sx - 1 && y != 0 && y != sy - 1 && z != 0 && z != sz - 1) {
                        continue;
                    }
                    tail = enqueuePassable(tpl, exterior, queue, tail, x, y, z);
                }
            }
        }

        int head = 0;
        while (head < tail) {
            int index = queue[head++];
            int x = index % sx;
            int z = (index / sx) % sz;
            int y = index / (sx * sz);
            tail = enqueuePassable(tpl, exterior, queue, tail, x - 1, y, z);
            tail = enqueuePassable(tpl, exterior, queue, tail, x + 1, y, z);
            tail = enqueuePassable(tpl, exterior, queue, tail, x, y - 1, z);
            tail = enqueuePassable(tpl, exterior, queue, tail, x, y + 1, z);
            tail = enqueuePassable(tpl, exterior, queue, tail, x, y, z - 1);
            tail = enqueuePassable(tpl, exterior, queue, tail, x, y, z + 1);
        }
        return exterior;
    }

    private static int enqueuePassable(
            DungeonTemplate tpl, boolean[] exterior, int[] queue, int tail, int x, int y, int z
    ) {
        int sx = tpl.getSizeX(), sy = tpl.getSizeY(), sz = tpl.getSizeZ();
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
            return tail;
        }
        int index = indexOf(x, y, z, sx, sz);
        if (!exterior[index] && isPassable(tpl.getBlock(x, y, z))) {
            exterior[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int indexOf(int x, int y, int z, int sx, int sz) {
        return (y * sz + z) * sx + x;
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
