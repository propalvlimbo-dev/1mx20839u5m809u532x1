package ru.rooyzee.elytrixtrader.virtual;

import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.by1337.blib.geom.Vec3d;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TraderConfig;
import ru.rooyzee.elytrixtrader.util.ColorUtil;
import ru.rooyzee.elytrixtrader.util.Components;

import java.util.ArrayList;
import java.util.List;

/**
 * ЕДИНСТВЕННОЕ место плагина, где используется библиотека VirtualEntity.
 *
 * Владеет сущностями одного торговца:
 *  - {@link VirtualPlayer} — сам NPC;
 *  - TextDisplay — прозрачная голограмма на современных версиях;
 *  - {@link VirtualArmorStand} и общий {@link PlayerTracker} — fallback для
 *    старых серверов без TextDisplay.
 *
 * Никаких VirtualExperienceOrb здесь нет и быть не должно: в 1.16.5 клиент
 * сам «тикает» XP-орбы (магнит к игрокам в радиусе 8 блоков — ванильный код
 * ExperienceOrbEntity.tick, флагом он не отключается), поэтому любые
 * виртуальные орбы дёргаются и улетают к игрокам. Декоративные эффекты
 * живут на частицах — см. {@link AuraEffect}.
 *
 * Жизненный цикл: {@code create -> spawn() -> tick() каждую итерацию ->
 * destroy()}. После destroy объект мёртв и пересоздаётся заново.
 */
public final class TraderNpc {

    private final Main plugin;
    private final TraderConfig config;
    private final Location location; // позиция НОГ npc (авторитетная, мутируется setPos)

    private VirtualPlayer player;
    private final List<VirtualArmorStand> holograms = new ArrayList<>();
    private Entity transparentHologram;
    private final NamespacedKey hologramMarkerKey;
    private final String hologramMarkerValue;
    private boolean transparentHologramMode;
    private boolean hologramHidden;
    private PlayerTracker tracker;

    private boolean spawned;

    // ── Плавный поворот головы ─────────────────────────────────────────
    // Пакеты поворота отправляем только когда угол реально изменился
    // (dead-zone 0.05°) — в статике ноль пакетов.
    private float currentYaw;
    private float currentPitch;
    private float targetYaw;
    private float targetPitch;
    private float lastSentYaw = -9999.0f;
    private float lastSentPitch = -9999.0f;

    // Кеш текста голограммы: обновляем имена только когда текст изменился.
    private String lastHologramState;

    public TraderNpc(Main plugin, TraderConfig config, Location location) {
        this.plugin = plugin;
        this.config = config;
        this.location = location.clone();
        this.hologramMarkerKey = new NamespacedKey(plugin, "trader_hologram");
        World world = this.location.getWorld();
        this.hologramMarkerValue = config.id() + ":"
                + (world == null ? "unknown" : world.getName()) + ":"
                + this.location.getBlockX() + ":" + this.location.getBlockY() + ":"
                + this.location.getBlockZ();
        this.currentYaw = this.location.getYaw();
        this.currentPitch = 0.0f;
        this.targetYaw = currentYaw;
        this.targetPitch = currentPitch;
    }

    // ─────────────────────────────────────────────────────────────────
    // Жизненный цикл
    // ─────────────────────────────────────────────────────────────────

