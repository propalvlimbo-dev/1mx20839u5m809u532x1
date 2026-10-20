package ru.rooyzee.elytrixaddonsphere;

import dev.by1337.bc.CaseBlock;
import dev.by1337.bc.animation.AbstractAnimation;
import dev.by1337.bc.animation.AnimationContext;
import dev.by1337.bc.prize.Prize;
import dev.by1337.bc.prize.PrizeSelector;
import dev.by1337.bc.task.AsyncTask;
import dev.by1337.bc.yaml.CashedYamlContext;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.item.VirtualItem;
import dev.by1337.virtualentity.api.virtual.monster.breeze.VirtualBreeze;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.by1337.blib.geom.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Анимация кейса «Вихрь».
 *
 * <p>В центр кейса ставится пакетный Вихрь (Breeze), вокруг него крутится кольцо
 * призов. Вихрь взлетает, резко падает, в момент удара выпадает приз и дальше
 * плавно летает по орбите вокруг Вихря.</p>
 *
 * <p>Всё движение считается в одном тик-таске (ровно 20 раз в секунду, в такт
 * отправке пакетов), поэтому вращение плавное и без дрожания. Поток анимации
 * только переключает фазы.</p>
 */
public class SphereAnimator extends AbstractAnimation {

    private final Prize winner;
    private final Config config;
    private final Vec3d center;
    private final Sound spawnSound;
    private final Sound winSound;
    private final Particle trailParticle;
    private final Particle itemParticle;
    private final Particle breezeParticle;
    private final Particle.DustOptions dustOptions;

    private final List<VirtualEntity> activeItems = new ArrayList<>();
    private final Map<VirtualEntity, Vec3d> lastPos = new IdentityHashMap<>();
    private final Object posLock = new Object();

    /** Кольцо призов (крутится вокруг Вихря). */
    private final List<VirtualEntity> ringItems = new ArrayList<>();
    private final List<VirtualEntity> ringNames = new ArrayList<>();
    /** Откуда предмет падает на орбиту и прогресс этого падения (0..1). */
    private final List<Vec3d> entryFrom = new ArrayList<>();
    private final List<Double> entryT = new ArrayList<>();
    private double[] slots = new double[0];

    private VirtualEntity breezeEntity;
    private VirtualEntity winnerItem;
    private VirtualEntity winnerName;

    // ── состояние, которое читает/пишет тик-таск ────────────────────────────
    private volatile boolean ringActive;
    private volatile double targetRadius;
    private volatile double targetElevation;
    private volatile double targetSpeed;
    private volatile double curRadius;
    private volatile double curElevation;
    private volatile double curSpeed;
    private volatile double ringAngle;

    private volatile boolean breezeSpin;
    private volatile double breezeSpinSpeed;
    private volatile float breezeYaw;

    private volatile boolean winnerOrbit;
    private volatile double winnerAngle;
    private volatile double winnerRadius;
    private volatile double winnerRadiusTarget;
    private volatile double winnerSpeed;
    private volatile double winnerPhase;
    private volatile double winnerY;
    private volatile VirtualEntity winnerEntityRef;
    private volatile VirtualEntity winnerNameRef;

    private volatile int puffTicks;

    public SphereAnimator(
            CaseBlock caseBlock,
            AnimationContext context,
            Runnable onEndCallback,
            PrizeSelector prizeSelector,
            CashedYamlContext config,
            Player player
    ) {
        super(caseBlock, context, onEndCallback, prizeSelector, config, player);

        Prize selected = prizeSelector.getRandomPrize();
        if (selected == null) {
            throw new IllegalStateException("No prize available");
        }
        this.winner = selected;
        this.center = new Vec3d(this.blockPos).add(0.5D, 0.5D, 0.5D);
        this.config = new Config(config);

        this.curRadius = this.config.radius;
        this.targetRadius = this.config.radius;
        this.curSpeed = this.config.rotationSpeed;
        this.targetSpeed = this.config.rotationSpeed;
        this.winnerSpeed = this.config.winnerOrbitSpeed;

        this.spawnSound = parseSound(this.config.timings.spawnSound, Sound.BLOCK_NOTE_BLOCK_PLING);
        this.winSound = parseSound(this.config.timings.winSound, Sound.ENTITY_PLAYER_LEVELUP);
        this.trailParticle = parseParticle(this.config.particleVector, Particle.REDSTONE);
        this.itemParticle = parseParticle(this.config.particleItems, Particle.REDSTONE);
        this.breezeParticle = parseParticle(this.config.breezeParticle, Particle.CLOUD);

        this.dustOptions = new Particle.DustOptions(
                Color.fromRGB(
                        Math.max(0, Math.min(255, this.config.red)),
                        Math.max(0, Math.min(255, this.config.green)),
                        Math.max(0, Math.min(255, this.config.blue))
                ),
                0.6F
        );
    }

