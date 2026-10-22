package ru.rooyzee.elytrixsubscribe.pet;

import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.animal.VirtualBee;
import dev.by1337.virtualentity.api.virtual.animal.VirtualParrot;
import dev.by1337.virtualentity.api.virtual.animal.allay.VirtualAllay;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.by1337.blib.geom.Vec3d;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import ru.rooyzee.elytrixsubscribe.Main;
import ru.rooyzee.elytrixsubscribe.utils.Utils;

/**
 * Питомцы подписки: попугай, аллей и пчела.
 *
 * Животные не настоящие — это пакетные сущности VirtualEntityApi: их видят
 * все игроки рядом, но в мире они ничего не ломают, не толкаются, их нельзя
 * убить и они не попадают в /kill @e. Один игрок может включить только
 * одного питомца: включение второго выключает первого.
 */
public final class PetManager implements Listener {

    private static final double ORBIT_RADIUS = 1.4;
    private static final double ORBIT_SPEED = 0.055;
    private static final double MAX_STEP = 1.0;
    private static final double TELEPORT_DISTANCE = 15.0;
    private static final double MOVE_EPSILON = 0.05;
    private static final double CENTER_IDLE = 0.06;
    private static final double CENTER_MOVE = 0.14;
    private static final int VIEWERS_REFRESH = 10;
    private static final int HEAD_DELAY = 8;

    private final Main plugin;
    private final Map<UUID, EnumMap<PetType, PetData>> data =
            new ConcurrentHashMap<UUID, EnumMap<PetType, PetData>>();
    private final Map<UUID, PetInstance> pets = new ConcurrentHashMap<UUID, PetInstance>();
    /** Невидимая «кнопка» в мире → владелец: по ней ловим ПКМ по питомцу. */
    private final Map<UUID, UUID> hitboxes = new ConcurrentHashMap<UUID, UUID>();
    private int task = -1;
    private boolean unavailable;
    /** Interaction-хитбоксы не создаются (старое ядро) — не спамим в лог. */
    private boolean hitboxesBroken;
    /** Последняя ошибка создания питомца — показываем её игроку при включении. */
    private String lastError;
    private boolean errorLogged;

    public PetManager(Main plugin) {
        this.plugin = plugin;
        this.createTable();
    }

    public static List<PetType> types() {
        List<PetType> out = new ArrayList<PetType>();
        Collections.addAll(out, PetType.values());
        return out;
    }

    // ------------------------------------------------------------- хранение

    private void createTable() {
        this.plugin.getStorage().createSection("pets");
    }

    /** Загрузка при входе: у каждого питомца своя секция pets.<uuid>.<pet>. */
    public void load(UUID uuid) {
        EnumMap<PetType, PetData> map = new EnumMap<PetType, PetData>(PetType.class);
        for (PetType type : PetType.values()) map.put(type, new PetData(type));
        String base = "pets." + uuid;
        for (PetType type : PetType.values()) {
            String path = base + "." + type.id();
            PetData row = map.get(type);
            if (!this.plugin.getStorage().has(path)) continue;
            String name = this.plugin.getStorage().getString(path + ".name", null);
            row.setName(name);
            row.setVariant(this.plugin.getStorage().getInt(path + ".variant", 0));
            row.setBehavior(PetBehavior.byId(
                    this.plugin.getStorage().getString(path + ".behavior", null)));
            row.setActive(this.plugin.getStorage().getBoolean(path + ".active", false));
        }
        this.migrateLegacy(uuid, map);
        this.data.put(uuid, map);
    }