    public void spawn() {
        if (spawned) return;
        World world = location.getWorld();
        if (world == null) return;

        player = VirtualPlayer.create();
        player.setPos(new Vec3d(location));
        player.setYaw(location.getYaw());
        player.setPitch(0.0F);
        player.setNoGravity(true);   // NPC стоит ровно, его двигает только плагин
        player.setSilent(true);
        player.setCustomNameVisible(false);
        hideIdentity();

        if (config.holdItem() && config.icon().material() != null && config.icon().material().isItem()) {
            heldItem(new ItemStack(config.icon().material(), 1));
        }

        List<VirtualEntity> entities = new ArrayList<>();
        entities.add(player);
        hologramHidden = false;
        lastHologramState = null;
        transparentHologramMode = false;
        if (plugin.config().hologramEnabled() && config.hologramEnabled()
                && !config.hologramLines().isEmpty()) {
            transparentHologramMode = spawnTransparentHologram();
            if (!transparentHologramMode) {
                // TextDisplay is available on modern servers; retain the 1.16-era fallback otherwise.
                buildHolograms(entities);
            }
        }

        tracker = new PlayerTracker(world, entities, new Vec3d(location), trackerRadius());
        spawned = true;

        // Скрыть из таба чуть позже — после того как клиент получит spawn-пакеты.
        Bukkit.getScheduler().runTaskLater(plugin, this::hideFromTabForAll, 15L);
    }

    /** Ни ника, ни строки в табе, ни лишней информации о «игроке». */
    private void hideIdentity() {
        try { player.setName(""); } catch (Throwable ignored) {}
        try {
            Component empty = Components.empty();
            if (empty != null) player.setDisplayName(empty);
        } catch (Throwable ignored) {}
        try { player.setListed(false); } catch (Throwable ignored) {}      // 1.19.4+
        try { player.hideFromTab(); } catch (Throwable ignored) {}
        try { player.setLatency(0); } catch (Throwable ignored) {}
        try { player.setGameMode(org.bukkit.GameMode.SURVIVAL); } catch (Throwable ignored) {}
        try { player.setPlayerModeCustomisation((byte) 0x00); } catch (Throwable ignored) {}
    }

    public void tick() {
        if (!spawned) return;
        if (tracker != null) {
            try { tracker.tick(); } catch (Throwable ignored) {}
        }
        updateSmoothRotation();
    }

    /** Принудительно прогнать трекер (используется анимацией исчезновения). */
    public void tickViewers() {
        if (tracker != null) {
            try { tracker.tick(); } catch (Throwable ignored) {}
        }
    }