    // ── фазы ────────────────────────────────────────────────────────────────

    @Override
    protected void onStart() {
        caseBlock.hideHologram();
        caseBlock.hideBlock();
    }

    @Override
    protected void animate() throws InterruptedException {
        int count = Math.max(4, Math.min(24, config.itemCount));
        int winnerIdx = RANDOM.nextInt(count);

        slots = new double[count];
        for (int i = 0; i < count; i++) {
            slots[i] = (2.0D * Math.PI * i) / count;
        }

        try {
            startDriver();
            spawnBreeze();
            ascent(count, winnerIdx);
            orbitPhase();

            // Вихрь резко вверх, затем резкий удар вниз
            breezeUp();
            breezeSlam();
            onImpact(winnerIdx);

            // приз плавно летает вокруг Вихря
            winnerOrbitPhase();

            breezeOutro();
        } finally {
            cleanup();
        }
    }

    @Override
    protected void onEnd() {
        cleanup();
        caseBlock.showHologram();
        caseBlock.showBlock();
        caseBlock.givePrize(winner, player);
    }

    @Override
    protected void onClick(VirtualEntity entity, Player player) {
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
    }

    // ── Вихрь ───────────────────────────────────────────────────────────────

    private void spawnBreeze() {
        if (!config.breeze) return;
        try {
            VirtualBreeze breeze = VirtualBreeze.create();
            breeze.setNoAi(true);
            breeze.setSilent(true);
            breeze.setNoGravity(true);
            breeze.setNoMotion();
            breeze.setPos(breezePos(0.0D));
            breeze.setYaw(0.0F);
            breeze.setPitch(0.0F);
            trackEntity(breeze);
            breezeEntity = breeze;
            breezeYaw = 0.0F;
            breezeSpinSpeed = config.breezeSpin;
            breezeSpin = true;
            puff(8);
        } catch (Throwable t) {
            breezeEntity = null;
        }
    }

    private Vec3d breezePos(double rise) {
        return new Vec3d(center.x, center.y + config.breezeHeight + rise, center.z);
    }

    private void puff(int count) {
        if (breezeEntity == null) return;
        Vec3d pos = breezeEntity.getPos();
        if (pos == null) pos = breezePos(curElevation);
        spawnParticle(breezeParticle, pos.add(0.0D, 0.9D, 0.0D), count, 0.45D, 0.7D, 0.45D, 0.02D);
    }

    // ── единый тик-драйвер ─────────────────────────────────────────────────

    /**
     * Один таск на 20 тиков в секунду: двигает кольцо, Вихрь и приз.
     * Именно поэтому всё крутится ровно и плавно — без дрожания анимационного потока.
     */
    private void startDriver() {
        addTask(AsyncTask.create(this::drive).delay(1).timer());
    }