    /**
     * Перенос старых настроек попугая (parrot_settings из 1.x). Читаем
     * секцию legacy.<uuid>, если она осталась в data.yml.
     */
    private void migrateLegacy(UUID uuid, EnumMap<PetType, PetData> map) {
        boolean empty = true;
        for (PetData row : map.values()) {
            if (row.name() != null || row.variant() != 0 || row.active()
                    || row.behavior() != row.type().defaultBehavior()) {
                empty = false;
                break;
            }
        }
        if (!empty) return;
        String base = "legacy.parrot." + uuid;
        if (!this.plugin.getStorage().has(base)) return;
        PetData parrot = map.get(PetType.PARROT);
        parrot.setName(this.plugin.getStorage().getString(base + ".name", null));
        parrot.setVariant(this.plugin.getStorage().getInt(base + ".variant", 0));
        parrot.setBehavior(PetBehavior.byId(
                this.plugin.getStorage().getString(base + ".behavior", null)));
        parrot.setActive(true);
        this.saveRow(uuid, parrot);
        this.plugin.getStorage().remove("legacy.parrot." + uuid);
        this.plugin.getStorage().saveAsync();
    }

    public void unload(UUID uuid) {
        this.data.remove(uuid);
    }

    public void saveAll() {
        for (Map.Entry<UUID, EnumMap<PetType, PetData>> entry : this.data.entrySet()) {
            for (PetData row : entry.getValue().values()) {
                this.saveRow(entry.getKey(), row);
            }
        }
        this.plugin.getStorage().saveNow();
    }

    private void saveRow(UUID uuid, PetData row) {
        String path = "pets." + uuid + "." + row.type().id();
        this.plugin.getStorage().set(path + ".name", row.name());
        this.plugin.getStorage().set(path + ".variant", row.variant());
        this.plugin.getStorage().set(path + ".behavior", row.behavior().id());
        this.plugin.getStorage().set(path + ".active", row.active());
        this.plugin.getStorage().saveAsync();
    }

    public void save(Player player, PetType type) {
        this.saveRow(player.getUniqueId(), this.data(player, type));
    }

    // --------------------------------------------------------------- данные

    public EnumMap<PetType, PetData> rows(Player player) {
        UUID uuid = player.getUniqueId();
        EnumMap<PetType, PetData> map = this.data.get(uuid);
        if (map == null) {
            map = new EnumMap<PetType, PetData>(PetType.class);
            for (PetType type : PetType.values()) map.put(type, new PetData(type));
            this.data.put(uuid, map);
        }
        return map;
    }

    public PetData data(Player player, PetType type) {
        return this.rows(player).get(type);
    }

    public PetType activeType(Player player) {
        for (PetData row : this.rows(player).values()) {
            if (row.active()) return row.type();
        }
        return null;
    }

    public boolean isActive(Player player, PetType type) {
        return type != null && this.data(player, type).active();
    }

    /** Включить питомца: все остальные выключаются. */
    public void enable(Player player, PetType type) {
        PetType current = this.activeType(player);
        if (current == type) return;
        if (current != null && current != type) {
            this.data(player, current).setActive(false);
            this.save(player, current);
        }
        PetData row = this.data(player, type);
        row.setActive(true);
        this.save(player, type);
        this.despawn(player.getUniqueId());
        if (this.spawn(player)) {
            this.plugin.msg(player, this.plugin.message("pet-enabled",
                    "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fПитомец "
                            + "&#F8BEFB%pet% &fвключён.")
                    .replace("%pet%", type.displayName()));
        } else {
            row.setActive(false);
            this.save(player, type);
            String reason = this.lastError == null ? "нет VirtualEntityApi/BLib" : this.lastError;
            this.plugin.msg(player, "&cПитомец не появился: &7" + reason);
            this.plugin.msg(player, "&7Подробности — в консоли сервера.");
        }
    }

    public void disable(Player player, PetType type) {
        PetData row = this.data(player, type);
        if (!row.active()) return;
        row.setActive(false);
        this.save(player, type);
        this.despawn(player.getUniqueId());
        this.plugin.msg(player, this.plugin.message("pet-disabled",
                "&f☁ &#F8BEFBᴇ&#F6BEFBʟ&#F3BEFBʏ&#F1BFFBᴛ&#EEBFFBʀ&#ECBFFBɪ&#E9BFFBx &7» &fПитомец "
                        + "&#F8BEFB%pet% &fвыключен.")
                .replace("%pet%", type.displayName()));
    }