    public void destroy() {
        if (!spawned) return;
        spawned = false;
        removeTransparentHologram();
        transparentHologramMode = false;
        if (tracker != null) {
            try { tracker.removeAll(); } catch (Throwable ignored) {}
            tracker = null;
        }
        removeFromTabForAll();
        holograms.clear();
        player = null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Внешний вид
    // ─────────────────────────────────────────────────────────────────

    /** Применяет скин. Вызывает respawn — entityId может измениться! */
    public void applySkin(String value, String signature) {
        if (!spawned || player == null) return;
        try {
            player.setTexture(value, signature);
            player.respawn();
            // respawn снова показывает строку в табе — прячем и пере-прячем
            Bukkit.getScheduler().runTaskLater(plugin, this::hideFromTabForAll, 2L);
        } catch (Throwable ignored) {}
    }

    public void heldItem(ItemStack item) {
        if (player == null || item == null) return;
        try { player.setEquipment(EquipmentSlot.MAINHAND, item); } catch (Throwable ignored) {}
    }

    public void setInvisible(boolean invisible) {
        if (player == null) return;
        try { player.setInvisible(invisible); } catch (Throwable ignored) {}
    }

    public void swing() {
        if (player == null) return;
        try { player.playAnimation(EntityAnimation.SWING_MAIN_ARM); } catch (Throwable ignored) {}
    }

    public void raiseHands() {
        if (player == null) return;
        try {
            player.playAnimation(EntityAnimation.SWING_MAIN_ARM);
            player.playAnimation(EntityAnimation.SWING_OFFHAND);
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    // Позиция и взгляд
    // ─────────────────────────────────────────────────────────────────

    public void setPos(Location newLoc) {
        if (player == null) return;
        try {
            player.setPos(new Vec3d(newLoc));
            location.setX(newLoc.getX());
            location.setY(newLoc.getY());
            location.setZ(newLoc.getZ());
            location.setYaw(newLoc.getYaw());
            location.setPitch(newLoc.getPitch());
        } catch (Throwable ignored) {}
    }

    public void lookAt(Player target) {
        if (player == null || target == null) return;
        Location from = location.clone().add(0, 1.5, 0);      // голова NPC
        Location to = target.getLocation().clone().add(0, 1.6, 0);
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));
        setTargetLook(yaw, pitch);
    }

    public void setTargetLook(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = Math.max(-60, Math.min(60, pitch));
    }

    private void updateSmoothRotation() {
        if (player == null) return;
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float yawStep = yawDiff * 0.15f;
        if (Math.abs(yawStep) > 8) yawStep = Math.signum(yawStep) * 8;
        if (Math.abs(yawDiff) < 0.5f) currentYaw = targetYaw;
        else currentYaw += yawStep;

        float pitchDiff = targetPitch - currentPitch;
        float pitchStep = pitchDiff * 0.15f;
        if (Math.abs(pitchStep) > 5) pitchStep = Math.signum(pitchStep) * 5;
        if (Math.abs(pitchDiff) < 0.5f) currentPitch = targetPitch;
        else currentPitch += pitchStep;

        while (currentYaw > 180) currentYaw -= 360;
        while (currentYaw < -180) currentYaw += 360;

        boolean changed = Math.abs(currentYaw - lastSentYaw) >= 0.05f
                || Math.abs(currentPitch - lastSentPitch) >= 0.05f;
        if (!changed) return; // стоим ровно — пакеты не шлём
        lastSentYaw = currentYaw;
        lastSentPitch = currentPitch;

        try {
            player.setYaw(currentYaw);
            player.setPitch(currentPitch);
        } catch (Throwable ignored) {
            // Запасной путь для версий библиотеки без setYaw/setPitch:
            // смотрим в точку «голова + направление взгляда * 10».
            try {
                double radYaw = Math.toRadians(currentYaw);
                double radPitch = Math.toRadians(currentPitch);
                double dirX = -Math.sin(radYaw) * Math.cos(radPitch);
                double dirY = -Math.sin(radPitch);
                double dirZ = Math.cos(radYaw) * Math.cos(radPitch);
                player.lookAt(new Vec3d(
                        location.getX() + dirX * 10,
                        location.getY() + 1.5 + dirY * 10,
                        location.getZ() + dirZ * 10));
            } catch (Throwable ignored2) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Голограмма
    // ─────────────────────────────────────────────────────────────────

    private void buildHolograms(List<VirtualEntity> entities) {
        List<String> lines = config.hologramLines();
        double baseY = location.getY() + config.hologramHeight();
        for (int index = 0; index < lines.size(); index++) {
            VirtualArmorStand stand = VirtualArmorStand.create();
            stand.setPos(new Vec3d(location.getX(), baseY - index * config.hologramLineHeight(), location.getZ()));
            stand.setMarker(true);
            stand.setNoBasePlate(true);
            stand.setSmall(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setInvisible(true);
            stand.setCustomNameVisible(true);
            holograms.add(stand);
            entities.add(stand);
        }
    }

    /**
     * Uses a real TextDisplay on modern servers so trader holograms can have a
     * fully transparent background. Older servers fall back to virtual stands.
     */
    private boolean spawnTransparentHologram() {
        Location displayLocation = location.clone().add(0.0D, config.hologramHeight(), 0.0D);
        World world = displayLocation.getWorld();
        if (world == null || !world.isChunkLoaded(
                displayLocation.getBlockX() >> 4, displayLocation.getBlockZ() >> 4)) return false;

        Entity display = findExistingTextDisplay(world, displayLocation);
        if (display == null) {
            EntityType textDisplayType;
            try {
                textDisplayType = EntityType.valueOf("TEXT_DISPLAY");
            } catch (Throwable ignored) {
                return false;
            }
            try {
                display = world.spawnEntity(displayLocation, textDisplayType);
            } catch (Throwable ignored) {
                return false;
            }
        } else if (display.getLocation().distanceSquared(displayLocation) > 0.01D) {
            display.teleport(displayLocation);
        }

        try {
            display.setPersistent(false);
            display.getPersistentDataContainer().set(
                    hologramMarkerKey, PersistentDataType.STRING, hologramMarkerValue);
            configureTransparentTextDisplay(display, config.hologramLines());
            transparentHologram = display;
            lastHologramState = hologramState(config.hologramLines());
            return true;
        } catch (Throwable ignored) {
            try { display.remove(); } catch (Throwable ignoredAgain) {}
            transparentHologram = null;
            return false;
        }
    }

    private Entity findExistingTextDisplay(World world, Location at) {
        Entity found = null;
        try {
            for (Entity entity : world.getNearbyEntities(at, 1.5D, 2.5D, 1.5D)) {
                if (!"TEXT_DISPLAY".equals(entity.getType().name())) continue;
                String marker = entity.getPersistentDataContainer()
                        .get(hologramMarkerKey, PersistentDataType.STRING);
                if (!hologramMarkerValue.equals(marker)) continue;
                if (found == null) {
                    found = entity;
                } else {
                    entity.remove();
                }
            }
        } catch (Throwable ignored) {
        }
        return found;
    }

    private void configureTransparentTextDisplay(Entity display, List<String> lines)
            throws ReflectiveOperationException {
        Class<?> displayApi = Class.forName("org.bukkit.entity.Display");
        Class<?> textDisplayApi = Class.forName("org.bukkit.entity.TextDisplay");
        Class<?> billboardType = Class.forName("org.bukkit.entity.Display$Billboard");
        Class<?> alignmentType = Class.forName("org.bukkit.entity.TextDisplay$TextAlignment");

        invoke(displayApi, display, "setBillboard", new Class<?>[]{billboardType}, enumValue(billboardType, "CENTER"));
        invoke(textDisplayApi, display, "setAlignment", new Class<?>[]{alignmentType}, enumValue(alignmentType, "CENTER"));
        invoke(textDisplayApi, display, "setDefaultBackground", new Class<?>[]{boolean.class}, false);
        setTransparentBackground(display, textDisplayApi);
        invoke(textDisplayApi, display, "setShadowed", new Class<?>[]{boolean.class}, true);
        invoke(textDisplayApi, display, "setSeeThrough", new Class<?>[]{boolean.class}, false);
        invoke(textDisplayApi, display, "setLineWidth", new Class<?>[]{int.class}, 300);
        float viewRange = Math.max(1.0F, Math.min(3.0F, plugin.config().viewDistance() / 4.0F));
        invoke(displayApi, display, "setViewRange", new Class<?>[]{float.class}, viewRange);
        setTransparentText(display, textDisplayApi, lines);
    }

    private void setTransparentBackground(Entity display, Class<?> textDisplayApi)
            throws ReflectiveOperationException {
        try {
            Class<?> colorApi = Class.forName("org.bukkit.Color");
            Object transparent = colorApi.getMethod("fromARGB", int.class, int.class, int.class, int.class)
                    .invoke(null, 0, 0, 0, 0);
            invoke(textDisplayApi, display, "setBackgroundColor", new Class<?>[]{colorApi}, transparent);
        } catch (NoSuchMethodException ignored) {
            // setDefaultBackground(false) already disables the opaque default background.
        }
    }

    private void setTransparentText(Entity display, Class<?> textDisplayApi, List<String> lines)
            throws ReflectiveOperationException {
        Component text = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) text = text.append(Component.text("\n"));
            Component line = Components.colored(lines.get(index));
            if (line != null) text = text.append(line);
        }

        try {
            textDisplayApi.getMethod("text", Component.class).invoke(display, text);
        } catch (NoSuchMethodException ignored) {
            textDisplayApi.getMethod("setText", String.class)
                    .invoke(display, ColorUtil.translate(String.join("\n", lines)));
        }
    }

    private void removeTransparentHologram() {
        Entity display = transparentHologram;
        transparentHologram = null;
        if (display != null) {
            try { display.remove(); } catch (Throwable ignored) {}
        }

        World world = location.getWorld();
        if (world == null) return;
        Location at = location.clone().add(0.0D, config.hologramHeight(), 0.0D);
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        try {
            for (Entity entity : world.getNearbyEntities(at, 1.5D, 2.5D, 1.5D)) {
                String marker = entity.getPersistentDataContainer()
                        .get(hologramMarkerKey, PersistentDataType.STRING);
                if (hologramMarkerValue.equals(marker)) entity.remove();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void invoke(Class<?> api, Entity target, String method,
                               Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        api.getMethod(method, parameterTypes).invoke(target, args);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    /**
     * Обновляет текст голограммы. Строки уже собраны владельцем (плейсхолдеры
     * подставлены); здесь только диф — если текст не менялся, пакетов нет.
     */
    public void updateHologramText(List<String> lines) {
        if (hologramHidden || lines == null) return;
        String state = hologramState(lines);

        if (transparentHologramMode) {
            if (transparentHologram == null || !transparentHologram.isValid()) {
                transparentHologram = null;
                if (!spawnTransparentHologram()) return;
            }
            if (state.equals(lastHologramState)) return;
            try {
                setTransparentText(transparentHologram,
                        Class.forName("org.bukkit.entity.TextDisplay"), lines);
                lastHologramState = state;
            } catch (Throwable ignored) {
                // Retry on the next update instead of leaving the hologram with stale text.
            }
            return;
        }

        if (holograms.isEmpty()) return;
        if (state.equals(lastHologramState)) return;
        lastHologramState = state;
        for (int i = 0; i < holograms.size() && i < lines.size(); i++) {
            try {
                Component comp = Components.colored(lines.get(i));
                if (comp != null) holograms.get(i).setCustomName(comp);
            } catch (Throwable ignored) {
            }
        }
    }

    private String hologramState(List<String> lines) {
        StringBuilder state = new StringBuilder();
        for (String line : lines) state.append(line == null ? "" : line).append((char) 0);
        return state.toString();
    }

    public void hideHologramNames() {
        hologramHidden = true;
        lastHologramState = null;
        if (transparentHologram != null) {
            try {
                setTransparentText(transparentHologram,
                        Class.forName("org.bukkit.entity.TextDisplay"), new ArrayList<>());
            } catch (Throwable ignored) {
            }
        }
        for (VirtualArmorStand stand : holograms) {
            try { stand.setCustomNameVisible(false); } catch (Throwable ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Таб-лист
    // ─────────────────────────────────────────────────────────────────

    public void hideFromTabForAll() {
        if (player == null) return;
        removeFromTabForAll();
        try { player.setListed(false); } catch (Throwable ignored) {}
    }

    public void hideFromTabFor(Player target) {
        if (player == null || target == null) return;
        try { player.sendRemovePlayerPacket(target); } catch (Throwable ignored) {}
    }

    private void removeFromTabForAll() {
        if (player == null) return;
        try {
            for (Player online : Bukkit.getOnlinePlayers()) {
                try { player.sendRemovePlayerPacket(online); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // ─────────────────────────────────────────────────────────────────
    // Прочее
    // ─────────────────────────────────────────────────────────────────

    /** Радиус видимости виртуальных сущностей в блоках. */
    private int trackerRadius() {
        return Math.max(32, plugin.config().viewDistance() * 16);
    }

    public boolean spawned() { return spawned; }
    public int entityId() { return player == null ? -1 : player.getId(); }
    public VirtualPlayer player() { return player; }
    public Location location() { return location; }
}
