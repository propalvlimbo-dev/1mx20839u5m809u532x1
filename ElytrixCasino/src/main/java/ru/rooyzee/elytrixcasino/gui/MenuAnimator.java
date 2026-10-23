package ru.rooyzee.elytrixcasino.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.rooyzee.elytrixcasino.Main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

public final class MenuAnimator {

    private static final int[][] PURPLE_FRAMES = {
            {48, 49, 50},
            {51, 52},
            {53, 36},
            {37, 43},
            {44, 27},
            {35}
    };

    private static final float[][] PURPLE_CHIMES = {
            {0.20F, 0.85F},
            null,
            {0.22F, 0.91F},
            null,
            {0.23F, 0.98F},
            null
    };

    private static final int[][] BLACK_FRAMES = {
            {18, 9},
            {10, 0},
            {1, 2},
            {3, 4},
            {5, 6},
            {7, 8},
            {16, 17},
            {26}
    };

    private static final float[][] BLACK_CHIMES = {
            {0.25F, 1.05F},
            null,
            {0.27F, 1.13F},
            null,
            {0.28F, 1.21F},
            null,
            {0.30F, 1.30F},
            null
    };

    private static final int[][] AUTOMATE_PURPLE_FRAMES = {
            {48, 50},
            {51, 52},
            {37, 43},
            {28, 34}
    };

    private static final float[][] AUTOMATE_PURPLE_CHIMES = {
            {0.20F, 0.85F},
            null,
            {0.22F, 0.93F},
            null
    };

    private static final int[][] AUTOMATE_BLACK_FRAMES = {
            {19, 25},
            {10, 16},
            {1, 7}
    };

    private static final float[][] AUTOMATE_BLACK_CHIMES = {
            {0.25F, 1.05F},
            null,
            {0.29F, 1.22F}
    };

    private final Main plugin;
    private final Map<UUID, BukkitTask> runningTasks = new ConcurrentHashMap<>();

    public MenuAnimator(Main plugin) {
        this.plugin = plugin;
    }

    public void stop(UUID playerId) {
        if (playerId == null) {
            return;
        }
        BukkitTask prev = runningTasks.remove(playerId);
        if (prev != null) {
            prev.cancel();
        }
    }

