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
import ru.elytrix.dungeons.model.CuboidRegion;

public final class TerrainSnapshotStore {

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
        int sx = norm.sizeX(), sy = norm.sizeY(), sz = norm.sizeZ();
        List<String> palette = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        int[] raw = new int[sx * sy * sz];

        int idx = 0;
        for (int y = norm.minY(); y <= norm.maxY(); y++) {
            for (int z = norm.minZ(); z <= norm.maxZ(); z++) {
                for (int x = norm.minX(); x <= norm.maxX(); x++) {
                    Block b = world.getBlockAt(x, y, z);
                    String state = b.getBlockData().getAsString(true);
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
            out.writeUTF(norm.worldName());
            out.writeInt(norm.minX());
            out.writeInt(norm.minY());
            out.writeInt(norm.minZ());
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

    public void restoreWorldSnapshotInstant(String dungeonId) throws IOException {
        File inFile = snapshotFile(dungeonId);
        if (!inFile.isFile()) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(inFile))))) {
            String worldName = in.readUTF();
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return;
            }
            int minX = in.readInt(), minY = in.readInt(), minZ = in.readInt();
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            int pCount = in.readInt();
            BlockData[] datas = new BlockData[pCount];
            for (int i = 0; i < pCount; i++) {
                String state = in.readUTF();
                try {
                    datas[i] = Bukkit.createBlockData(state);
                } catch (IllegalArgumentException ex) {
                    datas[i] = Bukkit.createBlockData(Material.AIR);
                }
            }
            int runCount = in.readInt();
            int cursor = 0;
            int total = sx * sy * sz;
            for (int r = 0; r < runCount; r++) {
                int pid = in.readInt();
                int len = in.readInt();
                BlockData bd = pid >= 0 && pid < datas.length ? datas[pid] : Bukkit.createBlockData(Material.AIR);
                for (int k = 0; k < len && cursor < total; k++, cursor++) {
                    int y = cursor / (sx * sz);
                    int rem = cursor % (sx * sz);
                    int z = rem / sx;
                    int x = rem % sx;
                    Block b = world.getBlockAt(minX + x, minY + y, minZ + z);
                    if (b.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                        holder.getInventory().clear();
                    }
                    b.setBlockData(bd, false);
                }
            }
        }
    }
}
