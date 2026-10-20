package ru.rooyzee.elytrixaddonsphere;

import dev.by1337.bc.CaseBlock;
import dev.by1337.bc.animation.AbstractAnimation;
import dev.by1337.bc.animation.AnimationContext;
import dev.by1337.bc.prize.Prize;
import dev.by1337.bc.prize.PrizeSelector;
import dev.by1337.bc.task.AsyncTask;
import dev.by1337.bc.yaml.CashedYamlContext;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.task.TickTask;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.item.VirtualItem;
import dev.by1337.virtualentity.api.virtual.monster.breeze.VirtualBreeze;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.by1337.blib.geom.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Анимация кейса «Вихрь».
 *
 * <p>Сценарий: вокруг Вихря (Breeze) крутится кольцо призов, Вихрь подлетает,
 * сверху падает булава и бьёт его, от удара Вихрь падает — в этот момент
 * выпадает приз и 4 секунды летает по орбите вокруг Вихря.</p>
 *
 * <p>Все визуальные обновления (кольцо, Вихрь, булава, искры, победитель)
 * выполняются в тик-таске самой сущности — то есть ровно в тот момент, когда
 * VirtualEntityApi отправляет пакеты. Поэтому вращение не дёргается: нет
 * рассинхрона между потоком анимации и тикером сервера.</p>
 *
 * <p>Искры — это пакетные предметы (glowstone dust), а не частицы: они видны
 * даже если в настройках Minecraft частицы выключены.</p>
 */
public class SphereAnimator extends AbstractAnimation {

    private final Prize winner;
    private final Config config;
    private final Vec3d center;
    private final Sound spawnSound;
    private final Sound winSound;
    private final Sound impactSound;
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
    /** Откуда приз опускается на орбиту и прогресс этого опускания (0..1). */
    private final List<Vec3d> entryFrom = new ArrayList<>();
    private final List<Double> entryT = new ArrayList<>();
    private double[] slots = new double[0];

    private VirtualEntity breezeEntity;
    private VirtualEntity clockEntity;
    private VirtualEntity winnerItem;
    private VirtualEntity maceItem;

    /** Искры вокруг Вихря — предметы, видны при любых настройках частиц. */
    private final List<VirtualEntity> sparks = new ArrayList<>();
    private int sparkPerRing = 1;

    /** Разлетающиеся искры в момент удара. */
    private final List<VirtualEntity> shockItems = new ArrayList<>();
    private double[] shockAngles = new double[0];

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
    private volatile double breezeSpinTarget;
    private volatile float breezeYaw;

    private volatile boolean sparkActive;
    private volatile double sparkAngle;
    private volatile double sparkSpeedScale = 1.0D;
    private volatile double sparkSpeedTarget = 1.0D;
    private volatile double sparkRadiusScale = 1.0D;
    private volatile double sparkRadiusTarget = 1.0D;

    private volatile boolean maceActive;
    private volatile double maceT;
    private volatile double maceFromY;
    private volatile double maceToY;
    private volatile int maceTicks = 8;

    private volatile boolean shockActive;
    private volatile double shockT;

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
    private volatile int trailTick;
    private volatile double bobTick;

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
        this.impactSound = parseSound(this.config.timings.impactSound, Sound.ENTITY_GENERIC_EXPLODE);
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
            spawnBreeze();
            spawnClock();
            spawnSparks();
            ascent(count, winnerIdx);
            orbitPhase();

