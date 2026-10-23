package ru.elytrix.dungeons.structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonTemplate;
import ru.elytrix.dungeons.model.TrialType;

public final class BuiltinArchitect {

    private BuiltinArchitect() {}

    public static Map<String, DungeonTemplate> buildAll() {
        Map<String, DungeonTemplate> out = new LinkedHashMap<>();
        out.put("surface_ruin_temple", buildSurfaceRuinTemple());
        out.put("surface_blackstone_bastion", buildSurfaceBastion());
        out.put("surface_purpur_keep", buildSurfacePurpurKeep());
        out.put("underground_rift_crypt", buildUndergroundRiftCrypt());
        out.put("underground_sculk_sanctum", buildUndergroundSculkSanctum());
        out.put("sky_astral_island", buildSkyAstralIsland());
        out.put("sky_elytrix_citadel", buildSkyElytrixCitadel());
        return out;
    }

    private static DungeonTemplate buildSurfaceRuinTemple() {
        int sx = 21, sy = 14, sz = 21;
        DungeonTemplate t = new DungeonTemplate(
                "surface_ruin_temple",
                "Храм Древних Руин",
                DungeonCategory.SURFACE,
                1,
                TrialType.WAVES,
                sx, sy, sz
        );
        Random rng = new Random(10101L);
        String[] stoneMix = {
            "minecraft:stone_bricks",
            "minecraft:mossy_stone_bricks",
            "minecraft:cracked_stone_bricks",
            "minecraft:tuff_bricks",
            "minecraft:polished_andesite"
        };

        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");
        for (int x = 1; x <= sx - 2; x++) {
            for (int z = 1; z <= sz - 2; z++) {
                t.setBlock(x, 0, z, stoneMix[rng.nextInt(stoneMix.length)]);
                t.setBlock(x, 1, z, ((x + z) % 3 == 0) ? "minecraft:polished_tuff" : stoneMix[rng.nextInt(stoneMix.length)]);
            }
        }
        // Central glowing floor sigil
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    t.setBlock(10 + dx, 0, 10 + dz, "minecraft:sea_lantern");
                    t.setBlock(10 + dx, 1, 10 + dz, "minecraft:lime_stained_glass");
                }
            }
        }
        // Stepped entry stairs on 4 sides
        for (int o = 8; o <= 12; o++) {
            t.setBlock(o, 1, sz - 1, "minecraft:stone_brick_stairs[facing=north,half=bottom]");
            t.setBlock(o, 1, 0, "minecraft:stone_brick_stairs[facing=south,half=bottom]");
            t.setBlock(0, 1, o, "minecraft:stone_brick_stairs[facing=east,half=bottom]");
            t.setBlock(sx - 1, 1, o, "minecraft:stone_brick_stairs[facing=west,half=bottom]");
        }
        // Perimeter pillars and arches
        int[] pillars = {2, 6, 14, 18};
        for (int px : pillars) {
            for (int pz : pillars) {
                if (px > 2 && px < 18 && pz > 2 && pz < 18) continue;
                for (int y = 2; y <= 8; y++) {
                    t.setBlock(px, y, pz, y == 2 || y == 8 ? "minecraft:chiseled_tuff_bricks" : "minecraft:tuff_bricks");
                }
                t.setBlock(px, 9, pz, "minecraft:mossy_stone_brick_wall");
                t.setBlock(px, 10, pz, "minecraft:lantern[hanging=false]");
            }
        }
        // Corner walls and alcoves
        for (int c = 2; c <= 18; c++) {
            if (c < 7 || c > 13) {
                for (int y = 2; y <= 7; y++) {
                    String w = (y == 4 || y == 5) ? "minecraft:iron_bars" : stoneMix[rng.nextInt(4)];
                    t.setBlock(c, y, 2, w);
                    t.setBlock(c, y, 18, w);
                    t.setBlock(2, y, c, w);
                    t.setBlock(18, y, c, w);
                }
            }
        }
        // Stepped vaulted roof with central skylight ring
        for (int layer = 0; layer < 4; layer++) {
            int lo = 2 + layer * 2;
            int hi = 18 - layer * 2;
            int ry = 9 + layer;
            for (int x = lo; x <= hi; x++) {
                for (int z = lo; z <= hi; z++) {
                    boolean border = x == lo || x == hi || z == lo || z == hi;
                    if (border || layer < 3) {
                        if (Math.abs(x - 10) <= 1 && Math.abs(z - 10) <= 1 && layer == 3) {
                            t.setBlock(x, ry, z, "minecraft:tinted_glass");
                        } else {
                            t.setBlock(x, ry, z, stoneMix[rng.nextInt(4)]);
                        }
                    }
                }
            }
        }
        // Hanging lanterns inside hall
        int[][] lamps = {{6, 6}, {14, 6}, {6, 14}, {14, 14}};
        for (int[] l : lamps) {
            t.setBlock(l[0], 8, l[1], "minecraft:iron_chain[axis=y]");
            t.setBlock(l[0], 7, l[1], "minecraft:lantern[hanging=true]");
        }

        // Altar + Reward Chests + Explorer Chests + Mob Spawns
        t.setBlock(10, 2, 10, "minecraft:chiseled_tuff_bricks");
        t.setBlock(10, 3, 10, "minecraft:vault[ominous=false,vault_state=active]");
        t.setAltarOffset(10, 3, 10);
        t.setEntranceOffset(10, 2, 17);

        addExplorerChest(t, 4, 2, 4, "south");
        addExplorerChest(t, 16, 2, 4, "south");
        addExplorerChest(t, 4, 2, 16, "north");
        addExplorerChest(t, 16, 2, 16, "north");

        addRewardChest(t, 8, 2, 10, "east");
        addRewardChest(t, 12, 2, 10, "west");

        addMobSpawnsAround(t, 10, 2, 10, 5);
        return t;
    }

    private static DungeonTemplate buildSurfaceBastion() {
        int sx = 23, sy = 15, sz = 23;
        DungeonTemplate t = new DungeonTemplate(
                "surface_blackstone_bastion",
                "Чернитовый Бастион",
                DungeonCategory.SURFACE,
                2,
                TrialType.CAPTURE,
                sx, sy, sz
        );
        Random rng = new Random(20202L);
        String[] pal = {
            "minecraft:polished_blackstone_bricks",
            "minecraft:cracked_polished_blackstone_bricks",
            "minecraft:blackstone",
            "minecraft:gilded_blackstone",
            "minecraft:deepslate_bricks"
        };
        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");
        for (int x = 1; x <= sx - 2; x++) {
            for (int z = 1; z <= sz - 2; z++) {
                t.setBlock(x, 0, z, "minecraft:blackstone");
                t.setBlock(x, 1, z, pal[rng.nextInt(pal.length)]);
            }
        }
        // Glowing crimson capture circle around center (11,11)
        for (int x = 5; x <= 17; x++) {
            for (int z = 5; z <= 17; z++) {
                double d = Math.hypot(x - 11, z - 11);
                if (d >= 5.3 && d <= 6.5) {
                    t.setBlock(x, 0, z, "minecraft:shroomlight");
                    t.setBlock(x, 1, z, "minecraft:red_stained_glass");
                }
            }
        }
        // Outer fortress walls with arched gates
        for (int i = 2; i <= sx - 3; i++) {
            for (int y = 2; y <= 9; y++) {
                boolean gate = i >= 9 && i <= 13 && y <= 6;
                String blk = gate ? (y == 6 ? "minecraft:iron_bars" : "minecraft:air") : pal[rng.nextInt(3)];
                t.setBlock(i, y, 2, blk);
                t.setBlock(i, y, sz - 3, blk);
                t.setBlock(2, y, i, blk);
                t.setBlock(sx - 3, y, i, blk);
            }
            if (i % 2 == 0) {
                t.setBlock(i, 10, 2, "minecraft:polished_blackstone_brick_wall");
                t.setBlock(i, 10, sz - 3, "minecraft:polished_blackstone_brick_wall");
                t.setBlock(2, 10, i, "minecraft:polished_blackstone_brick_wall");
                t.setBlock(sx - 3, 10, i, "minecraft:polished_blackstone_brick_wall");
            }
        }
        // 4 Corner Bastion Towers
        int[][] corners = {{1, 1}, {1, sz - 4}, {sx - 4, 1}, {sx - 4, sz - 4}};
        for (int[] c : corners) {
            t.fill(c[0], 1, c[1], c[0] + 2, 12, c[1] + 2, "minecraft:polished_blackstone_bricks");
            t.fill(c[0], 13, c[1], c[0] + 2, 13, c[1] + 2, "minecraft:chiseled_polished_blackstone");
            t.setBlock(c[0] + 1, 13, c[1] + 1, "minecraft:netherrack");
            t.setBlock(c[0] + 1, 14, c[1] + 1, "minecraft:soul_fire");
        }
        // Basalt columns + soul chandeliers inside
        int[][] cols = {{6, 6}, {16, 6}, {6, 16}, {16, 16}};
        for (int[] col : cols) {
            t.fill(col[0], 2, col[1], col[0], 9, col[1], "minecraft:polished_basalt[axis=y]");
            t.setBlock(col[0], 7, col[1] + (col[1] < 11 ? 1 : -1), "minecraft:soul_lantern[hanging=false]");
        }

        t.setBlock(11, 2, 11, "minecraft:chiseled_polished_blackstone");
        t.setBlock(11, 3, 11, "minecraft:respawn_anchor[charges=4]");
        t.setAltarOffset(11, 3, 11);
        t.setEntranceOffset(11, 2, sz - 5);

        addExplorerChest(t, 4, 2, 5, "east");
        addExplorerChest(t, 18, 2, 5, "west");
        addExplorerChest(t, 4, 2, 17, "east");
        addExplorerChest(t, 18, 2, 17, "west");

        addRewardChest(t, 9, 2, 11, "east");
        addRewardChest(t, 13, 2, 11, "west");

        addMobSpawnsAround(t, 11, 2, 11, 5);
        return t;
    }

    private static DungeonTemplate buildSurfacePurpurKeep() {
        int sx = 23, sy = 17, sz = 23;
        DungeonTemplate t = new DungeonTemplate(
                "surface_purpur_keep",
                "Пурпурная Цитадель",
                DungeonCategory.SURFACE,
                3,
                TrialType.SEALS,
                sx, sy, sz
        );
        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");
        for (int x = 1; x <= sx - 2; x++) {
            for (int z = 1; z <= sz - 2; z++) {
                double d = Math.hypot(x - 11, z - 11);
                if (d <= 10.4) {
                    t.setBlock(x, 0, z, "minecraft:deepslate_tiles");
                    t.setBlock(x, 1, z, ((x + z) % 2 == 0) ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate");
                    if (Math.abs(x - 11) <= 1 || Math.abs(z - 11) <= 1) {
                        if (d <= 8.0) {
                            t.setBlock(x, 0, z, "minecraft:pearlescent_froglight[axis=y]");
                            t.setBlock(x, 1, z, "minecraft:magenta_stained_glass");
                        }
                    }
                }
            }
        }
        // Circular gothic colonnade + pointed dome spire
        for (int x = 1; x <= sx - 2; x++) {
            for (int z = 1; z <= sz - 2; z++) {
                double d = Math.hypot(x - 11, z - 11);
                if (d >= 9.0 && d <= 10.2) {
                    boolean doorway = (Math.abs(x - 11) <= 2 || Math.abs(z - 11) <= 2);
                    for (int y = 2; y <= 9; y++) {
                        if (doorway && y <= 6) continue;
                        t.setBlock(x, y, z, y % 3 == 0 ? "minecraft:purpur_pillar[axis=y]" : "minecraft:deepslate_tiles");
                    }
                }
                for (int y = 10; y <= 14; y++) {
                    double r = 9.5 - (y - 10) * 1.9;
                    if (Math.abs(d - r) <= 0.85) {
                        t.setBlock(x, y, z, y % 2 == 0 ? "minecraft:purpur_block" : "minecraft:deepslate_tiles");
                    }
                }
            }
        }
        t.fill(11, 14, 11, 11, 16, 11, "minecraft:purpur_pillar[axis=y]");
        t.setBlock(11, 16, 11, "minecraft:end_rod[facing=up]");

        t.setBlock(11, 2, 11, "minecraft:amethyst_block");
        t.setBlock(11, 3, 11, "minecraft:vault[ominous=true,vault_state=active]");
        t.setAltarOffset(11, 3, 11);
        t.setEntranceOffset(11, 2, 18);

        int[][] seals = {{6, 2, 6}, {16, 2, 6}, {6, 2, 16}, {16, 2, 16}};
        for (int[] s : seals) {
            t.setBlock(s[0], 1, s[2], "minecraft:crying_obsidian");
            t.setBlock(s[0], 2, s[2], "minecraft:respawn_anchor[charges=0]");
            t.getSealOffsets().add(new int[]{s[0], 2, s[2]});
        }

        addExplorerChest(t, 4, 2, 11, "east");
        addExplorerChest(t, 18, 2, 11, "west");
        addExplorerChest(t, 11, 2, 4, "south");

        addRewardChest(t, 9, 2, 11, "east");
        addRewardChest(t, 13, 2, 11, "west");
        addRewardChest(t, 11, 2, 9, "south");

        addMobSpawnsAround(t, 11, 2, 11, 5);
        return t;
    }

    private static DungeonTemplate buildUndergroundRiftCrypt() {
        int sx = 23, sy = 13, sz = 23;
        DungeonTemplate t = new DungeonTemplate(
                "underground_rift_crypt",
                "Склеп Раскола",
                DungeonCategory.UNDERGROUND,
                3,
                TrialType.WAVES,
                sx, sy, sz
        );
        Random rng = new Random(30303L);
        String[] walls = {
            "minecraft:deepslate_bricks",
            "minecraft:cracked_deepslate_bricks",
            "minecraft:deepslate_tiles",
            "minecraft:tuff_bricks"
        };
        // Full enclosed underground vault shell so cave openings don't flood lava/water in
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    boolean shell = x <= 1 || x >= sx - 2 || z <= 1 || z >= sz - 2 || y <= 1 || y >= sy - 2;
                    t.setBlock(x, y, z, shell ? walls[rng.nextInt(walls.length)] : "minecraft:air");
                }
            }
        }
        // Cyan glowing cross + crypt columns
        for (int i = 4; i <= sx - 5; i++) {
            t.setBlock(i, 0, 11, "minecraft:sea_lantern");
            t.setBlock(i, 1, 11, "minecraft:cyan_stained_glass");
            t.setBlock(11, 0, i, "minecraft:sea_lantern");
            t.setBlock(11, 1, i, "minecraft:cyan_stained_glass");
        }
        int[][] cols = {{6, 6}, {16, 6}, {6, 16}, {16, 16}};
        for (int[] c : cols) {
            t.fill(c[0], 2, c[1], c[0], sy - 3, c[1], "minecraft:chiseled_deepslate");
            t.setBlock(c[0], sy - 3, c[1] + 1, "minecraft:soul_lantern[hanging=true]");
        }
        // Crypt wall niches with candles & iron bars
        for (int n = 5; n <= 17; n += 4) {
            t.setBlock(2, 3, n, "minecraft:candle[candles=3,lit=true]");
            t.setBlock(sx - 3, 3, n, "minecraft:candle[candles=3,lit=true]");
        }

        // Landing pool at entrance (11, 2, 18) where vertical shaft descends
        t.fill(10, 1, 17, 12, 1, 19, "minecraft:water");
        t.setEntranceOffset(11, 2, 16);

        t.setBlock(11, 2, 11, "minecraft:chiseled_deepslate");
        t.setBlock(11, 3, 11, "minecraft:vault[ominous=true,vault_state=active]");
        t.setAltarOffset(11, 3, 11);

        addExplorerChest(t, 3, 2, 7, "east");
        addExplorerChest(t, 19, 2, 7, "west");
        addExplorerChest(t, 3, 2, 15, "east");
        addExplorerChest(t, 19, 2, 15, "west");

        addRewardChest(t, 9, 2, 11, "east");
        addRewardChest(t, 13, 2, 11, "west");
        addRewardChest(t, 11, 2, 9, "south");

        addMobSpawnsAround(t, 11, 2, 11, 5);
        return t;
    }

    private static DungeonTemplate buildUndergroundSculkSanctum() {
        int sx = 25, sy = 14, sz = 25;
        DungeonTemplate t = new DungeonTemplate(
                "underground_sculk_sanctum",
                "Скалковое Святилище",
                DungeonCategory.UNDERGROUND,
                4,
                TrialType.SEALS,
                sx, sy, sz
        );
        Random rng = new Random(40404L);
        String[] shellPal = {
            "minecraft:deepslate_tiles",
            "minecraft:cracked_deepslate_tiles",
            "minecraft:sculk",
            "minecraft:polished_deepslate"
        };
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    boolean shell = x <= 1 || x >= sx - 2 || z <= 1 || z >= sz - 2 || y <= 1 || y >= sy - 2;
                    t.setBlock(x, y, z, shell ? shellPal[rng.nextInt(shellPal.length)] : "minecraft:air");
                }
            }
        }
        for (int x = 4; x <= sx - 5; x++) {
            for (int z = 4; z <= sz - 5; z++) {
                double d = Math.hypot(x - 12, z - 12);
                if (Math.abs(d - 5.0) <= 0.75) {
                    t.setBlock(x, 0, z, "minecraft:verdant_froglight[axis=y]");
                    t.setBlock(x, 1, z, "minecraft:purple_stained_glass");
                }
            }
        }
        int[][] cols = {{6, 6}, {18, 6}, {6, 18}, {18, 18}};
        for (int[] c : cols) {
            t.fill(c[0], 2, c[1], c[0], sy - 3, c[1], "minecraft:polished_basalt[axis=y]");
            t.setBlock(c[0], sy - 4, c[1], "minecraft:sculk_catalyst");
            t.setBlock(12, sy - 3, c[1], "minecraft:iron_chain[axis=y]");
            t.setBlock(12, sy - 4, c[1], "minecraft:soul_lantern[hanging=true]");
        }

        t.fill(11, 1, 19, 13, 1, 21, "minecraft:water");
        t.setEntranceOffset(12, 2, 18);

        int[][] seals = {{6, 2, 8}, {18, 2, 8}, {6, 2, 16}, {18, 2, 16}};
        for (int[] s : seals) {
            t.setBlock(s[0], 1, s[2], "minecraft:crying_obsidian");
            t.setBlock(s[0], 2, s[2], "minecraft:respawn_anchor[charges=0]");
            t.getSealOffsets().add(new int[]{s[0], 2, s[2]});
        }

        t.setBlock(12, 2, 12, "minecraft:chiseled_deepslate");
        t.setBlock(12, 3, 12, "minecraft:vault[ominous=true,vault_state=active]");
        t.setAltarOffset(12, 3, 12);

        addExplorerChest(t, 3, 2, 12, "east");
        addExplorerChest(t, 21, 2, 12, "west");
        addExplorerChest(t, 8, 2, 3, "south");
        addExplorerChest(t, 16, 2, 3, "south");

        addRewardChest(t, 10, 2, 12, "east");
        addRewardChest(t, 14, 2, 12, "west");
        addRewardChest(t, 12, 2, 10, "south");

        addMobSpawnsAround(t, 12, 2, 12, 6);
        return t;
    }

    private static DungeonTemplate buildSkyAstralIsland() {
        int sx = 25, sy = 22, sz = 25;
        DungeonTemplate t = new DungeonTemplate(
                "sky_astral_island",
                "Астральный Остров",
                DungeonCategory.SKY,
                4,
                TrialType.CAPTURE,
                sx, sy, sz
        );
        Random rng = new Random(50505L);
        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");
        buildFloatingIslandBase(t, sx, sz, 8, rng, true);

        // Marble-quartz & purpur temple columns on deck y=9..18
        for (int a = 0; a < 8; a++) {
            double ang = (Math.PI * 2.0 * a) / 8.0;
            int px = (int) Math.round(12 + Math.cos(ang) * 8.0);
            int pz = (int) Math.round(12 + Math.sin(ang) * 8.0);
            t.fill(px, 9, pz, px, 16, pz, "minecraft:quartz_pillar[axis=y]");
            t.setBlock(px, 17, pz, "minecraft:purpur_slab[type=bottom]");
            t.setBlock(px, 18, pz, "minecraft:end_rod[facing=up]");
        }
        // Airy halo ring above arena
        for (int x = 3; x <= sx - 4; x++) {
            for (int z = 3; z <= sz - 4; z++) {
                double d = Math.hypot(x - 12, z - 12);
                if (Math.abs(d - 8.0) <= 0.7) {
                    t.setBlock(x, 17, z, "minecraft:quartz_bricks");
                }
            }
        }

        t.setBlock(12, 9, 12, "minecraft:amethyst_block");
        t.setBlock(12, 10, 12, "minecraft:respawn_anchor[charges=4]");
        t.setAltarOffset(12, 10, 12);
        t.setEntranceOffset(12, 9, 18);

        addExplorerChest(t, 6, 9, 12, "east");
        addExplorerChest(t, 18, 9, 12, "west");
        addExplorerChest(t, 12, 9, 6, "south");

        addRewardChest(t, 10, 9, 12, "east");
        addRewardChest(t, 14, 9, 12, "west");
        addRewardChest(t, 12, 9, 10, "south");

        addMobSpawnsAround(t, 12, 9, 12, 5);
        return t;
    }

    private static DungeonTemplate buildSkyElytrixCitadel() {
        int sx = 27, sy = 24, sz = 27;
        DungeonTemplate t = new DungeonTemplate(
                "sky_elytrix_citadel",
                "Небесная Цитадель Элитрикса",
                DungeonCategory.SKY,
                5,
                TrialType.WAVES,
                sx, sy, sz
        );
        Random rng = new Random(60606L);
        t.fill(0, 0, 0, sx - 1, sy - 1, sz - 1, "minecraft:air");
        buildFloatingIslandBase(t, sx, sz, 9, rng, false);

        // Grand Obsidian + Purpur + Quartz Crown Citadel on y=10..23
        int cx = sx / 2, cz = sz / 2;
        int[][] towers = {{cx - 8, cz - 8}, {cx + 8, cz - 8}, {cx - 8, cz + 8}, {cx + 8, cz + 8}};
        for (int[] tw : towers) {
            t.fill(tw[0] - 1, 10, tw[1] - 1, tw[0] + 1, 18, tw[1] + 1, "minecraft:polished_blackstone_bricks");
            t.fill(tw[0], 10, tw[1], tw[0], 20, tw[1], "minecraft:purpur_pillar[axis=y]");
            t.setBlock(tw[0], 21, tw[1], "minecraft:pearlescent_froglight[axis=y]");
            t.setBlock(tw[0], 22, tw[1], "minecraft:end_rod[facing=up]");
        }
        // Low perimeter battlements so players can knock each other off the island edge
        for (int x = 2; x <= sx - 3; x++) {
            for (int z = 2; z <= sz - 3; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (Math.abs(d - 10.6) <= 0.65 && (x + z) % 2 == 0) {
                    t.setBlock(x, 10, z, "minecraft:polished_blackstone_brick_wall");
                }
            }
        }

        t.setBlock(cx, 10, cz, "minecraft:crying_obsidian");
        t.setBlock(cx, 11, cz, "minecraft:vault[ominous=true,vault_state=active]");
        t.setAltarOffset(cx, 11, cz);
        t.setEntranceOffset(cx, 10, cz + 7);

        addExplorerChest(t, cx - 7, 10, cz, "east");
        addExplorerChest(t, cx + 7, 10, cz, "west");
        addExplorerChest(t, cx, 10, cz - 7, "south");
        addExplorerChest(t, cx, 10, cz + 8, "north");

        addRewardChest(t, cx - 2, 10, cz, "east");
        addRewardChest(t, cx + 2, 10, cz, "west");
        addRewardChest(t, cx, 10, cz - 2, "south");
        addRewardChest(t, cx, 10, cz + 2, "north");

        addMobSpawnsAround(t, cx, 10, cz, 6);
        return t;
    }

    private static void buildFloatingIslandBase(
            DungeonTemplate t,
            int sx,
            int sz,
            int deckY,
            Random rng,
            boolean quartzDeck
    ) {
        int cx = sx / 2, cz = sz / 2;
        double maxR = (sx / 2.0) - 1.4;
        for (int y = deckY; y >= 1; y--) {
            double progress = (double) y / deckY;
            double radius = maxR * Math.pow(progress, 0.62);
            for (int x = 1; x <= sx - 2; x++) {
                for (int z = 1; z <= sz - 2; z++) {
                    double wobble = Math.sin(x * 0.7) * 0.55 + Math.cos(z * 0.7) * 0.55;
                    double d = Math.hypot(x - cx, z - cz);
                    if (d <= radius + wobble * (1.0 - progress * 0.4)) {
                        if (y == deckY) {
                            double dc = Math.hypot(x - cx, z - cz);
                            if (dc <= 4.2 && (Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1)) {
                                t.setBlock(x, y - 1, z, "minecraft:pearlescent_froglight[axis=y]");
                                t.setBlock(x, y, z, "minecraft:magenta_stained_glass");
                            } else {
                                t.setBlock(x, y, z, quartzDeck
                                        ? ((x + z) % 2 == 0 ? "minecraft:quartz_bricks" : "minecraft:calcite")
                                        : ((x + z) % 2 == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:deepslate_tiles"));
                            }
                        } else {
                            int pick = rng.nextInt(8);
                            String rock = switch (pick) {
                                case 0 -> "minecraft:amethyst_block";
                                case 1 -> "minecraft:crying_obsidian";
                                case 2, 3 -> "minecraft:deepslate";
                                default -> "minecraft:tuff";
                            };
                            t.setBlock(x, y, z, rock);
                        }
                    }
                }
            }
        }
        // Hanging chains & lanterns beneath the floating island
        int[][] hangs = {{cx, cz}, {cx - 5, cz - 4}, {cx + 5, cz + 4}, {cx - 4, cz + 5}, {cx + 4, cz - 5}};
        for (int[] h : hangs) {
            for (int y = deckY; y >= 0; y--) {
                if (!t.getBlock(h[0], y, h[1]).equals("minecraft:air")) {
                    if (y >= 2) {
                        t.setBlock(h[0], y - 1, h[1], "minecraft:iron_chain[axis=y]");
                        t.setBlock(h[0], y - 2, h[1], "minecraft:soul_lantern[hanging=true]");
                    }
                    break;
                }
            }
        }
    }

    private static void addExplorerChest(DungeonTemplate t, int x, int y, int z, String facing) {
        t.setBlock(x, y, z, "minecraft:barrel[facing=up]");
        t.getExplorerChestOffsets().add(new int[]{x, y, z});
    }

    private static void addRewardChest(DungeonTemplate t, int x, int y, int z, String facing) {
        t.setBlock(x, y, z, "minecraft:chest[facing=" + facing + "]");
        t.getRewardChestOffsets().add(new int[]{x, y, z});
    }

    private static void addMobSpawnsAround(DungeonTemplate t, int cx, int y, int cz, int dist) {
        t.getMobSpawnOffsets().add(new int[]{cx - dist, y, cz});
        t.getMobSpawnOffsets().add(new int[]{cx + dist, y, cz});
        t.getMobSpawnOffsets().add(new int[]{cx, y, cz - dist});
        t.getMobSpawnOffsets().add(new int[]{cx, y, cz + dist});
        t.getMobSpawnOffsets().add(new int[]{cx - dist + 1, y, cz - dist + 1});
        t.getMobSpawnOffsets().add(new int[]{cx + dist - 1, y, cz + dist - 1});
    }
}
