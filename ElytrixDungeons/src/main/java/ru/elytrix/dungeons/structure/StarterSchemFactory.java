package ru.elytrix.dungeons.structure;

import java.io.File;
import java.io.IOException;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonTemplate;

/**
 * Generates ready-to-replace starter schematics:
 *  - surface_1.schem & surface_2.schem (Haunted Houses for 4 + 3 = 7 Surface Dungeons)
 *  - underground_1.schem & underground_2.schem (Underground Vaults for 4 + 3 = 7 Underground Dungeons)
 */
public final class StarterSchemFactory {

    private StarterSchemFactory() {}

    public static void writeStarterSurfacePair(File dir) throws IOException {
        SpongeSchematicCodec.writeSchem(buildHouseVariant("surface_1", false), new File(dir, "surface_1.schem"));
        SpongeSchematicCodec.writeSchem(buildHouseVariant("surface_2", true), new File(dir, "surface_2.schem"));
    }

    public static void writeStarterUndergroundPair(File dir) throws IOException {
        SpongeSchematicCodec.writeSchem(buildUndergroundVariant("underground_1", false), new File(dir, "underground_1.schem"));
        SpongeSchematicCodec.writeSchem(buildUndergroundVariant("underground_2", true), new File(dir, "underground_2.schem"));
    }

    private static DungeonTemplate buildHouseVariant(String id, boolean stoneChapel) {
        int sx = 19, sy = 13, sz = 19;
        DungeonTemplate t = new DungeonTemplate(id, "Дом призраков", DungeonCategory.SURFACE, sx, sy, sz);

        String floor = stoneChapel ? "minecraft:polished_deepslate" : "minecraft:spruce_planks";
        String wall = stoneChapel ? "minecraft:stone_bricks" : "minecraft:dark_oak_planks";
        String pillar = stoneChapel ? "minecraft:chiseled_stone_bricks" : "minecraft:stripped_dark_oak_log[axis=y]";
        String roof = stoneChapel ? "minecraft:deepslate_tile_slab[type=bottom]" : "minecraft:deepslate_brick_slab[type=bottom]";

        // Ground & veranda
        for (int x = 1; x <= sx - 2; x++) {
            for (int z = 1; z <= sz - 2; z++) {
                t.setBlock(x, 0, z, floor);
            }
        }
        // Walls of 2-story house (x: 2..16, z: 2..16, y: 1..8)
        for (int y = 1; y <= 8; y++) {
            for (int x = 2; x <= 16; x++) {
                for (int z = 2; z <= 16; z++) {
                    boolean borderX = (x == 2 || x == 16);
                    boolean borderZ = (z == 2 || z == 16);
                    if (borderX && borderZ) {
                        t.setBlock(x, y, z, pillar);
                    } else if (borderX || borderZ) {
                        boolean window = (y == 3 || y == 6) && (x % 4 == 1 || z % 4 == 1);
                        t.setBlock(x, y, z, window ? "minecraft:tinted_glass" : wall);
                    }
                }
            }
        }
        // Second-floor balcony ring (leaves central atrium open for Allay ghosts to fly freely!)
        for (int x = 3; x <= 15; x++) {
            for (int z = 3; z <= 15; z++) {
                if (x <= 5 || x >= 13 || z <= 5 || z >= 13) {
                    t.setBlock(x, 5, z, floor);
                }
            }
        }
        for (int y = 1; y <= 5; y++) {
            t.setBlock(3, y, 9, "minecraft:ladder[facing=east]");
        }
        // Pitched roof y: 9..12
        for (int step = 0; step <= 3; step++) {
            int y = 9 + step;
            int min = 1 + step * 2;
            int max = sx - 2 - step * 2;
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    t.setBlock(x, y, z, step == 3 ? "minecraft:deepslate_bricks" : roof);
                }
            }
        }
        // Wide open entrance doorway
        for (int dx = 8; dx <= 10; dx++) {
            for (int y = 1; y <= 3; y++) {
                t.setBlock(dx, y, 2, "minecraft:air");
            }
        }
        // Atmospheric soul lanterns, cobwebs, bookshelves & 5 loot chests
        t.setBlock(9, 8, 9, "minecraft:soul_lantern[hanging=true]");
        t.setBlock(5, 4, 5, "minecraft:soul_lantern[hanging=true]");
        t.setBlock(13, 4, 5, "minecraft:soul_lantern[hanging=true]");
        t.setBlock(5, 4, 13, "minecraft:soul_lantern[hanging=true]");
        t.setBlock(13, 4, 13, "minecraft:soul_lantern[hanging=true]");
        t.setBlock(3, 8, 3, "minecraft:cobweb");
        t.setBlock(15, 8, 15, "minecraft:cobweb");

        int[][] chests = {{4, 1, 14}, {14, 1, 14}, {9, 1, 15}, {4, 6, 9}, {14, 6, 9}};
        for (int[] c : chests) {
            t.setBlock(c[0], c[1], c[2], "minecraft:chest[facing=south]");
        }
        return t;
    }

    private static DungeonTemplate buildUndergroundVariant(String id, boolean bastionStyle) {
        int sx = 23, sy = 12, sz = 23;
        DungeonTemplate t = new DungeonTemplate(id, "Подземный данж", DungeonCategory.UNDERGROUND, sx, sy, sz);

        String shell = bastionStyle ? "minecraft:blackstone" : "minecraft:deepslate_tiles";
        String floor = bastionStyle ? "minecraft:polished_blackstone_bricks" : "minecraft:deepslate_bricks";
        String pillar = bastionStyle ? "minecraft:gilded_blackstone" : "minecraft:chiseled_deepslate";

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    boolean boundary = x == 0 || z == 0 || x == sx - 1 || z == sz - 1 || y == 0 || y == sy - 1;
                    t.setBlock(x, y, z, boundary ? (y == 0 ? floor : shell) : "minecraft:air");
                }
            }
        }
        int[][] cols = {{5, 5}, {17, 5}, {5, 17}, {17, 17}};
        for (int[] c : cols) {
            for (int y = 1; y <= sy - 2; y++) t.setBlock(c[0], y, c[1], pillar);
            t.setBlock(c[0], sy - 3, c[1] + 1, "minecraft:soul_lantern[hanging=true]");
        }

        // Central dais + Main Locked Shulker Box
        for (int x = 9; x <= 13; x++) {
            for (int z = 9; z <= 13; z++) {
                t.setBlock(x, 1, z, pillar);
            }
        }
        t.setBlock(11, 2, 11, "minecraft:purple_shulker_box[facing=up]");

        // Side chests & barrels
        t.setBlock(3, 1, 11, "minecraft:chest[facing=east]");
        t.setBlock(19, 1, 11, "minecraft:chest[facing=west]");
        t.setBlock(11, 1, 19, "minecraft:barrel[facing=north]");
        t.setBlock(11, 1, 3, "minecraft:barrel[facing=south]");
        return t;
    }
}