    /** Выключить того питомца, который сейчас включён (кончилась подписка). */
    public void disableActive(Player player) {
        PetType active = this.activeType(player);
        if (active == null) {
            this.despawn(player.getUniqueId());
            return;
        }
        this.disable(player, active);
    }

    /** ЛКМ по питомцу в меню: включить, если выключен, иначе выключить. */
    public void toggle(Player player, PetType type) {
        if (this.isActive(player, type)) {
            this.disable(player, type);
        } else {
            this.enable(player, type);
        }
    }

    /** Следующий режим поведения; true — если питомец включён и обновился. */
    public PetBehavior cycleBehavior(Player player, PetType type) {
        PetData row = this.data(player, type);
        row.setBehavior(type.next(row.behavior()));
        this.save(player, type);
        if (row.active()) {
            this.despawn(player.getUniqueId());
            this.spawn(player);
        }
        return row.behavior();
    }

    /** Следующий вариант окраса/свечения/размера. */
    public void cycleLook(Player player, PetType type) {
        PetData row = this.data(player, type);
        row.setVariant(type.nextLook(row.variant()));
        this.save(player, type);
        if (row.active()) {
            this.despawn(player.getUniqueId());
            this.spawn(player);
        }
    }

    public void setName(Player player, PetType type, String name) {
        PetData row = this.data(player, type);
        row.setName(name);
        this.save(player, type);
        this.plugin.getLogger().info("Питомец " + type.id() + " игрока " + player.getName()
                + ": имя = " + row.name() + ", включён = " + row.active());
        if (!row.active()) return;
        PetInstance instance = this.pets.get(player.getUniqueId());
        if (instance != null && instance.type == type) {
            // имя меняем на живой сущности: без мигания и без риска не заспавниться
            try {
                if (row.name() == null) {
                    instance.entity.setCustomNameVisible(false);
                } else {
                    instance.entity.setCustomName(this.component(row.name()));
                    instance.entity.setCustomNameVisible(true);
                }
                instance.entity.tick(instance.viewers);
                // переслать сущность с уже новым именем: так табличка точно обновится
                instance.entity.respawn();
                return;
            } catch (RuntimeException ignored) {
                // не получилось на живой — пересоздадим ниже
            }
        }
        this.despawn(player.getUniqueId());
        this.spawn(player);
    }

    // -------------------------------------------------------------- сущность

    /** Питомцы выключены в конфиге или нет VirtualEntityApi. */
    public boolean available() {
        if (!this.plugin.getConfig().getBoolean("pets.enabled", true)) return false;
        if (this.unavailable) return false;
        try {
            Class.forName("dev.by1337.virtualentity.api.VirtualEntityApi");
            Class.forName("org.by1337.blib.geom.Vec3d");
        } catch (Throwable error) {
            if (this.lastError == null) {
                this.lastError = "не найдены классы VirtualEntityApi/BLib (" + error + ")";
            }
            return false;
        }
        return true;
    }

