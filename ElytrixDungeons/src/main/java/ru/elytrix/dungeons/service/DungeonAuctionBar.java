package ru.elytrix.dungeons.service;

import java.util.Collection;
import java.util.List;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.elytrix.dungeons.ElytrixDungeonsPlugin;
import ru.elytrix.dungeons.model.BlockPosKey;
import ru.elytrix.dungeons.model.DungeonCategory;
import ru.elytrix.dungeons.model.DungeonInstance;
import ru.elytrix.dungeons.model.DungeonStatus;
import ru.elytrix.dungeons.util.TextUtil;

/** Branded action bar that rotates through dungeon reset, ghost and loot timers. */
public final class DungeonAuctionBar {

    private final ElytrixDungeonsPlugin plugin;

    public DungeonAuctionBar(ElytrixDungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick(Collection<DungeonInstance> dungeons, long now) {
        if (!plugin.getConfig().getBoolean("auctionbar.enabled", true)) return;

        long cycleMs = Math.max(1L, plugin.getConfig().getLong("auctionbar.cycle-seconds", 15L)) * 1000L;
        int page = (int) ((now / cycleMs) % 3L);
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        for (Player player : players) {
            DungeonInstance active = findContaining(dungeons, player);
            if (active == null) continue;
            String line = switch (page) {
                case 0 -> resetLine(active, now);
                case 1 -> ghostsLine(active);
                default -> lootLine(active, now);
            };
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(TextUtil.color(line)));
        }
    }

    private static DungeonInstance findContaining(Collection<DungeonInstance> dungeons, Player player) {
        for (DungeonInstance dungeon : dungeons) {
            if (dungeon.getStatus() != DungeonStatus.REGENERATING
                    && dungeon.getBounds().contains(player.getLocation())) {
                return dungeon;
            }
        }
        return null;
    }

    private String resetLine(DungeonInstance dungeon, long now) {
        String fallback = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» "
                + "&7{dungeon} &8┃ &fПолный сброс через &#96FF91{timer}";
        return render("auctionbar.reset-line", fallback, dungeon)
                .replace("{timer}", TextUtil.formatDuration(dungeon.secondsUntilReset()));
    }

    private String ghostsLine(DungeonInstance dungeon) {
        int alive;
        int total;
        String groupLabel;
        if (dungeon.getCategory() == DungeonCategory.SURFACE) {
            alive = plugin.getGhostController().countAliveGhosts(dungeon.getId());
            total = Math.max(1, plugin.getConfig().getInt("surface-dungeon.ghosts.initial-count", 6));
            groupLabel = "Призраков осталось";
        } else {
            TrialController.ActiveFight fight = plugin.getTrialController().getFight(dungeon.getId());
            alive = fight == null ? 0 : fight.aliveMobs.size();
            total = fight == null
                    ? Math.max(1, plugin.getConfig().getInt("underground-dungeon.mob-count", 5)
                            + (plugin.getConfig().getBoolean("underground-dungeon.spawn-leader", true) ? 1 : 0))
                    : Math.max(1, fight.initialMobCount);
            groupLabel = "Стражей осталось";
        }

        String fallback = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» "
                + "&7{dungeon} &8┃ &f{ghost-label}: &#F8BEFB{ghosts}&7/{ghost-total}";
        String line = render("auctionbar.ghosts-line", fallback, dungeon)
                .replace(" &8┃ &f{ghost-state}", "")
                .replace("{ghost-state}", "")
                .replace("{ghost-label}", groupLabel)
                .replace("{ghosts}", String.valueOf(alive))
                .replace("{ghost-total}", String.valueOf(total));
        return line.replace("Призраков:", "Призраков осталось:");
    }

    private String lootLine(DungeonInstance dungeon, long now) {
        String fallback = "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &8» "
                + "&7{dungeon} &8┃ &fЛут шалкеров обновится через &#96FF91{timer}";
        return render("auctionbar.loot-line", fallback, dungeon)
                .replace("{timer}", TextUtil.formatDuration(secondsUntilLootRefresh(dungeon, now)));
    }

    private String render(String path, String fallback, DungeonInstance dungeon) {
        return plugin.getConfig().getString(path, fallback)
                .replace("{dungeon}", "Данж")
                .replace("{status}", dungeon.getStatus().getTitle());
    }

    private static long secondsUntilLootRefresh(DungeonInstance dungeon, long now) {
        if (dungeon.getCategory() == DungeonCategory.SURFACE) {
            long next = Long.MAX_VALUE;
            for (BlockPosKey pos : dungeon.getShulkerPositions()) {
                if (dungeon.isSurfaceShulkerLocked(pos)) continue;
                Long refreshAt = dungeon.getSurfaceShulkerRefreshAtMs().get(pos);
                if (refreshAt != null && refreshAt > 0L) next = Math.min(next, refreshAt);
            }
            if (next != Long.MAX_VALUE) return Math.max(0L, (next - now + 999L) / 1000L);
        }
        return dungeon.secondsUntilReset();
    }
}
