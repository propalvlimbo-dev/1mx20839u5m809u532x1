package ru.rooyzee.elytrixsubscribe.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.rooyzee.elytrixsubscribe.Main;

/**
 * Ввод текста через табличку — как было в старой версии плагина.
 *
 * Игрок нажимает кнопку в меню, у него открывается табличка, он пишет имя
 * и жмёт «Готово». Настоящий блок таблички ставится на пару секунд высоко
 * над игроком (в загруженном чанке) и сразу убирается, поэтому в мире ничего
 * не остаётся.
 */
public class SignInput implements Listener {
    private static final long TIMEOUT = 30L * 20L;

    private final Main plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<UUID, Pending>();

    public SignInput(Main plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        Consumer<String> callback;
        Block block;
        BlockData original;
        int task = -1;
    }

    /** Открыть табличку. false — поставить её не удалось (нет свободного места). */
    public boolean request(Player player, Consumer<String> callback) {
        if (player == null) return false;
        this.cancel(player.getUniqueId(), false);
        Block block = this.findSpot(player);
        if (block == null) return false;
        Pending data = new Pending();
        data.callback = callback;
        data.block = block;
        data.original = block.getBlockData();
        this.pending.put(player.getUniqueId(), data);
        try {
            block.setType(Material.OAK_SIGN, false);
            BlockState state = block.getState();
            if (!(state instanceof Sign)) {
                this.restore(data);
                this.pending.remove(player.getUniqueId());
                return false;
            }
            Sign sign = (Sign) state;
            sign.setWritable(true);
            sign.update(true, false);
            player.openSign(sign);
        } catch (Throwable error) {
            this.plugin.getLogger().warning("Табличка не открылась: " + error);
            this.restore(data);
            this.pending.remove(player.getUniqueId());
            return false;
        }
        data.task = Bukkit.getScheduler().runTaskLater(this.plugin,
                () -> this.cancel(player.getUniqueId(), true), TIMEOUT).getTaskId();
        return true;
    }

    /**
     * Свободное место рядом с игроком в загруженном чанке: сначала сверху,
     * потом чуть в сторону (в пещере сверху камень, зато рядом воздух).
     */
    private Block findSpot(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        int max = world.getMaxHeight() - 2;
        int[] heights = {4, 8, 12, 20, 30, 3};
        int[] sides = {0, 2, -2, 4, -4};
        for (int height : heights) {
            for (int side : sides) {
                for (int sideZ : sides) {
                    int y = base.getBlockY() + height;
                    if (y < world.getMinHeight() + 1 || y > max) continue;
                    int x = base.getBlockX() + side;
                    int z = base.getBlockZ() + sideZ;
                    // не грузим чужие чанки синхронно — только уже загруженные
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.AIR) return block;
                }
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Pending data = this.pending.remove(event.getPlayer().getUniqueId());
        if (data == null) return;
        event.setCancelled(true);
        if (data.task != -1) Bukkit.getScheduler().cancelTask(data.task);
        String text = "";
        for (String line : event.getLines()) {
            if (line != null && !line.trim().isEmpty()) {
                text = line.trim();
                break;
            }
        }
        this.restore(data);
        if (text.isEmpty()) {
            this.plugin.msg(event.getPlayer(), this.plugin.message("input-aborted",
                    "&7Ввод отменён."));
            return;
        }
        final String value = text;
        Bukkit.getScheduler().runTask(this.plugin, () -> data.callback.accept(value));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.cancel(event.getPlayer().getUniqueId(), false);
    }

    /** Убрать табличку и забыть про ввод. */
    public void cancel(UUID uuid, boolean notify) {
        Pending data = this.pending.remove(uuid);
        if (data == null) return;
        if (data.task != -1) Bukkit.getScheduler().cancelTask(data.task);
        this.restore(data);
        if (notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                this.plugin.msg(player, this.plugin.message("input-aborted", "&7Ввод отменён."));
            }
        }
    }

    public void shutdown() {
        for (UUID uuid : this.pending.keySet()) this.cancel(uuid, false);
    }

    private void restore(Pending data) {
        if (data.block == null || data.original == null) return;
        try {
            data.block.setBlockData(data.original, false);
        } catch (RuntimeException ignored) {
            try {
                data.block.setType(Material.AIR, false);
            } catch (RuntimeException ignoredToo) {
                // чанк уже выгрузился — блок уйдёт вместе с ним
            }
        }
        data.block = null;
    }
}