    private void drive() {
        try {
            // плавно подтягиваем текущие значения к целевым
            curRadius += (targetRadius - curRadius) * 0.30D;
            curElevation += (targetElevation - curElevation) * 0.35D;
            curSpeed += (targetSpeed - curSpeed) * 0.25D;

            if (ringActive) {
                ringAngle = (ringAngle + curSpeed) % 360.0D;
            }

            // предметы падают на орбиту каждый со своей точки (плавно, ease-out)
            double entryStep = 1.0D / Math.max(1, config.timings.ascentSteps);
            for (int i = 0; i < ringItems.size(); i++) {
                VirtualEntity item = ringItems.get(i);
                if (item == null) continue;

                Vec3d target = ringPos(slots[i]);
                Vec3d pos = target;

                double t = i < entryT.size() ? entryT.get(i) : 1.0D;
                if (t < 1.0D) {
                    t = Math.min(1.0D, t + entryStep);
                    entryT.set(i, t);
                    double e = 1.0D - Math.pow(1.0D - t, 3.0D);
                    Vec3d from = (i < entryFrom.size() && entryFrom.get(i) != null)
                            ? entryFrom.get(i) : target;
                    pos = new Vec3d(
                            from.x + (target.x - from.x) * e,
                            from.y + (target.y - from.y) * e,
                            from.z + (target.z - from.z) * e
                    );
                }

                moveTo(item, pos);
                if (i < ringNames.size() && ringNames.get(i) != null) {
                    moveTo(ringNames.get(i), pos.add(0.0D, config.nameOffset, 0.0D));
                }
            }

            if (ringActive && curSpeed > 1.0D) {
                drawRingTrail();
            }

            if (breezeSpin) {
                breezeYaw = (float) ((breezeYaw + breezeSpinSpeed) % 360.0D);
                if (breezeEntity != null) {
                    breezeEntity.setPos(breezePos(curElevation));
                    breezeEntity.setYaw(breezeYaw);
                }
                if (++puffTicks % 3 == 0) {
                    puff(1);
                }
            }

            VirtualEntity we = winnerEntityRef;
            if (winnerOrbit && we != null) {
                winnerAngle = (winnerAngle + winnerSpeed) % 360.0D;
                winnerRadius += (winnerRadiusTarget - winnerRadius) * 0.22D;
                winnerY += ((center.y + config.breezeHeight + 1.15D) - winnerY) * 0.22D;
                double rad = Math.toRadians(winnerAngle);
                double bob = 0.07D * Math.sin(Math.toRadians(winnerPhase));
                winnerPhase += 6.0D;
                Vec3d pos = new Vec3d(
                        center.x + Math.cos(rad) * winnerRadius,
                        winnerY + bob,
                        center.z + Math.sin(rad) * winnerRadius
                );
                moveTo(we, pos);
                if (winnerNameRef != null) {
                    moveTo(winnerNameRef, pos.add(0.0D, config.nameOffset, 0.0D));
                }
            }
        } catch (Throwable ignored) {
            // анимация не должна падать из-за одной ошибки в тике
        }
    }

    private Vec3d ringPos(double slot) {
        double ang = Math.toRadians(slot) + Math.toRadians(ringAngle);
        return new Vec3d(
                center.x + Math.cos(ang) * curRadius,
                center.y + config.height + curElevation,
                center.z + Math.sin(ang) * curRadius
        );
    }

    /** Двигает сущность только если позиция реально изменилась. */
    private void moveTo(VirtualEntity entity, Vec3d pos) {
        if (entity == null || pos == null) return;
        synchronized (posLock) {
            Vec3d prev = lastPos.get(entity);
            if (prev != null) {
                double dx = prev.x - pos.x;
                double dy = prev.y - pos.y;
                double dz = prev.z - pos.z;
                if (dx * dx + dy * dy + dz * dz < 1.0e-5D) return;
            }
            lastPos.put(entity, pos);
        }
        entity.setPos(pos);
    }

    // ── сбор и круг ─────────────────────────────────────────────────────────

    private void ascent(int count, int winnerIdx) throws InterruptedException {
        int steps = config.timings.ascentSteps;
        int gap = config.timings.ascentGap;

        Vec3d hidden = center.add(0.0D, 6.0D, 0.0D);

        for (int i = 0; i < count; i++) {
            Prize prize = (i == winnerIdx) ? winner : safePrize();
            VirtualEntity item = spawnDisplay(prize, hidden, (float) Math.toDegrees(slots[i]) - 90.0F);
            ringItems.add(item);
            entryFrom.add(hidden);
            entryT.add(0.0D);
            if (i == winnerIdx) {
                winnerItem = item;
            }
            ringNames.add(config.standMode ? spawnNameStand(prize, hidden) : null);

            playSound(center, spawnSound, 0.25F, 0.9F + 0.4F * ((float) i / count));
            sleepTicks(gap);
        }

        // кольцо включается, предметы плавно съезжают на орбиту
        ringActive = true;
        targetSpeed = config.rotationSpeed;
        sleepTicks(steps);
    }

    private void orbitPhase() throws InterruptedException {
        int ticks = config.timings.orbitTicks;
        targetSpeed = config.rotationSpeed * 1.35D;
        sleepTicks(ticks);
    }

    // ── взлёт и удар ────────────────────────────────────────────────────────

