package ru.rooyzee.elytrixitem.item.impl;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixitem.Main;

/**
 * Анимация добычи спавнера Киркой Раскола.
 *
 * Вариант выбирается в config.yml (split-pickaxe.animation), а посмотреть
 * все три можно командой /elytrixitems anim &lt;1-3&gt;.
 */
public final class SpawnerAnimation {

    public static final int VARIANTS = 3;

    private SpawnerAnimation() {
    }

    public static void play(Main plugin, Location blockLocation, int variant) {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }

        Location center = blockLocation.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
        switch (variant) {
            case 2:
                vortex(plugin, world, center);
                break;
            case 3:
                discharge(plugin, world, center);
                break;
            default:
                shatter(plugin, world, center);
                break;
        }
    }

    /** 1 — «Раскол»: спавнер рассыпается осколками, по полу идёт ударная волна. */
    private static void shatter(Main plugin, World world, Location center) {
        BlockData spawner = Material.SPAWNER.createBlockData();

        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0F, 0.65F);
        world.spawnParticle(Particle.BLOCK, center, 80, 0.35D, 0.35D, 0.35D, spawner);
        world.spawnParticle(Particle.CRIT, center, 20, 0.3D, 0.3D, 0.3D, 0.35D);

        later(plugin, world, center, 3L, () -> {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8F, 0.8F);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, center, 45, 0.45D, 0.4D, 0.45D, spawner);
            ring(world, center, Particle.END_ROD, 0.7D, 18, 0.0D);
        });

        later(plugin, world, center, 7L, () -> {
            world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9F, 1.2F);
            ring(world, center, Particle.END_ROD, 1.15D, 24, -0.15D);
            world.spawnParticle(Particle.SMOKE, center, 12, 0.35D, 0.2D, 0.35D, 0.02D);
        });
    }

    /** 2 — «Вихрь»: спираль душ закручивается вверх и растворяется. */
    private static void vortex(Main plugin, World world, Location center) {
        world.playSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 0.9F, 0.7F);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick > 22) {
                    cancel();
                    world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7F, 1.35F);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0.0D, 1.0D, 0.0D),
                            18, 0.25D, 0.35D, 0.25D, 0.05D);
                    world.spawnParticle(Particle.SOUL, center.clone().add(0.0D, 1.1D, 0.0D),
                            25, 0.3D, 0.3D, 0.3D, 0.02D);
                    return;
                }

                double angle = tick * 0.65D;
                double radius = 0.12D + tick * 0.022D;
                double height = tick * 0.045D;

                for (int i = 0; i < 3; i++) {
                    double rayAngle = angle + i * (Math.PI * 2.0D / 3.0D);
                    Location point = center.clone().add(
                            Math.cos(rayAngle) * radius, height, Math.sin(rayAngle) * radius);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                    if (tick % 2 == 0) {
                        world.spawnParticle(Particle.SOUL, point, 1, 0.02D, 0.02D, 0.02D, 0.01D);
                    }
                }

                if (tick == 8) {
                    world.playSound(center, Sound.BLOCK_SOUL_SOIL_BREAK, 0.7F, 1.1F);
                }
                if (tick == 16) {
                    world.playSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 0.6F, 1.4F);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** 3 — «Разряд»: молнии бьют из центра, вспышка и расходящееся кольцо. */
    private static void discharge(Main plugin, World world, Location center) {
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7F, 1.8F);

        for (int direction = 0; direction < 8; direction++) {
            double angle = direction * (Math.PI / 4.0D);
            for (int step = 1; step <= 5; step++) {
                double radius = step * 0.14D;
                Location point = center.clone().add(
                        Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
        }

        world.spawnParticle(Particle.FLASH, center, 1, Color.fromRGB(248, 190, 251));

        later(plugin, world, center, 3L, () -> {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.9F);
            world.spawnParticle(Particle.END_ROD, center, 25, 0.5D, 0.25D, 0.5D, 0.06D);
            ring(world, center, Particle.ELECTRIC_SPARK, 0.9D, 16, 0.0D);
        });

        later(plugin, world, center, 6L, () -> {
            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.6F, 1.6F);
            world.spawnParticle(Particle.SMOKE, center, 20, 0.35D, 0.2D, 0.35D, 0.02D);
        });
    }

    private static void ring(World world, Location center, Particle particle,
                             double radius, int points, double height) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            Location point = center.clone().add(
                    Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void later(Main plugin, World world, Location center, long delay, Runnable action) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (center.getWorld() != world) {
                    return;
                }
                action.run();
            }
        }.runTaskLater(plugin, delay);
    }
}
