package ru.elytrix.dungeons.structure;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.InventoryHolder;
import ru.elytrix.dungeons.model.CuboidRegion;

public final class TerrainSnapshotStore {

    public record BlockRestoreDiff(int x, int y, int z, BlockData data) {}

    private final File folder;

    public TerrainSnapshotStore(File dataFolder) {
        this.folder = new File(dataFolder, "terrain-snapshots");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public File snapshotFile(String dungeonId) {
        return new File(folder, dungeonId.toLowerCase() + ".snap.gz");
    }

    public boolean hasSnapshot(String dungeonId) {
        return snapshotFile(dungeonId).isFile();
    }

    public void deleteSnapshot(String dungeonId) {
        File f = snapshotFile(dungeonId);
        if (f.exists()) {
            f.delete();
        }
    }

    public void saveWorldSnapshot(String dungeonId, World world, CuboidRegion box) throws IOException {
        CuboidRegion norm = box.normalized();
        int minY = Math.max(world.getMinHeight(), norm.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, norm.maxY());
        CuboidRegion clamped = new CuboidRegion(norm.worldName(), norm.minX(), minY, norm.minZ(), norm.maxX(), maxY, norm.maxZ());

        ensureChunksLoaded(world, clamped);

        int sx = clamped.sizeX(), sy = clamped.sizeY(), sz = clamped.sizeZ();
        List<String> palette = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        int[] raw = new int[sx * sy * sz];

        int idx = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = clamped.minZ(); z <= clamped.maxZ(); z++) {
                for (int x = clamped.minX(); x <= clamped.maxX(); x++) {
                    String state = world.getBlockAt(x, y, z).getBlockData().getAsString(true);
                    Integer pid = map.get(state);
                    if (pid == null) {
                        pid = palette.size();
                        palette.add(state);
                        map.put(state, pid);
                    }
                    raw[idx++] = pid;
                }
            }
        }

