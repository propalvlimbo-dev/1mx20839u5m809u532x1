package ru.rooyzee.elytrixcasino.automate;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixcasino.Main;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AutomateManager {

    /**
     * Насколько точно игрок должен смотреть «сквозь» соседний блок на автомат,
     * чтобы клик по этому блоку засчитался: косинус угла, 0.5 ≈ конус 60°.
     */
    private static final double MIN_LOOK_DOT = 0.5D;

    private final Main plugin;
    private final Map<String, Automate> automates = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingBets = new HashMap<>();

    public AutomateManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Регистрирует автомат. Вывеска поднимется сразу, если чанк загружен,
     * и вместе с чанком, если ещё нет (см. {@link #onChunkLoad(Chunk)}).
     */
    public Automate spawn(String id, Location bottom, long bet) {
        Automate previous = automates.remove(id);
        if (previous != null) {
            previous.hideHologram();
        }
        Automate automate = new Automate(plugin, id, bottom, bet);
        if (!automate.isValid()) {
            plugin.getLogger().warning("Автомат '" + id + "' не создан: мир '"
                    + (bottom.getWorld() == null ? "?" : bottom.getWorld().getName()) + "' недоступен.");
            return null;
        }
        automates.put(id, automate);
        automate.refreshHologram();
        return automate;
    }

    /**
     * Снять автомат, не трогая data.yml (например, при выгрузке мира).
     */
    public Automate unload(String id) {
        Automate automate = automates.remove(id);
        if (automate != null) {
            automate.hideHologram();
        }
        return automate;
    }

    /**
     * Чанк загрузился — показываем вывески автоматов, которые в нём стоят.
     * Спавним на следующем тике: чанк должен окончательно подняться,
     * иначе сущность может не добавиться в мир.
     */
    public void onChunkLoad(Chunk chunk) {
        final List<Automate> inChunk = new ArrayList<>();
        for (Automate automate : automates.values()) {
            if (automate.isInChunk(chunk)) {
                inChunk.add(automate);
            }
        }
        if (inChunk.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            for (Automate automate : inChunk) {
                if (automates.get(automate.getId()) == automate) {
                    automate.refreshHologram();
                }
            }
        });
    }

    /**
     * Чанк выгружается — убираем вывески, чтобы армор-стенды не уезжали в сейв мира
     * и не дублировались после перезагрузки.
     */
    public void onChunkUnload(Chunk chunk) {
        for (Automate automate : new ArrayList<>(automates.values())) {
            if (!automate.isInChunk(chunk)) {
                continue;
            }
            automate.hideHologram();
            // выгрузку могут отменить (плагины, держащие чанки казино) — тогда вывеску надо вернуть
            final String id = automate.getId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                Automate current = automates.get(id);
                if (current == automate && current.isChunkLoaded() && !current.isHologramShown()) {
                    current.refreshHologram();
                }
            });
        }
    }

    public void beginCreate(Player player, long bet) {
        pendingBets.put(player.getUniqueId(), Math.max(1L, bet));
    }

    public boolean isCreating(Player player) {
        return pendingBets.containsKey(player.getUniqueId());
    }

    public void cancelCreate(Player player) {
        pendingBets.remove(player.getUniqueId());
    }

    public Automate completeCreate(Player player, Block clicked) {
        Long bet = pendingBets.remove(player.getUniqueId());
        if (bet == null) {
            return null;
        }
        if (clicked == null || clicked.getType().isAir()) {
            pendingBets.put(player.getUniqueId(), bet);
            return null;
        }
        Block bottom = clicked.getRelative(BlockFace.DOWN);
        if (bottom.getType().isAir()) {
            pendingBets.put(player.getUniqueId(), bet);
            return null;
        }
        if (findByBlock(clicked.getLocation()) != null) {
            pendingBets.put(player.getUniqueId(), bet);
            return null;
        }
        String id = plugin.getDataManager().newId();
        Automate automate = spawn(id, bottom.getLocation(), bet);
        if (automate == null) {
            return null;
        }
        plugin.getDataManager().saveAutomate(id, automate.getLocation(), bet);
        return automate;
    }

    public boolean remove(String id) {
        Automate automate = unload(id);
        plugin.getDataManager().removeAutomate(id);
        return automate != null;
    }

    public void removeAll() {
        unloadAll();
    }

    public void unloadAll() {
        for (Automate automate : new ArrayList<>(automates.values())) {
            automate.hideHologram();
        }
        automates.clear();
        pendingBets.clear();
    }

    public Automate get(String id) {
        return automates.get(id);
    }

    public Collection<Automate> getAll() {
        return Collections.unmodifiableCollection(automates.values());
    }

    public Set<String> getIds() {
        return Collections.unmodifiableSet(automates.keySet());
    }

    public Automate findByBlock(Location location) {
        for (Automate automate : automates.values()) {
            if (automate.isBlock(location)) {
                return automate;
            }
        }
        return null;
    }

    /**
     * Автомат, стоящий вплотную ЗА указанным блоком со стороны игрока: клик по такому блоку
     * («+1 блок вперёд», только по прямой) тоже должен открывать меню.
     * <p>
     * Направление автомата нигде не сохранено, поэтому «перёд» определяется в момент клика:
     * берём горизонтальное направление взгляда игрока и требуем, чтобы автомат находился
     * примерно по этому направлению за кликнутым блоком. Благодаря этому свойство сразу
     * действует и на все уже созданные автоматы, без миграции data.yml.
     *
     * @return ближайший по направлению взгляда автомат либо {@code null}
     */
    public Automate findBehindBlock(Player player, Location location) {
        if (player == null || location == null) {
            return null;
        }
        Vector look = player.getEyeLocation().getDirection();
        double lookX = look.getX();
        double lookZ = look.getZ();
        double length = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (length < 1.0E-6D) {
            // игрок смотрит строго вверх или вниз — «вперёд» не определить
            return null;
        }
        lookX /= length;
        lookZ /= length;

        Automate best = null;
        double bestDot = MIN_LOOK_DOT;
        for (Automate automate : automates.values()) {
            BlockFace face = automate.adjacentFace(location);
            if (face == null) {
                continue;
            }
            // направление от кликнутого блока к автомату
            double dot = lookX * -face.getModX() + lookZ * -face.getModZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = automate;
            }
        }
        return best;
    }
}