    private void breezeUp() throws InterruptedException {
        if (breezeEntity == null || config.breezeRise <= 0.0D) return;

        breezeSpinSpeed = config.breezeSpin * 1.2D;
        targetSpeed = config.rotationSpeed * 1.6D;

        // быстрый, но плавный взлёт: ease-out по кубу — рывок вверх без телепорта
        int ticks = Math.max(2, config.timings.breezeUpTicks);
        for (int t = 1; t <= ticks; t++) {
            double p = (double) t / ticks;
            double e = 1.0D - Math.pow(1.0D - p, 3.0D);
            curElevation = config.breezeRise * e;
            targetElevation = curElevation;
            sleepTicks(1L);
        }
        curElevation = config.breezeRise;
        targetElevation = config.breezeRise;
    }

    private void breezeSlam() throws InterruptedException {
        if (breezeEntity == null) return;

        // зависание наверху — чтобы удар читался как удар
        sleepTicks(3);

        // резкий удар: цель — пол, но с ускорением (ease-in)
        int ticks = Math.max(2, config.timings.breezeDownTicks);
        double from = curElevation;
        for (int t = 1; t <= ticks; t++) {
            double p = (double) t / ticks;
            curElevation = from * (1.0D - p * p);
            targetElevation = curElevation;
            breezeSpinSpeed = config.breezeSpin * (1.2D + 1.6D * p);
            sleepTicks(1L);
        }
        curElevation = 0.0D;
        targetElevation = 0.0D;
    }

    /** Момент удара: вспышка, звук, вылет остальных призов, старт орбиты приза. */
    private void onImpact(int winnerIdx) throws InterruptedException {
        Vec3d impact = breezePos(0.0D);
        playSound(impact.add(0.0D, 0.6D, 0.0D), parseSound(config.timings.impactSound, Sound.ENTITY_GENERIC_EXPLODE), 1.0F, 0.75F);
        burst(impact.add(0.0D, 0.8D, 0.0D), 60);
        puff(10);
        sleepTicks(2);

        // остальные призы разлетаются в стороны и исчезают
        for (int i = 0; i < ringItems.size(); i++) {
            if (i == winnerIdx) continue;
            VirtualEntity item = ringItems.get(i);
            if (item == null) continue;
            Vec3d from = item.getPos();
            if (from != null) poof(from);
            removeAndForget(item);
            ringItems.set(i, null);
            if (i < ringNames.size() && ringNames.get(i) != null) {
                removeAndForget(ringNames.get(i));
                ringNames.set(i, null);
            }
        }

        ringActive = false;

        // приз вылетает из Вихря и садится на орбиту
        if (winnerItem != null) {
            Vec3d start = winnerItem.getPos();
            double ang = Math.atan2(
                    (start == null ? center.z : start.z) - center.z,
                    (start == null ? center.x : start.x) - center.x);
            winnerAngle = Math.toDegrees(ang);
            winnerRadius = Math.max(0.35D, Math.hypot(
                    (start == null ? center.x : start.x) - center.x,
                    (start == null ? center.z : start.z) - center.z));
            winnerRadiusTarget = config.winnerOrbitRadius;
            winnerY = start == null ? center.y + config.breezeHeight + 1.15D : start.y;
            winnerSpeed = 0.0D;
            winnerPhase = 0.0D;
            winnerEntityRef = winnerItem;
            winnerNameRef = winnerNames() > winnerIdx ? ringNames.get(winnerIdx) : null;
            // дальше призом управляет только орбита — из кольца его убираем
            if (winnerIdx < ringItems.size()) ringItems.set(winnerIdx, null);
            if (winnerIdx < ringNames.size()) ringNames.set(winnerIdx, null);
            winnerOrbit = true;
        }
    }

    private int winnerNames() {
        return ringNames.size();
    }

    private void winnerOrbitPhase() throws InterruptedException {
        int ticks = config.timings.winnerOrbitTicks;
        if (winnerItem == null) return;

        // плавный разгон по орбите
        for (int t = 0; t < 10; t++) {
            winnerSpeed = config.winnerOrbitSpeed * ((t + 1) / 10.0D);
            sleepTicks(1L);
        }
        winnerSpeed = config.winnerOrbitSpeed;
        playSound(center, winSound, 0.8F, 1.0F);
        burst(winnerItem.getPos() == null ? center : winnerItem.getPos(), 35);
        sleepTicks(Math.max(10, ticks - 10));
    }

    private void breezeOutro() throws InterruptedException {
        if (breezeEntity == null || config.timings.breezeStayTicks <= 0) return;
        breezeSpinSpeed = config.breezeSpin * 0.5D;
        sleepTicks(config.timings.breezeStayTicks);
    }

    // ── сущности ────────────────────────────────────────────────────────────