        File outFile = snapshotFile(dungeonId);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(outFile))))) {
            out.writeUTF(clamped.worldName());
            out.writeInt(clamped.minX());
            out.writeInt(minY);
            out.writeInt(clamped.minZ());
            out.writeInt(sx);
            out.writeInt(sy);
            out.writeInt(sz);
            out.writeInt(palette.size());
            for (String s : palette) {
                out.writeUTF(s);
            }
            List<int[]> runs = new ArrayList<>();
            int cur = raw[0], len = 1;
            for (int i = 1; i < raw.length; i++) {
                if (raw[i] == cur) {
                    len++;
                } else {
                    runs.add(new int[]{cur, len});
                    cur = raw[i];
                    len = 1;
                }
            }
            runs.add(new int[]{cur, len});
            out.writeInt(runs.size());
            for (int[] run : runs) {
                out.writeInt(run[0]);
                out.writeInt(run[1]);
            }
        }
    }

    /**
     * Collects ONLY blocks around & inside the dungeon whose current world state differs
     * from original natural worldgen, allowing zero-lag batched restoration on live servers!
     */
    public List<BlockRestoreDiff> collectRestoreDiffs(String dungeonId) throws IOException {
        return collectRestoreDiffs(dungeonId, false);
    }

    private List<BlockRestoreDiff> collectRestoreDiffs(String dungeonId, boolean strict) throws IOException {
        List<BlockRestoreDiff> diffs = new ArrayList<>();
        File inFile = snapshotFile(dungeonId);
        if (!inFile.isFile()) {
            if (strict) throw new IOException("Снимок рельефа не найден");
            return diffs;
        }

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(inFile))))) {
            String worldName = in.readUTF();
            World world = Bukkit.getWorld(worldName);
            if (world == null) return diffs;

            int minX = in.readInt(), minY = in.readInt(), minZ = in.readInt();
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            long totalLong = (long) sx * sy * sz;
            if (strict && (sx <= 0 || sy <= 0 || sz <= 0 || totalLong > Integer.MAX_VALUE)) {
                throw new IOException("Некорректные размеры снимка рельефа");
            }
            int total = (int) totalLong;
            ensureChunksLoaded(world, new CuboidRegion(worldName, minX, minY, minZ, minX + sx - 1, minY + sy - 1, minZ + sz - 1));

            int pCount = in.readInt();
            if (strict && (pCount <= 0 || pCount > total)) {
                throw new IOException("Некорректная палитра снимка рельефа");
            }
            BlockData[] datas = new BlockData[pCount];
            for (int i = 0; i < pCount; i++) {
                String state = in.readUTF();
                try {
                    datas[i] = Bukkit.createBlockData(state);
                } catch (IllegalArgumentException ex) {
                    if (strict) throw new IOException("Некорректный блок в снимке рельефа: " + state, ex);
                    datas[i] = Bukkit.createBlockData(Material.AIR);
                }
            }
            int runCount = in.readInt();
            if (strict && (runCount <= 0 || runCount > total)) {
                throw new IOException("Некорректные данные снимка рельефа");
            }
            int cursor = 0;
            for (int r = 0; r < runCount; r++) {
                int pid = in.readInt();
                int len = in.readInt();
                if (strict && (pid < 0 || pid >= datas.length || len <= 0 || (long) cursor + len > total)) {
                    throw new IOException("Повреждены последовательности блоков снимка рельефа");
                }
                BlockData bd = pid >= 0 && pid < datas.length ? datas[pid] : Bukkit.createBlockData(Material.AIR);
                for (int k = 0; k < len && cursor < total; k++, cursor++) {
                    int y = cursor / (sx * sz);
                    int rem = cursor % (sx * sz);
                    int z = rem / sx;
                    int x = rem % sx;
                    Block b = world.getBlockAt(minX + x, minY + y, minZ + z);
                    if (!b.getBlockData().equals(bd)) {
                        diffs.add(new BlockRestoreDiff(minX + x, minY + y, minZ + z, bd));
                    }
                }
            }
            if (strict && cursor != total) {
                throw new IOException("Снимок рельефа содержит не все блоки");
            }
        }
        return diffs;
    }

    /**
     * Ultra-fast instant diff restore used on plugin disable / hide / removal so the entire
     * dungeon AND any surrounding grief craters disappear cleanly without holding shutdown.
     */
    public void restoreWorldSnapshotInstant(String dungeonId) throws IOException {
        restoreWorldSnapshotInstant(dungeonId, null, false);
    }

    /**
     * Strict restore used before deleting a dungeon. Unlike best-effort shutdown/regen restores,
     * this fails if the snapshot is missing or belongs to a different world.
     */
    public boolean restoreWorldSnapshotInstant(String dungeonId, String expectedWorldName) throws IOException {
        return restoreWorldSnapshotInstant(dungeonId, expectedWorldName, true);
    }

    private boolean restoreWorldSnapshotInstant(
            String dungeonId, String expectedWorldName, boolean requireSnapshot
    ) throws IOException {
        File inFile = snapshotFile(dungeonId);
        if (!inFile.isFile()) {
            if (requireSnapshot) throw new IOException("Снимок рельефа не найден");
            return false;
        }

        String snapshotWorldName;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(inFile))))) {
            snapshotWorldName = in.readUTF();
        }
        if (expectedWorldName != null && !expectedWorldName.equals(snapshotWorldName)) {
            throw new IOException("Снимок относится к миру «" + snapshotWorldName
                    + "», а данж находится в мире «" + expectedWorldName + "»");
        }

        World world = Bukkit.getWorld(snapshotWorldName);
        if (world == null) {
            if (requireSnapshot) throw new IOException("Мир снимка «" + snapshotWorldName + "» не загружен");
            return false;
        }

        List<BlockRestoreDiff> diffs = collectRestoreDiffs(dungeonId, requireSnapshot);
        for (BlockRestoreDiff diff : diffs) {
            Block b = world.getBlockAt(diff.x(), diff.y(), diff.z());
            if (isContainerType(b.getType()) && b.getState() instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }
            b.setBlockData(diff.data(), false);
        }
        return true;
    }

    public static void ensureChunksLoaded(World world, CuboidRegion box) {
        int minCx = box.minX() >> 4, maxCx = box.maxX() >> 4;
        int minCz = box.minZ() >> 4, maxCz = box.maxZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.getChunkAt(cx, cz);
                }
            }
        }
    }

    private static boolean isContainerType(Material m) {
        String n = m.name();
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL || n.endsWith("SHULKER_BOX");
    }
}
