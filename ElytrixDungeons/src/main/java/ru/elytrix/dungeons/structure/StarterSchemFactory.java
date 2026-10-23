package ru.elytrix.dungeons.structure;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonTemplate;

public final class StarterSchemFactory {

    private StarterSchemFactory() {}

    public static void writeStarterSurfaceSchem(File outFile) throws IOException {
        int sx = 23, sy = 15, sz = 23;
        DungeonTemplate t = new DungeonTemplate("surface_starter", "Наземный данж", DungeonCategory.SURFACE, sx, sy, sz);
        Random rng = new Random(17171L);
        String[] stone = {
            "minecraft:stone_bricks",
            "minecraft:mossy_stone_bricks",
            "minecraft:cracked_stone_bricks",
            "minecraft:tuff_bricks",
            "minecraft:polished_tuff"
        };
        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");
        for (int x = 1; x <= sx - 2; x++) {
            for (int z = 1; z <= sz - 2; z++) {
                t.setBlock(x, 0, z, stone[rng.nextInt(stone.length)]);
                t.setBlock(x, 1, z, stone[rng.nextInt(stone.length)]);
                double d = Math.hypot(x - 11, z - 11);
                if (d <= 4.2 && (Math.abs(x - 11) <= 1 || Math.abs(z - 11) <= 1)) {
                    t.setBlock(x, 0, z, "minecraft:pearlescent_froglight[axis=y]");
                    t.setBlock(x, 1, z, "minecraft:magenta_stained_glass");
                }
            }
        }
        int[] pillars = {2, 7, 15, 20};
        for (int px : pillars) {
            for (int pz : pillars) {
                if (px > 2 && px < 20 && pz > 2 && pz < 20) continue;
                t.fill(px, 2, pz, px, 9, pz, "minecraft:chiseled_tuff_bricks");
                t.setBlock(px, 10, pz, "minecraft:mossy_stone_brick_wall");
                t.setBlock(px, 11, pz, "minecraft:lantern[hanging=false]");
            }
        }
        for (int layer = 0; layer < 4; layer++) {
            int lo = 2 + layer * 2, hi = 20 - layer * 2, ry = 10 + layer;
            for (int x = lo; x <= hi; x++) {
                for (int z = lo; z <= hi; z++) {
                    if (x == lo || x == hi || z == lo || z == hi || layer >= 2) {
                        t.setBlock(x, ry, z, Math.abs(x - 11) <= 1 && Math.abs(z - 11) <= 1 && layer == 3
                                ? "minecraft:tinted_glass" : stone[rng.nextInt(4)]);
                    }
                }
            }
        }
        // Ordinary chests + barrels around hall
        t.setBlock(4, 2, 4, "minecraft:chest[facing=south]");
        t.setBlock(18, 2, 4, "minecraft:chest[facing=south]");
        t.setBlock(4, 2, 18, "minecraft:chest[facing=north]");
        t.setBlock(18, 2, 18, "minecraft:chest[facing=north]");

        t.setBlock(4, 2, 11, "minecraft:barrel[facing=up]");
        t.setBlock(18, 2, 11, "minecraft:barrel[facing=up]");
        t.setBlock(11, 2, 4, "minecraft:barrel[facing=up]");

        // Exactly 3 Unique Shulkers around central shrine (1 Unique Key = 1 Shulker)
        t.setBlock(9, 2, 11, "minecraft:chiseled_tuff");
        t.setBlock(9, 3, 11, "minecraft:purple_shulker_box[facing=up]");
        t.setBlock(13, 2, 11, "minecraft:chiseled_tuff");
        t.setBlock(13, 3, 11, "minecraft:purple_shulker_box[facing=up]");
        t.setBlock(11, 2, 9, "minecraft:chiseled_tuff");
        t.setBlock(11, 3, 9, "minecraft:magenta_shulker_box[facing=up]");

        SpongeSchematicCodec.writeSchem(t, outFile);
    }