    private VirtualEntity spawnDisplay(Prize prize, Vec3d pos, float yaw) {
        if (config.standMode) {
            VirtualArmorStand stand = VirtualArmorStand.create();
            stand.setSmall(true);
            stand.setNoBasePlate(true);
            stand.setNoGravity(true);
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setNoMotion();
            stand.setEquipment(EquipmentSlot.HEAD, prize.itemStack());
            stand.setPos(pos);
            stand.setYaw(yaw);
            trackEntity(stand);
            activeItems.add(stand);
            return stand;
        }

        VirtualItem item = VirtualItem.create();
        item.setItem(prize.itemStack());
        item.setPos(pos);
        item.setNoGravity(true);
        item.setNoMotion();
        item.setCustomName(prize.displayNameComponent());
        item.setCustomNameVisible(true);
        trackEntity(item);
        activeItems.add(item);
        return item;
    }

    private VirtualEntity spawnNameStand(Prize prize, Vec3d pos) {
        Component name = prize.displayNameComponent();
        if (name == null) return null;

        VirtualArmorStand stand = VirtualArmorStand.create();
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setNoBasePlate(true);
        stand.setNoGravity(true);
        stand.setInvisible(true);
        stand.setNoMotion();
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setPos(pos);
        trackEntity(stand);
        activeItems.add(stand);
        return stand;
    }

    private void removeAndForget(VirtualEntity entity) {
        if (entity == null) return;
        activeItems.remove(entity);
        synchronized (posLock) {
            lastPos.remove(entity);
        }
        try {
            removeEntity(entity);
        } catch (Throwable ignored) {
        }
    }

    private void cleanup() {
        ringActive = false;
        winnerOrbit = false;
        breezeSpin = false;
        winnerEntityRef = null;
        winnerNameRef = null;
        winnerItem = null;
        winnerName = null;

        if (breezeEntity != null) {
            removeAndForget(breezeEntity);
            breezeEntity = null;
        }
        for (VirtualEntity entity : new ArrayList<>(activeItems)) {
            removeAndForget(entity);
        }
        activeItems.clear();
        ringItems.clear();
        ringNames.clear();
        entryFrom.clear();
        entryT.clear();
        synchronized (posLock) {
            lastPos.clear();
        }
    }

    // ── частицы ─────────────────────────────────────────────────────────────

