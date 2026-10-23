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

/**
 * Покадровая анимация открытия меню казино в фирменном стиле BMenu ElytriX.
 */
public final class MenuAnimator {

    private static final int[][] PURPLE_STEPS = {
            {48, 50},
            {51, 52},
            {53, 36},
            {37, 43},
            {44, 27},
            {35, 49}
    };

    private static final int[][] BLACK_STEPS = {
            {18, 9},
            {10, 0},
            {1, 2},
            {3, 4},
            {5, 6},
            {7, 8, 16, 17, 26}
    };

    private static final int[][] AUTOMATE_PURPLE_STEPS = {
            {46, 52},
            {47, 51},
            {48, 50},
            {36, 44},
            {37, 43},
            {27, 28, 34, 35}
    };

    private static final int[][] AUTOMATE_BLACK_STEPS = {
            {18, 26},
            {19, 25},
            {9, 17},
            {10, 16},
            {0, 8},
            {1, 7}
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
                                 Map<Integer, ItemStack> actionButtons) {
        UUID uuid = player.getUniqueId();
        stop(uuid);

        ItemStack purple = GuiGuard.pane(plugin, Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack black = GuiGuard.pane(plugin, Material.BLACK_STAINED_GLASS_PANE);
        Map<Integer, ItemStack> orderedButtons = new LinkedHashMap<>(actionButtons);
        List<Map.Entry<Integer, ItemStack>> buttonList = new ArrayList<>(orderedButtons.entrySet());

        if (closeButton != null && 45 < inv.getSize()) {
            inv.setItem(45, closeButton);
        }
        placeFrameSlots(inv, new int[]{46, 47}, purple, orderedButtons);

        player.openInventory(inv);
        playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.30F, 1.50F);

        final int totalFrameSteps = PURPLE_STEPS.length;
        final int[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isStillViewing(player, inv)) {
                stop(uuid);
                return;
            }
            int step = tick[0]++;
            if (step < totalFrameSteps) {
                placeFrameSlots(inv, PURPLE_STEPS[step], purple, orderedButtons);
                placeFrameSlots(inv, BLACK_STEPS[step], black, orderedButtons);
                float progress = (float) step / (float) Math.max(1, totalFrameSteps - 1);
                playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME,
                        0.22F + 0.08F * progress,
                        0.85F + 0.45F * progress);
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
                        0.28F + 0.06F * progress,
                        1.00F + 0.50F * progress);
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
                                 Runnable paintInitialReelGrid) {
        UUID uuid = player.getUniqueId();
        stop(uuid);

        ItemStack purple = GuiGuard.pane(plugin, Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack black = GuiGuard.pane(plugin, Material.BLACK_STAINED_GLASS_PANE);
        Map<Integer, ItemStack> orderedButtons = new LinkedHashMap<>(controls);
        List<Map.Entry<Integer, ItemStack>> buttonList = new ArrayList<>(orderedButtons.entrySet());

        if (closeButton != null && 45 < inv.getSize()) {
            inv.setItem(45, closeButton);
        }
        if (paintInitialReelGrid != null) {
            paintInitialReelGrid.run();
        }

        player.openInventory(inv);
        playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.30F, 1.50F);

        final int totalFrameSteps = AUTOMATE_PURPLE_STEPS.length;
        final int[] tick = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isStillViewing(player, inv)) {
                stop(uuid);
                return;
            }
            int step = tick[0]++;
            if (step < totalFrameSteps) {
                placeFrameSlots(inv, AUTOMATE_PURPLE_STEPS[step], purple, orderedButtons);
                placeFrameSlots(inv, AUTOMATE_BLACK_STEPS[step], black, orderedButtons);
                float progress = (float) step / (float) Math.max(1, totalFrameSteps - 1);
                playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME,
                        0.22F + 0.08F * progress,
                        0.88F + 0.42F * progress);
                return;
            }

            int btnIndex = step - totalFrameSteps;
            if (btnIndex < buttonList.size()) {
                Map.Entry<Integer, ItemStack> entry = buttonList.get(btnIndex);
                int slot = entry.getKey();
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, entry.getValue());
                }
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.30F, 1.25F + 0.20F * btnIndex);
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
        for (int slot = 46; slot <= 52; slot++) {
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
                playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 0.90F + 0.12F * r);
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