    public void stopAll() {
        for (BukkitTask task : runningTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        runningTasks.clear();
    }

    public void openStandardMenu(Player player,
                                 Inventory inv,
                                 ItemStack closeButton,
                                 ItemStack bottomInfoItem,
                                 Map<Integer, ItemStack> actionButtons) {
        UUID uuid = player.getUniqueId();
        stop(uuid);

        ItemStack purple = GuiGuard.pane(plugin, Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack black = GuiGuard.pane(plugin, Material.BLACK_STAINED_GLASS_PANE);

        Map<Integer, ItemStack> reserved = new LinkedHashMap<>(actionButtons);
        List<Map.Entry<Integer, ItemStack>> buttonList = new ArrayList<>(actionButtons.entrySet());

        if (closeButton != null && 45 < inv.getSize()) {
            inv.setItem(45, closeButton);
        }
        if (bottomInfoItem != null && 49 < inv.getSize()) {
            inv.setItem(49, bottomInfoItem);
            reserved.put(49, bottomInfoItem);
        }
        placeFrameSlots(inv, new int[]{46, 47}, purple, reserved);

        player.openInventory(inv);
        playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.30F, 1.50F);

        final int purpleCount = PURPLE_FRAMES.length;
        final int blackCount = BLACK_FRAMES.length;
        final int totalFrameSteps = purpleCount + blackCount;
        final int[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isStillViewing(player, inv)) {
                stop(uuid);
                return;
            }
            int step = tick[0]++;
            if (step < purpleCount) {
                placeFrameSlots(inv, PURPLE_FRAMES[step], purple, reserved);
                float[] sound = PURPLE_CHIMES[step];
                if (sound != null) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, sound[0], sound[1]);
                }
                return;
            }
            if (step < totalFrameSteps) {
                int bIndex = step - purpleCount;
                placeFrameSlots(inv, BLACK_FRAMES[bIndex], black, reserved);
                float[] sound = BLACK_CHIMES[bIndex];
                if (sound != null) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, sound[0], sound[1]);
                }
                return;
            }

            int btnIndex = step - totalFrameSteps;
            if (btnIndex < buttonList.size()) {
                Map.Entry<Integer, ItemStack> entry = buttonList.get(btnIndex);
                int slot = entry.getKey();
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, entry.getValue());
                }
                float progress = buttonList.size() <= 1
                        ? 1.0F
                        : (float) btnIndex / (float) (buttonList.size() - 1);
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING,
                        0.26F + 0.08F * progress,
                        1.00F + 0.42F * progress);
                if (btnIndex == buttonList.size() - 1) {
                    playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.40F);
                    stop(uuid);
                }
                return;
            }

            playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.40F);
            stop(uuid);
        }, 1L, 1L);

        runningTasks.put(uuid, task);
    }

    public void openAutomateMenu(Player player,
                                 Inventory inv,
                                 ItemStack closeButton,
                                 Map<Integer, ItemStack> controls,
                                 IntConsumer paintReelRow) {
        UUID uuid = player.getUniqueId();
        stop(uuid);

        ItemStack purple = GuiGuard.pane(plugin, Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack black = GuiGuard.pane(plugin, Material.BLACK_STAINED_GLASS_PANE);
        Map<Integer, ItemStack> reserved = new LinkedHashMap<>(controls);

        if (closeButton != null) {
            inv.setItem(45, closeButton);
            inv.setItem(53, closeButton.clone());
            reserved.put(53, closeButton);
        }
        ItemStack playBtn = controls.get(49);
        if (playBtn != null) {
            inv.setItem(49, playBtn);
        }
        placeFrameSlots(inv, new int[]{46, 47}, purple, reserved);

        player.openInventory(inv);
        playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.30F, 1.50F);

        final int purpleCount = AUTOMATE_PURPLE_FRAMES.length;
        final int blackCount = AUTOMATE_BLACK_FRAMES.length;
        final int totalFrameSteps = purpleCount + blackCount;
        final int[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isStillViewing(player, inv)) {
                if (paintReelRow != null) {
                    for (int r = 0; r < 5; r++) {
                        paintReelRow.accept(r);
                    }
                }
                stop(uuid);
                return;
            }
            int step = tick[0]++;
            if (step < purpleCount) {
                placeFrameSlots(inv, AUTOMATE_PURPLE_FRAMES[step], purple, reserved);
                float[] sound = AUTOMATE_PURPLE_CHIMES[step];
                if (sound != null) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, sound[0], sound[1]);
                }
                return;
            }
            if (step < totalFrameSteps) {
                int bIndex = step - purpleCount;
                placeFrameSlots(inv, AUTOMATE_BLACK_FRAMES[bIndex], black, reserved);
                float[] sound = AUTOMATE_BLACK_CHIMES[bIndex];
                if (sound != null) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, sound[0], sound[1]);
                }
                return;
            }

            int reelRow = step - totalFrameSteps;
            if (reelRow < 5) {
                if (paintReelRow != null) {
                    paintReelRow.accept(reelRow);
                }
                float progress = (float) reelRow / 4.0F;
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING,
                        0.26F + 0.08F * progress,
                        1.00F + 0.42F * progress);
                if (reelRow == 4) {
                    playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.40F);
                    stop(uuid);
                }
                return;
            }

            playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.40F);
            stop(uuid);
        }, 1L, 1L);

        runningTasks.put(uuid, task);
    }

    public void openMinerBoard(Player player,
                               Inventory inv,
                               ItemStack hiddenCellTemplate,
                               Map<Integer, ItemStack> bottomControls) {
        UUID uuid = player.getUniqueId();
        stop(uuid);

        ItemStack purple = GuiGuard.pane(plugin, Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 46; slot <= 53; slot++) {
            if (!bottomControls.containsKey(slot)) {
                inv.setItem(slot, purple);
            }
        }
        for (Map.Entry<Integer, ItemStack> entry : bottomControls.entrySet()) {
            if (entry.getKey() >= 45 && entry.getKey() < inv.getSize()) {
                inv.setItem(entry.getKey(), entry.getValue());
            }
        }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, hiddenCellTemplate.clone());
        }

        player.openInventory(inv);
        playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.30F, 1.50F);

        final int[] row = {1};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isStillViewing(player, inv)) {
                for (int r = row[0]; r < 5; r++) {
                    for (int col = 0; col < 9; col++) {
                        int slot = r * 9 + col;
                        if (inv.getItem(slot) == null) {
                            inv.setItem(slot, hiddenCellTemplate.clone());
                        }
                    }
                }
                stop(uuid);
                return;
            }

            int r = row[0]++;
            if (r < 5) {
                for (int col = 0; col < 9; col++) {
                    int slot = r * 9 + col;
                    if (inv.getItem(slot) == null) {
                        inv.setItem(slot, hiddenCellTemplate.clone());
                    }
                }
                if (r % 2 == 1) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.24F, 0.92F + 0.14F * r);
                }
                if (r == 4) {
                    playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.40F);
                    stop(uuid);
                }
                return;
            }

            playSound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, 1.40F);
            stop(uuid);
        }, 1L, 1L);

        runningTasks.put(uuid, task);
    }

    private void placeFrameSlots(Inventory inv,
                                 int[] slots,
                                 ItemStack pane,
                                 Map<Integer, ItemStack> reservedButtons) {
        Map<Integer, ItemStack> safeReserved = reservedButtons == null ? Collections.emptyMap() : reservedButtons;
        for (int slot : slots) {
            if (slot >= 0 && slot < inv.getSize() && slot != 45 && !safeReserved.containsKey(slot)) {
                inv.setItem(slot, pane);
            }
        }
    }

    private boolean isStillViewing(Player player, Inventory inv) {
        return player != null
                && player.isOnline()
                && player.getOpenInventory().getTopInventory() == inv;
    }

    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {
        }
    }
}