    public void start() {
        if (this.task != -1) return;
        int interval = Math.max(1, this.plugin.getConfig().getInt("pets.tick-interval", 2));
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickAll, 1L, interval).getTaskId();
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<UUID>(this.pets.keySet())) this.despawn(uuid);
        this.hitboxes.clear();
        if (this.task != -1) {
            Bukkit.getScheduler().cancelTask(this.task);
            this.task = -1;
        }
        this.saveAll();
    }

    public boolean spawn(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (!this.available()) return false;
        if (!this.plugin.getSubManager().hasSub(player)) return false;
        PetType type = this.activeType(player);
        if (type == null) return false;
        PetData row = this.data(player, type);
        try {
            PetInstance instance = new PetInstance(type, player.getWorld().getName());
            instance.entity = this.createEntity(row);
            Location start = player.getLocation().clone().add(ORBIT_RADIUS, 1.2, 0);
            instance.entity.setPos(new Vec3d(start.getX(), start.getY(), start.getZ()));
            instance.center[0] = player.getLocation().getX();
            instance.center[1] = player.getLocation().getY();
            instance.center[2] = player.getLocation().getZ();
            instance.last = player.getLocation().clone();
            instance.viewers = this.viewers(player);
            instance.entity.tick(instance.viewers);
            this.pets.put(player.getUniqueId(), instance);
            this.spawnHitbox(player, instance, start);
            return true;
        } catch (Throwable error) {
            // не блокируем питомцев навсегда: VirtualEntityApi мог ещё не
            // зарегистрировать фабрику — следующий клик попробует снова
            this.lastError = String.valueOf(error);
            if (!this.errorLogged) {
                this.errorLogged = true;
                this.plugin.getLogger().warning("Питомцы не создались (" + error
                        + ") — проверь VirtualEntityApi и BLib.");
                error.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Пакетную сущность сервер не знает, поэтому ПКМ по ней до Bukkit не доходит.
     * Рядом с питомцем живёт настоящая (но невидимая) Interaction-хитбокс:
     * клиент по ней кликает, сервер вызывает PlayerInteractEntityEvent, и мы
     * открываем личные настройки этого питомца.
     */
    private void spawnHitbox(Player player, PetInstance instance, Location at) {
        if (this.hitboxesBroken) return;
        if (!this.plugin.getConfig().getBoolean("pets.clickable", true)) return;
        try {
            Interaction hitbox = player.getWorld().spawn(at, Interaction.class);
            hitbox.setInteractionWidth(0.9F);
            hitbox.setInteractionHeight(1.1F);
            hitbox.setResponsive(true);
            instance.hitbox = hitbox;
            this.hitboxes.put(hitbox.getUniqueId(), player.getUniqueId());
        } catch (Throwable error) {
            this.hitboxesBroken = true;
            this.plugin.getLogger().warning("ПКМ по питомцу недоступен: " + error);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        UUID owner = this.hitboxes.get(event.getRightClicked().getUniqueId());
        if (owner == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(owner)) return;
        PetInstance instance = this.pets.get(owner);
        if (instance == null) return;
        this.plugin.getMenuService().openPet(player, instance.type);
    }

    private VirtualEntity createEntity(PetData row) {
        VirtualEntity entity;
        if (row.type() == PetType.PARROT) {
            VirtualParrot parrot = VirtualParrot.create();
            parrot.setVariant(Math.abs(row.variant()) % 5);
            parrot.setTame(true);
            entity = parrot;
        } else if (row.type() == PetType.ALLAY) {
            VirtualAllay allay = VirtualAllay.create();
            allay.setDancing(row.behavior() == PetBehavior.DANCE);
            allay.setCanDuplicate(false);
            entity = allay;
        } else {
            VirtualBee bee = VirtualBee.create();
            bee.setBaby(row.variant() > 0);
            bee.setHasStung(false);
            bee.setAngerEndTime(0L);
            entity = bee;
        }
        entity.setNoGravity(true);
        entity.setOnGround(false);
        entity.setSilent(true);
        if (row.name() != null && !row.name().isEmpty()) {
            entity.setCustomName(this.component(row.name()));
            entity.setCustomNameVisible(true);
        } else {
            entity.setCustomNameVisible(false);
        }
        return entity;
    }

    private Component component(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(Utils.color(text));
    }

    public void despawn(UUID uuid) {
        PetInstance instance = this.pets.remove(uuid);
        if (instance == null) return;
        if (instance.hitbox != null) {
            this.hitboxes.remove(instance.hitbox.getUniqueId());
            try {
                instance.hitbox.remove();
            } catch (RuntimeException ignored) {
                // хитбокс уже выгрузился вместе с чанком
            }
            instance.hitbox = null;
        }
        try {
            instance.entity.tick(Collections.<Player>emptySet());
            instance.entity.removeAllTickTask();
        } catch (RuntimeException error) {
            this.plugin.getLogger().warning("Питомец не убрался: " + error);
        }
    }

    /** Пересоздать питомца (после телепорта, смерти, смены мира). */
    public void restart(Player player) {
        if (player == null) return;
        this.despawn(player.getUniqueId());
        this.spawn(player);
    }

    // ----------------------------------------------------------------- тик

    private Set<Player> viewers(Player owner) {
        Set<Player> out = new HashSet<Player>();
        double distance = this.plugin.getConfig().getDouble("pets.view-distance", 64.0);
        double limit = distance * distance;
        Location loc = owner.getLocation();
        for (Player other : owner.getWorld().getPlayers()) {
            if (other.getLocation().distanceSquared(loc) <= limit) out.add(other);
        }
        if (!out.contains(owner)) out.add(owner);
        return out;
    }

    private void tickAll() {
        if (this.pets.isEmpty()) return;
        for (UUID uuid : new ArrayList<UUID>(this.pets.keySet())) {
            PetInstance instance = this.pets.get(uuid);
            if (instance == null) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                this.despawn(uuid);
                continue;
            }
            try {
                this.tick(uuid, player, instance);
            } catch (RuntimeException error) {
                this.plugin.getLogger().warning("Питомец " + instance.type.id() + ": " + error);
                this.despawn(uuid);
            }
        }
    }

    private void tick(UUID uuid, Player player, PetInstance instance) {
        Location playerLoc = player.getLocation();
        if (!player.getWorld().getName().equals(instance.world)) {
            this.restart(player);
            return;
        }
        instance.ticks += 2;
        if (++instance.viewerTimer >= VIEWERS_REFRESH) {
            instance.viewerTimer = 0;
            instance.viewers = this.viewers(player);
        }

        Location last = instance.last == null ? playerLoc : instance.last;
        boolean moving = playerLoc.distanceSquared(last) > MOVE_EPSILON * MOVE_EPSILON;
        instance.last = playerLoc.clone();

        Vec3d position = instance.entity.getPos();
        double dx = position.getX() - playerLoc.getX();
        double dy = position.getY() - playerLoc.getY();
        double dz = position.getZ() - playerLoc.getZ();

        if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            Location spot = playerLoc.clone().add(ORBIT_RADIUS, 1.2, 0);
            instance.entity.setPos(new Vec3d(spot.getX(), spot.getY(), spot.getZ()));
            instance.entity.setNoMotion();
            instance.center[0] = playerLoc.getX();
            instance.center[1] = playerLoc.getY();
            instance.center[2] = playerLoc.getZ();
            this.syncHitbox(player, instance);
            instance.entity.tick(instance.viewers);
            return;
        }

        PetData row = this.data(player, instance.type);
        Location target = this.target(instance, row, player, playerLoc, moving);

        double tx = target.getX() - position.getX();
        double ty = target.getY() - position.getY();
        double tz = target.getZ() - position.getZ();
        double distance = Math.sqrt(tx * tx + ty * ty + tz * tz);

        double moveX = 0;
        double moveY = 0;
        double moveZ = 0;
        if (distance > 0.001) {
            double step = Math.min(MAX_STEP, distance);
            double factor = step / distance;
            moveX = tx * factor;
            moveY = ty * factor;
            moveZ = tz * factor;
            instance.entity.setPos(new Vec3d(position.getX() + moveX, position.getY() + moveY,
                    position.getZ() + moveZ));
        }
        instance.entity.setMotion(new Vec3d(moveX, moveY, moveZ));

        if (row.behavior() == PetBehavior.HEAD) {
            instance.entity.setYaw(playerLoc.getYaw());
            instance.entity.setPitch(playerLoc.getPitch());
        } else if (Math.abs(moveX) > 0.01 || Math.abs(moveZ) > 0.01) {
            instance.entity.setYaw((float) Math.toDegrees(Math.atan2(-moveX, moveZ)));
        }

        this.extras(instance, row);
        this.syncHitbox(player, instance);
        instance.entity.tick(instance.viewers);
    }

    /** Куда питомец хочет попасть в этом тике. */
    private Location target(PetInstance instance, PetData row, Player player, Location playerLoc,
                            boolean moving) {
        Vector direction = playerLoc.getDirection().setY(0);
        if (direction.lengthSquared() > 0.001) direction.normalize();
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());

        switch (row.behavior()) {
            case HEAD: {
                if (!moving) {
                    instance.stillTicks += 2;
                } else {
                    instance.stillTicks = 0;
                }
                if (instance.stillTicks >= HEAD_DELAY) {
                    return player.getEyeLocation().add(0, 0.35, 0);
                }
                instance.bob += 0.15;
                Location above = playerLoc.clone().add(direction.clone().multiply(-0.3))
                        .add(0, 2.2 + Math.sin(instance.bob) * 0.05, 0);
                return above;
            }
            case HOVER: {
                instance.bob += 0.05;
                return playerLoc.clone().add(right.multiply(1.5))
                        .add(0, 1.3 + Math.sin(instance.bob) * 0.08, 0);
            }
            case BEHIND: {
                instance.bob += 0.04;
                return playerLoc.clone().add(direction.clone().multiply(-2.0))
                        .add(0, 1.6 + Math.sin(instance.bob) * 0.06, 0);
            }
            case DANCE: {
                instance.angle += 0.16;
                this.moveCenter(instance, playerLoc, moving, 0.22);
                double x = instance.center[0] + Math.cos(instance.angle) * 1.6;
                double z = instance.center[2] + Math.sin(instance.angle) * 1.6;
                double y = playerLoc.getY() + 1.4 + Math.sin(instance.angle * 3.0) * 0.35;
                return new Location(player.getWorld(), x, y, z);
            }
            case ZIGZAG: {
                instance.angle += 0.11;
                this.moveCenter(instance, playerLoc, moving, CENTER_IDLE);
                double side = Math.sin(instance.angle * 3.0) * 1.8;
                double forward = Math.cos(instance.angle * 1.5) * 0.7;
                Location base = new Location(player.getWorld(), instance.center[0],
                        playerLoc.getY() + 1.5 + Math.sin(instance.angle * 6.0) * 0.15,
                        instance.center[2]);
                return base.add(right.clone().multiply(side)).add(direction.clone().multiply(forward));
            }
            case ROLL: {
                instance.bob += 0.06;
                return playerLoc.clone().add(right.multiply(-1.4))
                        .add(0, 1.5 + Math.sin(instance.bob) * 0.25, 0);
            }
            default: {
                instance.angle += ORBIT_SPEED * 2;
                this.moveCenter(instance, playerLoc, moving, moving ? CENTER_MOVE : CENTER_IDLE);
                double x = instance.center[0] + Math.cos(instance.angle) * ORBIT_RADIUS;
                double z = instance.center[2] + Math.sin(instance.angle) * ORBIT_RADIUS;
                double y = playerLoc.getY() + 1.15 + Math.sin(instance.angle * 2.0) * 0.15;
                return new Location(player.getWorld(), x, y, z);
            }
        }
    }

    private void moveCenter(PetInstance instance, Location playerLoc, boolean moving, double lerp) {
        double tx = playerLoc.getX();
        double tz = playerLoc.getZ();
        if (moving) {
            Vector delta = playerLoc.toVector().subtract(instance.last.toVector()).setY(0);
            if (delta.lengthSquared() > 0.001) {
                delta.normalize().multiply(1.2);
                tx += delta.getX();
                tz += delta.getZ();
            }
        }
        instance.center[0] = instance.center[0] + (tx - instance.center[0]) * lerp;
        instance.center[1] = playerLoc.getY();
        instance.center[2] = instance.center[2] + (tz - instance.center[2]) * lerp;
    }

    /** Особые движения: кувырок пчелы и танец аллея. */
    private void extras(PetInstance instance, PetData row) {
        if (!(instance.entity instanceof VirtualBee)) return;
        VirtualBee bee = (VirtualBee) instance.entity;
        if (row.behavior() == PetBehavior.ROLL) {
            boolean roll = (instance.ticks % 60) < 14;
            if (bee.isRolling() != roll) bee.setRolling(roll);
        }
    }

    /** Держим хитбокс под питомцем (телепорт только если реально сдвинулся). */
    private void syncHitbox(Player player, PetInstance instance) {
        Vec3d pos = instance.entity.getPos();
        Location at = new Location(player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
        if (instance.hitbox != null && !instance.hitbox.isValid()) {
            // чанк выгрузился или хитбокс убили — ставим заново
            this.hitboxes.remove(instance.hitbox.getUniqueId());
            instance.hitbox = null;
        }
        if (instance.hitbox == null) {
            this.spawnHitbox(player, instance, at);
            return;
        }
        try {
            if (instance.hitbox.getLocation().distanceSquared(at) < 0.01) return;
            instance.hitbox.teleport(at);
        } catch (RuntimeException ignored) {
            // мир успел смениться — хитбокс пересоздастся при респавне питомца
        }
    }

    // -------------------------------------------------------------- события

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        this.load(event.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.spawn(player), 10L);
        // вторая попытка: VirtualEntityApi иногда дорегистрирует фабрику позже
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!this.pets.containsKey(player.getUniqueId())) this.spawn(player);
        }, 100L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.despawn(event.getPlayer().getUniqueId());
        this.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        this.despawn(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restart(player), 10L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restart(player), 10L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PetInstance instance = this.pets.get(player.getUniqueId());
        if (instance == null) return;
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;
        if (!to.getWorld().getName().equals(instance.world)) return;
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            PetInstance current = this.pets.get(player.getUniqueId());
            if (current == null) return;
            Location dest = to;
            current.entity.setPos(new Vec3d(dest.getX() + ORBIT_RADIUS, dest.getY() + 1.2, dest.getZ()));
            current.entity.setNoMotion();
            current.center[0] = dest.getX();
            current.center[1] = dest.getY();
            current.center[2] = dest.getZ();
            current.last = dest.clone();
            current.viewers = this.viewers(player);
            current.entity.tick(current.viewers);
        }, 1L);
    }

    // --------------------------------------------------------------- данные для меню

    public String statusLine(Player player, PetType type) {
        return this.isActive(player, type)
                ? this.plugin.setting("functionEnable", "&aВключена")
                : this.plugin.setting("functionDisable", "&cВыключена");
    }

    public String nameLine(Player player, PetType type) {
        String name = this.data(player, type).name();
        return name == null ? "&7не задано" : name;
    }

    public String behaviorLine(Player player, PetType type) {
        return this.data(player, type).behavior().label();
    }

    public String lookLine(Player player, PetType type) {
        return this.data(player, type).lookLabel();
    }

    public String activeLine(Player player) {
        PetType active = this.activeType(player);
        return active == null ? "&7нет" : active.displayName();
    }

    /** Служебный класс: живой питомец игрока. */
    private static final class PetInstance {
        final PetType type;
        final String world;
        VirtualEntity entity;
        Interaction hitbox;
        final double[] center = new double[3];
        Location last;
        Set<Player> viewers = Collections.emptySet();
        int viewerTimer;
        int stillTicks;
        int ticks;
        double angle;
        double bob;

        PetInstance(PetType type, String world) {
            this.type = type;
            this.world = world;
        }
    }
}
