package ru.rooyzee.elytrixcemetery.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.util.MaterialUtil;

import java.util.*;

public class CemeteryGenerator {

    private final Main plugin;
    private final Random random = new Random();
    private final Set<Location> modifiedBlocks = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Location, Boolean>());
    private final Map<Location, Material> generatedBlockTypes = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Location, BlockData> originalBlockData = new java.util.concurrent.ConcurrentHashMap<>();

    public CemeteryGenerator(Main plugin) {
        this.plugin = plugin;
    }

    public Set<Location> getModifiedBlocks() {
        return modifiedBlocks;
    }

    /** Adds tracked ground decorations when a cemetery is supplied by a schematic. */
    public void decorateCemetery(Location center) {
        if (center == null || center.getWorld() == null) return;
        Material wallMat = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material brickMat = MaterialUtil.getDeepslateBrickMaterial();
        buildDecorations(center.getWorld(), center.getBlockX(), center.getBlockZ(), wallMat, brickMat);
    }

    public List<Grave> generateCemetery(Location center) {
        List<Grave> graves = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return graves;

        if (!modifiedBlocks.isEmpty()) clearCemeteryBlocks();
        modifiedBlocks.clear();
        generatedBlockTypes.clear();
        originalBlockData.clear();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int radius = 45;

        Material wallMat = MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material brickMat = MaterialUtil.getDeepslateBrickMaterial();
        Material pathMat = MaterialUtil.getPathMaterial();

        // 1. Внешняя ограда по периметру (следует рельефу местности)
        for (int i = -radius; i <= radius; i++) {
            // Север / Юг
            buildPerimeterSegment(world, cx + i, cz - radius, wallMat, brickMat, (i % 6 == 0));
            buildPerimeterSegment(world, cx + i, cz + radius, wallMat, brickMat, (i % 6 == 0));
            // Запад / Восток
            buildPerimeterSegment(world, cx - radius, cz + i, wallMat, brickMat, (i % 6 == 0));
            buildPerimeterSegment(world, cx + radius, cz + i, wallMat, brickMat, (i % 6 == 0));
        }

        // 2. Входные ворота (Юг: X = cx, Z = cz - radius)
        int gateZ = cz - radius;
        for (int dx = -2; dx <= 2; dx++) {
            int y = findSurfaceY(world, cx + dx, gateZ);
            setBlock(world, cx + dx, y + 1, gateZ, Material.AIR);
            setBlock(world, cx + dx, y + 2, gateZ, Material.AIR);
            setBlock(world, cx + dx, y, gateZ, pathMat);
        }

        // 3. 20 Пространственно разнесенных могил (дистанция 15-20 блоков между каждой)
        int[][] graveOffsets = {
                {-32, -32}, {-16, -34}, {0, -32}, {18, -34}, {34, -30},
                {-36, -16}, {-18, -16}, {-4, -14}, {16, -16}, {34, -14},
                {-34, 12},  {-16, 14},  {2, 12},   {18, 14},  {35, 12},
                {-32, 32},  {-14, 34},  {0, 32},   {18, 34},  {32, 30}
        };

        int graveId = 1;
        for (int[] offset : graveOffsets) {
            int gx = cx + offset[0];
            int gz = cz + offset[1];
            int gy = findSurfaceY(world, gx, gz);

            Grave grave = buildGrave(world, gx, gy, gz, graveId++);
            graves.add(grave);

            // Прокладываем атмосферную тропинку к могиле
            buildPathTo(world, cx, cz, gx, gz, pathMat);
        }

        // 4. Жуткие деревья и фонари душ
        int[][] treeOffsets = {
                {-26, -24}, {26, -24}, {-26, 24}, {26, 24},
                {-10, -2}, {10, -2}, {0, 20}
        };
        for (int[] to : treeOffsets) {
            int tx = cx + to[0];
            int tz = cz + to[1];
            int ty = findSurfaceY(world, tx, tz);
            buildSpookyTree(world, tx, ty, tz);
        }

        // 5. Дополнительные декорации: каменные фонари и полуразрушенные мемориалы.
        buildDecorations(world, cx, cz, wallMat, brickMat);

        // 6. Растительность кладбища (розы иссушителя, сухие кусты)
        for (int dx = -radius + 3; dx <= radius - 3; dx += 3) {
            for (int dz = -radius + 3; dz <= radius - 3; dz += 3) {
                int wx = cx + dx + random.nextInt(3) - 1;
                int wz = cz + dz + random.nextInt(3) - 1;
                int wy = findSurfaceY(world, wx, wz);
                Block floor = world.getBlockAt(wx, wy, wz);
                Block above = world.getBlockAt(wx, wy + 1, wz);
                if (floor.getType().isSolid() && above.getType() == Material.AIR) {
                    double r = random.nextDouble();
                    if (r < 0.08) setBlock(world, wx, wy + 1, wz, Material.WITHER_ROSE);
                    else if (r < 0.18) setBlock(world, wx, wy + 1, wz, Material.DEAD_BUSH);
                }
            }
        }

        return graves;
    }

    private void buildPerimeterSegment(World world, int x, int z, Material wallMat, Material brickMat, boolean isPillar) {
        int y = findSurfaceY(world, x, z);
        if (isPillar) {
            setBlock(world, x, y + 1, z, brickMat);
            setBlock(world, x, y + 2, z, brickMat);
            setBlock(world, x, y + 3, z, wallMat);
            setBlock(world, x, y + 4, z, Material.SOUL_LANTERN);
        } else {
            setBlock(world, x, y + 1, z, brickMat);
            setBlock(world, x, y + 2, z, Material.IRON_BARS);
        }
    }

    private Grave buildGrave(World world, int x, int y, int z, int id) {
        Material rootedDirt = MaterialUtil.getRootedDirtMaterial();

        buildGraveCross(world, x, y, z);

        // 2. Насыпь могилы (земля/подзол)
        Block surfaceBlock = world.getBlockAt(x, y, z);
        setBlock(world, x, y, z, Material.COARSE_DIRT);
        setBlock(world, x, y, z + 1, rootedDirt);

        // 3. Подземный сундук под могилой (y - 1)
        Block chestBlock = world.getBlockAt(x, y - 1, z);
        setBlock(world, x, y - 1, z, Material.CHEST);

        // Дно могилы
        setBlock(world, x, y - 1, z + 1, Material.DIRT);

        Location digLoc = surfaceBlock.getLocation();
        Location chestLoc = chestBlock.getLocation();
        // Подняли метку над более выразительным крестом, не перекрывая его верхушку.
        Location holoLoc = new Location(world, x + 0.5, y + 5.15, z + 0.5);

        return new Grave(id, digLoc, chestLoc, holoLoc);
    }

    /** Replaces the old narrow wall-only marker found in a pasted schematic. */
    public void upgradeGraveCross(Location digLocation) {
        if (digLocation == null || digLocation.getWorld() == null) return;
        World world = digLocation.getWorld();
        int x = digLocation.getBlockX();
        int y = digLocation.getBlockY();
        int crossZ = digLocation.getBlockZ() - 1;

        // Remove the previous wall-only cross, including its lower horizontal arms.
        setBlock(world, x, y + 1, crossZ, Material.AIR);
        setBlock(world, x, y + 2, crossZ, Material.AIR);
        setBlock(world, x - 1, y + 2, crossZ, Material.AIR);
        setBlock(world, x + 1, y + 2, crossZ, Material.AIR);
        setBlock(world, x, y + 3, crossZ, Material.AIR);
        buildGraveCross(world, x, y, digLocation.getBlockZ());
    }

    private void buildGraveCross(World world, int x, int y, int z) {
        Material brick = MaterialUtil.getDeepslateBrickMaterial();
        Material carved = MaterialUtil.match("CHISELED_POLISHED_BLACKSTONE", "CHISELED_DEEPSLATE",
                "CHISELED_STONE_BRICKS", "POLISHED_BLACKSTONE_BRICKS");
        Material wall = MaterialUtil.match("POLISHED_BLACKSTONE_BRICK_WALL", "DEEPSLATE_BRICK_WALL",
                "POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL");
        Material slab = MaterialUtil.getDeepslateSlabMaterial();
        int crossZ = z - 1;

        // Широкое каменное основание.
        setBlock(world, x - 1, y + 1, crossZ, slab);
        setBlock(world, x, y + 1, crossZ, brick);
        setBlock(world, x + 1, y + 1, crossZ, slab);

        // Ствол, рельефная перекладина и акцентированный центр.
        setBlock(world, x, y + 2, crossZ, brick);
        setBlock(world, x - 1, y + 3, crossZ, wall);
        setBlock(world, x, y + 3, crossZ, carved);
        setBlock(world, x + 1, y + 3, crossZ, wall);
        setBlock(world, x, y + 4, crossZ, wall);
    }

    private void buildDecorations(World world, int cx, int cz, Material wallMat, Material brickMat) {
        int[][] lanternOffsets = {
                {-40, -40}, {40, -40}, {-40, 40}, {40, 40}
        };
        for (int[] offset : lanternOffsets) {
            buildLanternPost(world, cx + offset[0], cz + offset[1], wallMat, brickMat);
        }

        int[][] memorialOffsets = {
                {-42, 0}, {42, 0}, {0, 42}
        };
        for (int[] offset : memorialOffsets) {
            buildBrokenMemorial(world, cx + offset[0], cz + offset[1], wallMat, brickMat);
        }
    }

    private void buildLanternPost(World world, int x, int z, Material wallMat, Material brickMat) {
        int y = findSurfaceY(world, x, z);
        setBlock(world, x, y + 1, z, brickMat);
        setBlock(world, x, y + 2, z, wallMat);
        setBlock(world, x, y + 3, z, Material.SOUL_LANTERN);
    }

    private void buildBrokenMemorial(World world, int x, int z, Material wallMat, Material brickMat) {
        int y = findSurfaceY(world, x, z);
        Material slab = MaterialUtil.getDeepslateSlabMaterial();
        setBlock(world, x - 1, y + 1, z, slab);
        setBlock(world, x, y + 1, z, brickMat);
        setBlock(world, x + 1, y + 1, z, slab);
        setBlock(world, x, y + 2, z, brickMat);
        setBlock(world, x, y + 3, z, wallMat);
    }

    private void buildPathTo(World world, int startX, int startZ, int endX, int endZ, Material pathMat) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 2) {
            double progress = (double) i / steps;
            int x = (int) Math.round(startX + (endX - startX) * progress);
            int z = (int) Math.round(startZ + (endZ - startZ) * progress);
            int y = findSurfaceY(world, x, z);
            Block b = world.getBlockAt(x, y, z);
            if (b.getType() != Material.CHEST && b.getType() != MaterialUtil.match("POLISHED_BLACKSTONE_WALL", "COBBLESTONE_WALL")) {
                setBlock(world, x, y, z, random.nextDouble() < 0.6 ? pathMat : Material.COARSE_DIRT);
            }
        }
    }

    private void buildSpookyTree(World world, int x, int y, int z) {
        for (int dy = 1; dy <= 4; dy++) {
            setBlock(world, x, y + dy, z, Material.DARK_OAK_LOG);
        }
        setBlock(world, x + 1, y + 4, z, Material.DARK_OAK_LOG);
        setBlock(world, x - 1, y + 5, z, Material.DARK_OAK_LOG);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 4; dy <= 6; dy++) {
                    if (random.nextDouble() < 0.6) {
                        Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                        if (b.getType() == Material.AIR) {
                            setBlock(world, x + dx, y + dy, z + dz, Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    public int findSurfaceY(World world, int x, int z) {
        int maxY = Math.min(255, world.getMaxHeight() - 1);
        for (int y = maxY; y >= 5; y--) {
            Block b = world.getBlockAt(x, y, z);
            Material type = b.getType();
            if (isSolidGround(type)) {
                return y;
            }
        }
        return world.getHighestBlockYAt(x, z);
    }

    private boolean isSolidGround(Material type) {
        if (type == Material.AIR || type.isAir()) return false;
        String name = type.name();
        if (name.contains("LEAVES") || name.contains("LOG") || name.contains("WOOD") ||
                name.contains("FENCE") || name.contains("WALL") || name.contains("FLOWER") ||
                (name.contains("GRASS") && !name.contains("BLOCK")) || name.contains("PLANT") ||
                name.contains("SAPLING") || name.contains("MUSHROOM") || name.contains("VINE") ||
                name.contains("LANTERN") || name.contains("TORCH") || name.contains("BARS")) {
            return false;
        }
        return type.isSolid();
    }

    private void setBlock(World world, int x, int y, int z, Material mat) {
        Block block = world.getBlockAt(x, y, z);
        Location location = block.getLocation();
        modifiedBlocks.add(location);
        originalBlockData.putIfAbsent(location, block.getBlockData().clone());
        generatedBlockTypes.put(location, mat);
        block.setType(mat, false);
    }

    public void clearCemeteryBlocks() {
        for (Map.Entry<Location, Material> entry : generatedBlockTypes.entrySet()) {
            Location location = entry.getKey();
            if (location.getWorld() == null) continue;
            Block block = location.getBlock();
            // Restore the exact prior block data and preserve edits that replaced our generated block.
            BlockData original = originalBlockData.get(location);
            if (block.getType() == entry.getValue() && original != null) {
                block.setBlockData(original, false);
            }
        }
        modifiedBlocks.clear();
        generatedBlockTypes.clear();
        originalBlockData.clear();
    }
}