    private void drawRingTrail() {
        int step = 3;
        for (int i = 0; i < ringItems.size(); i++) {
            if (i % step != 0) continue;
            VirtualEntity item = ringItems.get(i);
            if (item == null) continue;
            Vec3d pos = item.getPos();
            if (pos == null) continue;
            Vec3d point = pos.add(0.0D, config.standMode ? 0.95D : 0.1D, 0.0D);
            if (isRedstone(trailParticle)) {
                spawnParticle(Particle.REDSTONE, point, 0, 0.0D, 0.0D, 0.0D, 0.0D, dustOptions);
            } else {
                spawnParticle(trailParticle, point, 0, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void poof(Vec3d pos) {
        if (pos == null) return;
        if (isRedstone(trailParticle)) {
            spawnParticle(Particle.REDSTONE, pos, 8, 0.25D, 0.25D, 0.25D, 0.0D, dustOptions);
        } else {
            spawnParticle(trailParticle, pos, 8, 0.25D, 0.25D, 0.25D, 0.0D);
        }
    }

    private void burst(Vec3d pos, int count) {
        if (isRedstone(itemParticle)) {
            spawnParticle(Particle.REDSTONE, pos, count, 0.6D, 0.6D, 0.6D, 0.0D, dustOptions);
        } else {
            spawnParticle(itemParticle, pos, count, 0.6D, 0.6D, 0.6D, 0.0D);
        }
        spawnParticle(breezeParticle, pos, count / 2, 0.5D, 0.4D, 0.5D, 0.03D);
    }

    // ── утилиты ─────────────────────────────────────────────────────────────

    private Prize safePrize() {
        Prize p = prizeSelector.getRandomPrize();
        return p != null ? p : winner;
    }

    private Particle parseParticle(String name, Particle fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private boolean isRedstone(Particle p) {
        return p == Particle.REDSTONE;
    }

    // ── конфиг ──────────────────────────────────────────────────────────────

    private static class Config {
        final double radius;
        final int itemCount;
        final double rotationSpeed;
        final double height;
        final boolean standMode;
        final double nameOffset;
        final String particleVector;
        final String particleItems;
        final int red;
        final int green;
        final int blue;
        final boolean breeze;
        final double breezeSpin;
        final double breezeRise;
        final double breezeHeight;
        final String breezeParticle;
        final double winnerOrbitRadius;
        final double winnerOrbitSpeed;
        final Timings timings;

        private Config(CashedYamlContext c) {
            this.radius = clamp(get(c, "radius", 2.5D), 0.8D, 6.0D);
            this.itemCount = (int) clamp(get(c, "itemCount", 10), 4, 24);
            this.rotationSpeed = clamp(get(c, "rotationSpeed", 7.0D), 1.0D, 20.0D);
            this.height = clamp(get(c, "height", 0.35D), 0.0D, 4.0D);
            this.standMode = !getStr(c, "displayMode", "STAND").trim().equalsIgnoreCase("ITEM");
            this.nameOffset = clamp(get(c, "nameOffset", 1.15D), 0.0D, 3.0D);
            this.particleVector = getStr(c, "particleVector", "REDSTONE");
            this.particleItems = getStr(c, "particleItems", "REDSTONE");
            this.red = (int) clamp(get(c, "red", 255), 0, 255);
            this.green = (int) clamp(get(c, "green", 105), 0, 255);
            this.blue = (int) clamp(get(c, "blue", 180), 0, 255);
            this.breeze = getBool(c, "breeze", true);
            this.breezeSpin = clamp(get(c, "breezeSpin", 14.0D), 0.0D, 30.0D);
            this.breezeRise = clamp(get(c, "breezeRise", 2.6D), 0.0D, 6.0D);
            this.breezeHeight = clamp(get(c, "breezeHeight", -0.45D), -2.0D, 2.0D);
            this.breezeParticle = getStr(c, "breezeParticle", "CLOUD");
            this.winnerOrbitRadius = clamp(get(c, "winnerOrbitRadius", 1.05D), 0.0D, 4.0D);
            this.winnerOrbitSpeed = clamp(get(c, "winnerOrbitSpeed", 8.0D), 0.0D, 20.0D);
            this.timings = new Timings(c);
        }

        private static double get(CashedYamlContext c, String key, double def) {
            Double v = c.get("settings." + key, val -> val.getAsDouble(def), def);
            return v == null ? def : v;
        }

        private static String getStr(CashedYamlContext c, String key, String def) {
            String v = c.get("settings." + key, val -> val.getAsString(def), def);
            return v == null ? def : v;
        }

        private static boolean getBool(CashedYamlContext c, String key, boolean def) {
            Boolean v = c.get("settings." + key, val -> val.getAsBoolean(def), def);
            return v == null ? def : v;
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    private static class Timings {
        final int ascentSteps;
        final int ascentGap;
        final int orbitTicks;
        final int breezeUpTicks;
        final int breezeDownTicks;
        final int winnerOrbitTicks;
        final int breezeStayTicks;
        final String spawnSound;
        final String winSound;
        final String impactSound;

        Timings(CashedYamlContext c) {
            this.ascentSteps = (int) clamp(get(c, "ascentSteps", 8), 2, 15);
            this.ascentGap = (int) clamp(get(c, "ascentGap", 2), 1, 5);
            this.orbitTicks = (int) clamp(get(c, "orbitTicks", 75), 20, 1200);
            this.breezeUpTicks = (int) clamp(get(c, "breezeUpTicks", 8), 2, 200);
            this.breezeDownTicks = (int) clamp(get(c, "breezeDownTicks", 4), 2, 200);
            this.winnerOrbitTicks = (int) clamp(get(c, "winnerOrbitTicks", 80), 20, 400);
            this.breezeStayTicks = (int) clamp(get(c, "breezeStayTicks", 12), 0, 200);
            this.spawnSound = str(c, "spawnSound", "BLOCK_NOTE_BLOCK_PLING");
            this.winSound = str(c, "winSound", "ENTITY_PLAYER_LEVELUP");
            this.impactSound = str(c, "impactSound", "ENTITY_GENERIC_EXPLODE");
        }

        private static double get(CashedYamlContext c, String key, double def) {
            Double v = c.get("settings.timings." + key, val -> val.getAsDouble(def), def);
            return v == null ? def : v;
        }

        private static String str(CashedYamlContext c, String key, String def) {
            String v = c.get("settings.timings." + key, val -> val.getAsString(def), def);
            return v == null ? def : v;
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }
}