            // Вихрь подлетает, замирает — сверху падает булава и бьёт его
            breezeUp();
            sleepTicks(config.timings.breezeHoverTicks);
            maceStrike();
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
            // старт с нуля: голова не «телепортируется», вращение набирает темп
            breezeSpinSpeed = 0.0D;
            breezeSpinTarget = config.breezeSpin;
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
        fx(breezeParticle, pos.add(0.0D, 0.9D, 0.0D), count, 0.45D, 0.7D, 0.45D, 0.02D);
    }

    // ── тик-драйвер ─────────────────────────────────────────────────────────

    /**
     * Вешает драйвер на тик сущности: VirtualEntityApi сам вызывает его ровно
     * перед отправкой пакетов, поэтому движение и пакеты идут в такт.
     */
    private void spawnClock() {
        VirtualEntity clock = breezeEntity;
        if (clock == null) {
            try {
                VirtualArmorStand stand = VirtualArmorStand.create();
                stand.setSmall(true);
                stand.setMarker(true);
                stand.setInvisible(true);
                stand.setNoBasePlate(true);
                stand.setNoGravity(true);
                stand.setNoMotion();
                stand.setPos(center);
                trackEntity(stand);
                activeItems.add(stand);
                clock = stand;
            } catch (Throwable ignored) {
                clock = null;
            }
        }
        clockEntity = clock;

        if (clock != null) {
            try {
                clock.addTickTask((TickTask) this::drive);
                return;
            } catch (Throwable ignored) {
                // старая версия VirtualEntityApi — падаем на обычный таск
            }
        }
        try {
            addTask(AsyncTask.create(this::drive).delay(1).timer());
        } catch (Throwable ignored) {
        }
    }

    private void drive() {
        try {
            // плавно подтягиваем текущие значения к целевым
            curRadius += (targetRadius - curRadius) * 0.30D;
            curElevation += (targetElevation - curElevation) * 0.35D;
            curSpeed += (targetSpeed - curSpeed) * 0.25D;
            bobTick += config.bobSpeed;

            if (ringActive) {
                ringAngle = (ringAngle + curSpeed) % 360.0D;
            }

            driveRing();

            if (ringActive && curSpeed > 1.0D && (++trailTick % 2 == 0)) {
                drawRingTrail();
            }

            driveBreeze();
            driveSparks();
            driveMace();
            driveShock();
            driveWinner();
        } catch (Throwable ignored) {
            // анимация не должна падать из-за одной ошибки в тике
        }
    }

    /** Кольцо призов: опускание на орбиту, вращение и покачивание вверх-вниз. */
    private void driveRing() {
        double entryStep = 1.0D / Math.max(1, config.timings.ascentSteps);
        for (int i = 0; i < ringItems.size(); i++) {
            VirtualEntity item = ringItems.get(i);
            if (item == null || i >= slots.length) continue;

            Vec3d target = ringPos(slots[i]);
            Vec3d pos = target;

            double t = (i < entryT.size() && i < entryFrom.size()) ? entryT.get(i) : 1.0D;
            if (t < 1.0D) {
                t = Math.min(1.0D, t + entryStep);
                entryT.set(i, t);
                double e = 1.0D - Math.pow(1.0D - t, 3.0D);
                pos = lerp(entryFrom.get(i), target, e);
            }

            // сильное покачивание по высоте, у каждого приза своя фаза
            double bob = config.bobAmplitude * Math.sin(bobTick + i * 0.9D) * (t >= 1.0D ? 1.0D : t);
            if (bob != 0.0D) {
                pos = pos.add(0.0D, bob, 0.0D);
            }

            moveTo(item, pos);
            if (i < ringNames.size() && ringNames.get(i) != null) {
                moveTo(ringNames.get(i), pos.add(0.0D, config.nameOffset + bob * 0.4D, 0.0D));
            }
        }
    }

    private void driveBreeze() {
        if (!breezeSpin) return;

        breezeSpinSpeed += (breezeSpinTarget - breezeSpinSpeed) * 0.15D;
        breezeYaw = (float) ((breezeYaw + breezeSpinSpeed) % 360.0D);

        if (breezeEntity != null) {
            breezeEntity.setPos(breezePos(curElevation));
            breezeEntity.setYaw(breezeYaw);
        }
        if (++puffTicks % 3 == 0) {
            puff(1);
        }
    }

    /** Искры: два кольца пакетных предметов, крутятся в разные стороны. */
    private void driveSparks() {
        if (!sparkActive || sparks.isEmpty()) return;

        sparkSpeedScale += (sparkSpeedTarget - sparkSpeedScale) * 0.10D;
        sparkRadiusScale += (sparkRadiusTarget - sparkRadiusScale) * 0.10D;
        sparkAngle = (sparkAngle + config.sparkSpeed * sparkSpeedScale) % 360.0D;

        int n = Math.max(1, sparkPerRing);
        double baseY = center.y + config.breezeHeight + curElevation;

        for (int i = 0; i < sparks.size(); i++) {
            int ring = i / n;
            int idx = i % n;
            double dir = ring == 0 ? 1.0D : -1.0D;

            double ang = Math.toRadians(sparkAngle * dir + 360.0D * idx / (double) n);
            double radius = config.sparkRadius * sparkRadiusScale * (ring == 0 ? 1.0D : 0.62D);
            double y = baseY
                    + config.sparkHeight * (ring == 0 ? 1.0D : 0.55D)
                    + 0.16D * Math.sin(Math.toRadians(sparkAngle * 2.0D * dir + idx * 37.0D));

            moveTo(sparks.get(i), new Vec3d(
                    center.x + Math.cos(ang) * radius,
                    y,
                    center.z + Math.sin(ang) * radius
            ));
        }
    }

    private void driveMace() {
        if (!maceActive || maceItem == null) return;

        maceT = Math.min(1.0D, maceT + 1.0D / Math.max(1, maceTicks));
        double p = maceT * maceT; // ускорение к земле
        double y = maceFromY + (maceToY - maceFromY) * p;
        moveTo(maceItem, new Vec3d(center.x, y, center.z));
        fx(Particle.END_ROD, new Vec3d(center.x, y + 0.4D, center.z), 1, 0.04D, 0.04D, 0.04D, 0.0D);
    }

    private void driveShock() {
        if (!shockActive || shockItems.isEmpty()) return;

        shockT = Math.min(1.0D, shockT + 1.0D / 12.0D);
        double r = 0.4D + 3.2D * shockT;
        double y = center.y + config.height + 0.6D * (1.0D - shockT);

        for (int i = 0; i < shockItems.size() && i < shockAngles.length; i++) {
            double a = shockAngles[i];
            moveTo(shockItems.get(i), new Vec3d(
                    center.x + Math.cos(a) * r,
                    y,
                    center.z + Math.sin(a) * r
            ));
        }

        if (shockT >= 1.0D) {
            for (VirtualEntity e : new ArrayList<>(shockItems)) {
                removeAndForget(e);
            }
            shockItems.clear();
            shockActive = false;
        }
    }

    private void driveWinner() {
        VirtualEntity we = winnerEntityRef;
        if (!winnerOrbit || we == null) return;

        winnerAngle = (winnerAngle + winnerSpeed) % 360.0D;
        winnerRadius += (winnerRadiusTarget - winnerRadius) * 0.22D;
        winnerY += ((center.y + config.breezeHeight + 1.15D) - winnerY) * 0.22D;

        double rad = Math.toRadians(winnerAngle);
        double bob = 0.09D * Math.sin(Math.toRadians(winnerPhase));
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

    private Vec3d ringPos(double slot) {
        // slot уже в радианах (2π·i/count), ringAngle — в градусах
        double ang = slot + Math.toRadians(ringAngle);
        return new Vec3d(
                center.x + Math.cos(ang) * curRadius,
                center.y + config.height + curElevation,
                center.z + Math.sin(ang) * curRadius
        );
    }

    private Vec3d lerp(Vec3d from, Vec3d to, double t) {
        return new Vec3d(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t
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

        for (int i = 0; i < count; i++) {
            Prize prize = (i == winnerIdx) ? winner : safePrize();

            // своя точка над своим местом в кольце — призы не сбиваются в кучу
            Vec3d from = ringPos(slots[i]).add(0.0D, config.dropHeight, 0.0D);
            VirtualEntity item = spawnDisplay(prize, from, (float) Math.toDegrees(slots[i]) - 90.0F);
            ringItems.add(item);
            entryFrom.add(from);
            entryT.add(0.0D);
            if (i == winnerIdx) {
                winnerItem = item;
            }
            ringNames.add(config.standMode ? spawnNameStand(prize, from) : null);

            playSound(center, spawnSound, 0.25F, 0.9F + 0.4F * ((float) i / count));
            sleepTicks(gap);
        }

        // кольцо включается, призы плавно съезжают на орбиту
        ringActive = true;
        targetSpeed = config.rotationSpeed;
        sleepTicks(steps);
    }

    private void orbitPhase() throws InterruptedException {
        int ticks = config.timings.orbitTicks;
        targetSpeed = config.rotationSpeed * 1.35D;
        sleepTicks(ticks);
    }

    // ── взлёт, булава и удар ────────────────────────────────────────────────

    /** Эпичный подлёт: искры сжимаются и раскручиваются, столб частиц, звук. */
    private void breezeUp() throws InterruptedException {
        if (breezeEntity == null || config.breezeRise <= 0.0D) return;

        sparkSpeedTarget = 2.2D;
        sparkRadiusTarget = 0.58D;
        breezeSpinTarget = Math.min(9.0D, config.breezeSpin * 1.35D);
        targetSpeed = config.rotationSpeed * 1.45D;

        sound(breezePos(curElevation), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9F, 0.75F);

        int ticks = Math.max(2, config.timings.breezeUpTicks);
        for (int t = 1; t <= ticks; t++) {
            double p = (double) t / ticks;
            double e = 1.0D - Math.pow(1.0D - p, 3.0D);
            curElevation = config.breezeRise * e;
            targetElevation = curElevation;
            swirlColumn(4);
            sleepTicks(1L);
        }
        curElevation = config.breezeRise;
        targetElevation = config.breezeRise;

        sound(breezePos(curElevation), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
        burst(breezePos(curElevation).add(0.0D, 1.0D, 0.0D), 30);
    }

    /** Сверху падает булава и бьёт Вихря. */
    private void maceStrike() throws InterruptedException {
        if (!config.mace) return;

        Material mat = material(config.maceMaterial, Material.IRON_AXE);
        VirtualItem mace = null;
        try {
            mace = VirtualItem.create();
            mace.setItem(new ItemStack(mat));
            mace.setNoGravity(true);
            mace.setNoMotion();
            mace.setPos(new Vec3d(center.x, center.y + config.breezeHeight + curElevation + config.maceHeight, center.z));
            trackEntity(mace);
            activeItems.add(mace);
        } catch (Throwable t) {
            mace = null;
        }

        if (mace == null) {
            sleepTicks(4);
            return;
        }

        maceItem = mace;
        maceFromY = center.y + config.breezeHeight + curElevation + config.maceHeight;
        maceToY = center.y + config.breezeHeight + curElevation + 1.15D;
        maceTicks = Math.max(2, config.timings.maceFallTicks);
        maceT = 0.0D;
        maceActive = true;

        sound(new Vec3d(center.x, maceFromY, center.z), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);

        sleepTicks(maceTicks);

        // удар булавы по Вихрю
        maceActive = false;
        Vec3d hit = new Vec3d(center.x, maceToY, center.z);
        burst(hit, 45);
        fx(Particle.SWEEP_ATTACK, hit, 6, 0.7D, 0.2D, 0.7D, 0.0D);
        sound(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.6F);
        sound(hit, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 0.6F);
        removeAndForget(maceItem);
        maceItem = null;
        sleepTicks(2);
    }

    /** От удара булавы Вихрь резко падает. */
    private void breezeSlam() throws InterruptedException {
        if (breezeEntity == null) return;

        // резкий удар о землю: ускорение вниз (ease-in)
        int ticks = Math.max(2, config.timings.breezeDownTicks);
        double from = curElevation;
        for (int t = 1; t <= ticks; t++) {
            double p = (double) t / ticks;
            curElevation = from * (1.0D - p * p);
            targetElevation = curElevation;
            breezeSpinTarget = Math.min(11.0D, config.breezeSpin * (1.0D + 0.4D * p));
            sleepTicks(1L);
        }
        curElevation = 0.0D;
        targetElevation = 0.0D;
    }

    /** Момент удара: вспышка, звук, вылет остальных призов, старт орбиты приза. */
    private void onImpact(int winnerIdx) throws InterruptedException {
        Vec3d impact = breezePos(0.0D).add(0.0D, 0.7D, 0.0D);

        sound(impact, impactSound, 1.0F, 0.85F);
        sound(impact, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        burst(impact, 70);
        fx(Particle.SWEEP_ATTACK, impact, 10, 1.1D, 0.3D, 1.1D, 0.0D);
        scatterRing(14);
        puff(12);

        // искры возвращаются в обычный ритм
        sparkSpeedTarget = 1.0D;
        sparkRadiusTarget = 1.0D;
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
            winnerNameRef = ringNames.size() > winnerIdx ? ringNames.get(winnerIdx) : null;
            // дальше призом управляет только орбита — из кольца его убираем
            if (winnerIdx < ringItems.size()) ringItems.set(winnerIdx, null);
            if (winnerIdx < ringNames.size()) ringNames.set(winnerIdx, null);
            breezeSpinTarget = config.breezeSpin * 0.8D;
            winnerOrbit = true;
        }
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
        breezeSpinTarget = config.breezeSpin * 0.6D;
        sleepTicks(config.timings.breezeStayTicks);
    }

    // ── искры и разлёт ──────────────────────────────────────────────────────

    private void spawnSparks() {
        if (!config.sparks || config.sparkCount <= 0) return;

        Material mat = material(config.sparkMaterial, Material.GLOWSTONE_DUST);
        sparkPerRing = config.sparkCount;

        try {
            for (int ring = 0; ring < 2; ring++) {
                for (int i = 0; i < sparkPerRing; i++) {
                    VirtualItem spark = VirtualItem.create();
                    spark.setItem(new ItemStack(mat));
                    spark.setNoGravity(true);
                    spark.setNoMotion();
                    spark.setPos(breezePos(0.0D));
                    trackEntity(spark);
                    activeItems.add(spark);
                    sparks.add(spark);
                }
            }
        } catch (Throwable ignored) {
            // без искр анимация всё равно работает
        }
        sparkActive = !sparks.isEmpty();
    }

    /** Разлетающиеся искры в момент удара — видны и без частиц. */
    private void scatterRing(int count) {
        shockItems.clear();
        Material mat = material(config.shockMaterial, Material.GLOWSTONE_DUST);
        List<Double> angles = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            try {
                VirtualItem spark = VirtualItem.create();
                spark.setItem(new ItemStack(mat));
                spark.setNoGravity(true);
                spark.setNoMotion();
                spark.setPos(center);
                trackEntity(spark);
                activeItems.add(spark);
                shockItems.add(spark);
                angles.add(2.0D * Math.PI * i / count);
            } catch (Throwable ignored) {
            }
        }

        shockAngles = new double[angles.size()];
        for (int i = 0; i < shockAngles.length; i++) {
            shockAngles[i] = angles.get(i);
        }
        shockT = 0.0D;
        shockActive = !shockItems.isEmpty();
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
        sparkActive = false;
        maceActive = false;
        shockActive = false;
        winnerEntityRef = null;
        winnerNameRef = null;
        winnerItem = null;
        maceItem = null;
        clockEntity = null;

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
        sparks.clear();
        shockItems.clear();
        synchronized (posLock) {
            lastPos.clear();
        }
    }

    // ── эффекты ─────────────────────────────────────────────────────────────

    private void swirlColumn(int count) {
        Vec3d base = breezePos(curElevation);
        double a = Math.toRadians(ringAngle * 3.0D);
        for (int i = 0; i < count; i++) {
            double aa = a + i * (Math.PI * 2.0D / count);
            fx(Particle.END_ROD,
                    new Vec3d(base.x + Math.cos(aa) * 0.55D, base.y + 0.18D * i, base.z + Math.sin(aa) * 0.55D),
                    1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
    }

    private void drawRingTrail() {
        int step = 3;
        for (int i = 0; i < ringItems.size(); i++) {
            if (i % step != 0) continue;
            VirtualEntity item = ringItems.get(i);
            if (item == null) continue;
            Vec3d pos = item.getPos();
            if (pos == null) continue;
            fx(trailParticle, pos.add(0.0D, config.standMode ? 0.95D : 0.1D, 0.0D),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void poof(Vec3d pos) {
        if (pos == null) return;
        fx(trailParticle, pos, 8, 0.25D, 0.25D, 0.25D, 0.0D);
    }

    private void burst(Vec3d pos, int count) {
        fx(itemParticle, pos, count, 0.6D, 0.6D, 0.6D, 0.0D);
        fx(breezeParticle, pos, count / 2, 0.5D, 0.4D, 0.5D, 0.03D);
    }

    private void fx(Particle particle, Vec3d pos, int count, double ox, double oy, double oz, double extra) {
        if (pos == null) return;
        try {
            if (isRedstone(particle)) {
                spawnParticle(particle, pos, count, ox, oy, oz, extra, dustOptions);
            } else {
                spawnParticle(particle, pos, count, ox, oy, oz, extra);
            }
        } catch (Throwable ignored) {
        }
    }

    private void sound(Vec3d pos, Sound sound, float volume, float pitch) {
        if (pos == null || sound == null) return;
        try {
            playSound(pos, sound, volume, pitch);
        } catch (Throwable ignored) {
        }
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
        } catch (Throwable e) {
            return fallback;
        }
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable e) {
            return fallback;
        }
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) return fallback;
        try {
            Material m = Material.matchMaterial(name.trim());
            return m != null ? m : fallback;
        } catch (Throwable e) {
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
        final double bobAmplitude;
        final double bobSpeed;
        final double dropHeight;
        final boolean sparks;
        final int sparkCount;
        final double sparkRadius;
        final double sparkSpeed;
        final double sparkHeight;
        final String sparkMaterial;
        final String shockMaterial;
        final boolean mace;
        final String maceMaterial;
        final double maceHeight;
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
            this.breezeSpin = clamp(get(c, "breezeSpin", 6.0D), 0.0D, 30.0D);
            this.breezeRise = clamp(get(c, "breezeRise", 1.6D), 0.0D, 6.0D);
            this.breezeHeight = clamp(get(c, "breezeHeight", -0.45D), -2.0D, 2.0D);
            this.breezeParticle = getStr(c, "breezeParticle", "CLOUD");
            this.winnerOrbitRadius = clamp(get(c, "winnerOrbitRadius", 1.05D), 0.0D, 4.0D);
            this.winnerOrbitSpeed = clamp(get(c, "winnerOrbitSpeed", 8.0D), 0.0D, 20.0D);
            this.bobAmplitude = clamp(get(c, "bobAmplitude", 0.45D), 0.0D, 1.2D);
            this.bobSpeed = clamp(get(c, "bobSpeed", 0.13D), 0.0D, 0.6D);
            this.dropHeight = clamp(get(c, "dropHeight", 5.0D), 0.0D, 12.0D);
            this.sparks = getBool(c, "sparks", true);
            this.sparkCount = (int) clamp(get(c, "sparkCount", 8), 0, 24);
            this.sparkRadius = clamp(get(c, "sparkRadius", 1.15D), 0.0D, 3.0D);
            this.sparkSpeed = clamp(get(c, "sparkSpeed", 18.0D), 0.0D, 90.0D);
            this.sparkHeight = clamp(get(c, "sparkHeight", 0.9D), 0.0D, 3.0D);
            this.sparkMaterial = getStr(c, "sparkMaterial", "GLOWSTONE_DUST");
            this.shockMaterial = getStr(c, "shockMaterial", "GLOWSTONE_DUST");
            this.mace = getBool(c, "mace", true);
            this.maceMaterial = getStr(c, "maceMaterial", "MACE");
            this.maceHeight = clamp(get(c, "maceHeight", 7.0D), 1.0D, 20.0D);
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
        final int breezeHoverTicks;
        final int maceFallTicks;
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
            this.breezeHoverTicks = (int) clamp(get(c, "breezeHoverTicks", 6), 0, 100);
            this.maceFallTicks = (int) clamp(get(c, "maceFallTicks", 8), 2, 100);
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