    public static void writeStarterUndergroundSchem(File outFile) throws IOException {
        int sx = 23, sy = 13, sz = 23;
        DungeonTemplate t = new DungeonTemplate("underground_starter", "Подземный данж", DungeonCategory.UNDERGROUND, sx, sy, sz);
        Random rng = new Random(28282L);
        String[] shell = {
            "minecraft:deepslate_tiles",
            "minecraft:deepslate_bricks",
            "minecraft:cracked_deepslate_bricks",
            "minecraft:sculk"
        };
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    boolean border = x <= 1 || x >= sx - 2 || z <= 1 || z >= sz - 2 || y <= 1 || y >= sy - 2;
                    t.setBlock(x, y, z, border ? shell[rng.nextInt(shell.length)] : "minecraft:air");
                }
            }
        }
        int[][] cols = {{6, 6}, {16, 6}, {6, 16}, {16, 16}};
        for (int[] c : cols) {
            t.fill(c[0], 2, c[1], c[0], sy - 3, c[1], "minecraft:polished_basalt[axis=y]");
            t.setBlock(c[0], sy - 3, c[1] + 1, "minecraft:iron_chain[axis=y]");
            t.setBlock(c[0], sy - 4, c[1] + 1, "minecraft:soul_lantern[hanging=true]");
        }
        t.fill(10, 1, 17, 12, 1, 19, "minecraft:water");

        // Side barrels + Central locked Underground Boss Shulker
        t.setBlock(3, 2, 8, "minecraft:barrel[facing=up]");
        t.setBlock(19, 2, 8, "minecraft:barrel[facing=up]");
        t.setBlock(11, 2, 11, "minecraft:chiseled_deepslate");
        t.setBlock(11, 3, 11, "minecraft:purple_shulker_box[facing=up]");

        SpongeSchematicCodec.writeSchem(t, outFile);
    }

    public static void writeStarterSkySchem(File outFile) throws IOException {
        int sx = 25, sy = 21, sz = 25;
        DungeonTemplate t = new DungeonTemplate("sky_starter", "Воздушный данж", DungeonCategory.SKY, sx, sy, sz);
        Random rng = new Random(39393L);
        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");

        int cx = 12, cz = 12, deckY = 8;
        double maxR = 10.6;
        for (int y = deckY; y >= 1; y--) {
            double radius = maxR * Math.pow((double) y / deckY, 0.62);
            for (int x = 1; x <= sx - 2; x++) {
                for (int z = 1; z <= sz - 2; z++) {
                    double wobble = Math.sin(x * 0.7) * 0.55 + Math.cos(z * 0.7) * 0.55;
                    if (Math.hypot(x - cx, z - cz) <= radius + wobble * 0.6) {
                        t.setBlock(x, y, z, y == deckY
                                ? ((x + z) % 2 == 0 ? "minecraft:quartz_bricks" : "minecraft:calcite")
                                : (rng.nextInt(5) == 0 ? "minecraft:amethyst_block" : "minecraft:tuff"));
                    }
                }
            }
        }
        for (int a = 0; a < 8; a++) {
            double ang = (Math.PI * 2.0 * a) / 8.0;
            int px = (int) Math.round(cx + Math.cos(ang) * 8.0);
            int pz = (int) Math.round(cz + Math.sin(ang) * 8.0);
            t.fill(px, 9, pz, px, 15, pz, "minecraft:quartz_pillar[axis=y]");
            t.setBlock(px, 16, pz, "minecraft:end_rod[facing=up]");
        }

        // 8 Angelic Shards on crystal pedestals across the sky island
        int[][] shards = {
            {cx - 4, cz - 4}, {cx + 4, cz - 4}, {cx - 4, cz + 4}, {cx + 4, cz + 4},
            {cx - 6, cz}, {cx + 6, cz}, {cx, cz - 6}, {cx, cz + 5}
        };
        for (int[] s : shards) {
            t.setBlock(s[0], 8, s[1], "minecraft:budding_amethyst");
            t.setBlock(s[0], 9, s[1], "minecraft:amethyst_cluster[facing=up]");
        }

        SpongeSchematicCodec.writeSchem(t, outFile);
    }
}
