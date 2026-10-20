package ru.rooyzee.elytrixaddonsphere;

import dev.by1337.bc.CaseBlock;
import dev.by1337.bc.animation.AbstractAnimation;
import dev.by1337.bc.animation.AnimationContext;
import dev.by1337.bc.prize.Prize;
import dev.by1337.bc.prize.PrizeSelector;
import dev.by1337.bc.task.AsyncTask;
import dev.by1337.bc.yaml.CashedYamlContext;
import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.task.TickTask;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.item.VirtualItem;
import dev.by1337.virtualentity.api.virtual.monster.breeze.VirtualBreeze;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
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
    /** Лёгкая атмосферная частица (1.21.4+), парсится по имени с фолбэком. */
    private final Particle ambientParticle;
    /** Частица, из которой собран сам вихрь (режим VORTEX). */
    private final Particle vortexParticle;
    /** Частицы удара булавы: порыв (GUST_EMITTER_*), GUST и белый дым. */
    private final Particle maceParticle;
    private final Particle gustParticle;
    private final Particle smokeParticle;
    private final Particle.DustOptions dustOptions;

    private final List<VirtualEntity> activeItems = new ArrayList<>();
    private final Map<VirtualEntity, Vec3d> lastPos = new IdentityHashMap<>();
    private final Object posLock = new Object();

    /** Призы, которые после удара булавы резко падают вниз. */
    private final List<VirtualEntity> fallingItems = new ArrayList<>();
    private final List<VirtualEntity> fallingNames = new ArrayList<>();
    private final List<Vec3d> fallingPos = new ArrayList<>();

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

    /** Кольцо частиц, расходящееся от места удара. */
    private volatile int waveTicks;

    /** Падение призов после удара булавы. */
    private volatile boolean fallingActive;
    private volatile double fallingP;

    /** Сколько ещё тиков игрок с булавой стоит после удара. */
    private volatile int maceHold;

    /** Вихрь «дрожит» от полученного урона. */
    private volatile int shakeTicks;

    /** Призы уже уронили (чтобы не уронить дважды). */
    private volatile boolean othersDropped;

    private volatile boolean maceActive;
    private volatile double maceT;
    private volatile double maceFromY;
    private volatile double maceToY;
    private volatile int maceTicks = 8;

    private volatile boolean winnerOrbit;
    private volatile double winnerAngle;
    private volatile double winnerRadius;
    private volatile double winnerRadiusTarget;
    private volatile double winnerSpeed;
    private volatile double winnerPhase;
    private volatile double winnerY;
    private volatile VirtualEntity winnerEntityRef;
    private volatile VirtualEntity winnerNameRef;

    /** Кэш скина: [value, signature]. Подтягивается один раз в фоне. */
    private static volatile String[] skinCache;
    private static volatile boolean skinLoading;

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
        this.ambientParticle = parseParticle(this.config.ambientParticle, Particle.CLOUD);
        this.vortexParticle = parseParticle(this.config.vortexParticle, Particle.CLOUD);
        this.maceParticle = parseParticle(this.config.maceParticle, Particle.CLOUD);
        this.gustParticle = parseParticle(this.config.gustParticle, Particle.CLOUD);
        this.smokeParticle = parseParticle(this.config.smokeParticle, Particle.CLOUD);

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
            prefetchSkin(config.maceName);
            spawnBreeze();
            spawnClock();
            ascent(count, winnerIdx);
            orbitPhase();

            // Вихрь подлетает, замирает — сверху падает булава и бьёт его
            breezeUp();
            sleepTicks(config.timings.breezeHoverTicks);
            maceStrike(winnerIdx);
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
        if (config.vortexMode) {
            // без моба: вихрь рисуется частицами, у моба Breeze есть
            // собственный снежный «аура», который плагином не отключить
            breezeEntity = null;
            breezeYaw = 0.0F;
            breezeSpinSpeed = 0.0D;
            breezeSpinTarget = config.breezeSpin;
            breezeSpin = true;
            return;
        }

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

    /** Есть ли что анимировать: моб Breeze или вихрь из частиц. */
    private boolean hasVortex() {
        return breezeEntity != null || config.vortexMode;
    }

    private Vec3d breezePos(double rise) {
        return new Vec3d(center.x, center.y + config.breezeHeight + rise, center.z);
    }

    private void puff(int count) {
        Vec3d pos = breezeEntity == null ? null : breezeEntity.getPos();
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

            if (ringActive && curSpeed > 1.0D && (++trailTick % 8 == 0)) {
                drawRingTrail();
            }

            driveBreeze();
            driveMace();
            driveFalling();
            driveWave();
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
            Vec3d bodyPos = breezePos(curElevation);
            if (shakeTicks > 0) {
                // вихрь дёргается, будто получил урон
                shakeTicks--;
                bodyPos = bodyPos.add(
                        (RANDOM.nextDouble() - 0.5D) * 0.22D,
                        (RANDOM.nextDouble() - 0.5D) * 0.16D,
                        (RANDOM.nextDouble() - 0.5D) * 0.22D
                );
            }
            breezeEntity.setPos(bodyPos);
            breezeEntity.setYaw(breezeYaw);
            if (puffTicks % 10 == 0) {
                puff(1);
            }
        } else {
            // сам вихрь: спираль из частиц, плотность настраивается
            int every = Math.max(1, config.vortexEvery);
            if (puffTicks % every == 0) {
                spawnVortexParticle();
            }
        }
        puffTicks++;

        // атмосфера: одна лёгкая частица раз в несколько тиков
        if (config.ambientEvery > 0 && puffTicks % config.ambientEvery == 0) {
            double aa = Math.toRadians(breezeYaw + RANDOM.nextInt(360));
            double rr = 0.7D + RANDOM.nextDouble() * 0.6D;
            fx(ambientParticle, new Vec3d(
                    center.x + Math.cos(aa) * rr,
                    center.y + config.breezeHeight + curElevation + 0.4D + RANDOM.nextDouble() * 0.9D,
                    center.z + Math.sin(aa) * rr
            ), 0, 0.05D, 0.15D, 0.05D, 0.0D);
        }

        // редкая винтовая искра вокруг Вихря (видна и при «Частицы: Меньше»)
        if (breezeEntity != null && puffTicks % 5 == 0) {
            double baseY = center.y + config.breezeHeight + curElevation;
            double a = Math.toRadians(breezeYaw * 2.0D);
            fx(Particle.FIREWORK, new Vec3d(
                    center.x + Math.cos(a) * 0.8D,
                    baseY + 0.6D + 0.25D * Math.sin(Math.toRadians(breezeYaw)),
                    center.z + Math.sin(a) * 0.8D
            ), 0, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Одна частица спирали вихря: крутится, гуляет по высоте вверх-вниз. */
    private void spawnVortexParticle() {
        double a = Math.toRadians(breezeYaw * 3.0D);
        double r = config.vortexRadius;
        double baseY = center.y + config.breezeHeight + curElevation;
        double h = 0.55D * (1.0D + Math.sin(Math.toRadians(breezeYaw * 1.6D)));

        fx(vortexParticle, new Vec3d(
                center.x + Math.cos(a) * r,
                baseY + 0.25D + h,
                center.z + Math.sin(a) * r
        ), 0, 0.03D, 0.12D, 0.03D, 0.0D);
    }

    private void driveMace() {
        if (maceItem == null) return;

        if (maceActive) {
            maceT = Math.min(1.0D, maceT + 1.0D / Math.max(1, maceTicks));
            double p = maceT * maceT; // ускорение к земле
            double y = maceFromY + (maceToY - maceFromY) * p;
            moveTo(maceItem, new Vec3d(center.x, y, center.z));
            if (maceTicks > 0 && ((int) (maceT * maceTicks)) % 2 == 0) {
                fxHit(smokeParticle, new Vec3d(center.x, y + 1.1D, center.z), 0, 0.0D, 0.05D, 0.0D, 0.0D);
            }
            return;
        }

        // после удара игрок ещё мгновение стоит и исчезает
        if (maceHold > 0 && --maceHold == 0) {
            poof(maceItem.getPos());
            removeAndForget(maceItem);
            maceItem = null;
        }
    }

    /** Резкое падение всех призов, кроме выигрыша. */
    private void driveFalling() {
        if (!fallingActive || fallingItems.isEmpty()) return;

        fallingP = Math.min(1.0D, fallingP + 1.0D / 6.0D);
        double dy = -1.4D * fallingP * fallingP - 0.3D * fallingP;

        for (int i = 0; i < fallingItems.size(); i++) {
            Vec3d start = fallingPos.get(i);
            Vec3d pos = new Vec3d(start.x, start.y + dy, start.z);
            moveTo(fallingItems.get(i), pos);
            if (i < fallingNames.size() && fallingNames.get(i) != null) {
                moveTo(fallingNames.get(i), pos.add(0.0D, config.nameOffset, 0.0D));
            }
        }

        if (fallingP >= 1.0D) {
            for (int i = 0; i < fallingItems.size(); i++) {
                if (i % 5 == 0) fx(trailParticle, fallingItems.get(i).getPos(), 1, 0.2D, 0.2D, 0.2D, 0.0D);
                removeAndForget(fallingItems.get(i));
                if (i < fallingNames.size()) {
                    removeAndForget(fallingNames.get(i));
                }
            }
            fallingItems.clear();
            fallingNames.clear();
            fallingPos.clear();
            fallingActive = false;
        }
    }

    /** Запускает расходящееся кольцо частиц. */
    private void spawnWave() {
        waveTicks = 8;
    }

    /** Расходящееся кольцо частиц после удара. */
    private void driveWave() {
        if (waveTicks <= 0) return;

        waveTicks--;
        double p = 1.0D - waveTicks / 8.0D;
        double r = 0.35D + 3.1D * p;
        double y = center.y + config.height + 0.45D * (1.0D - p);
        int points = 7;

        for (int i = 0; i < points; i++) {
            double a = 2.0D * Math.PI * i / points;
            fxHit(gustParticle, new Vec3d(
                    center.x + Math.cos(a) * r,
                    y,
                    center.z + Math.sin(a) * r
            ), 0, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void driveWinner() {
        VirtualEntity we = winnerEntityRef;
        if (!winnerOrbit || we == null) return;

        winnerAngle = (winnerAngle + winnerSpeed) % 360.0D;
        winnerRadius += (winnerRadiusTarget - winnerRadius) * 0.22D;
        winnerY += ((center.y + config.breezeHeight + 1.15D) - winnerY) * 0.22D;

        double rad = Math.toRadians(winnerAngle);

        Vec3d pos = new Vec3d(
                center.x + Math.cos(rad) * winnerRadius,
                winnerY,
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
        if (!hasVortex() || config.breezeRise <= 0.0D) return;

        breezeSpinTarget = Math.min(9.0D, config.breezeSpin * 1.35D);
        targetSpeed = config.rotationSpeed * 1.45D;

        sound(breezePos(curElevation), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9F, 0.75F);

        int ticks = Math.max(2, config.timings.breezeUpTicks);
        for (int t = 1; t <= ticks; t++) {
            double p = (double) t / ticks;
            // smoothstep: мягкий старт, плавное торможение у верхней точки
            double e = p * p * (3.0D - 2.0D * p);
            curElevation = config.breezeRise * e;
            targetElevation = curElevation;
            if (t % 2 == 0) swirlColumn(1);
            sleepTicks(1L);
        }
        curElevation = config.breezeRise;
        targetElevation = config.breezeRise;

        sound(breezePos(curElevation), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.5F);
        burst(breezePos(curElevation).add(0.0D, 1.0D, 0.0D), 8);
    }

    /** Сверху падает булава и бьёт Вихря. */
    /** Сверху прыгает виртуальный игрок с булавой и бьёт Вихря. */
    private void maceStrike(int winnerIdx) throws InterruptedException {
        if (!config.mace) return;

        VirtualPlayer attacker = null;
        try {
            attacker = VirtualPlayer.create();
            attacker.setName(config.maceName);
            attacker.setListed(false);
            attacker.setCustomNameVisible(false);
            attacker.setSilent(true);
            attacker.setNoGravity(true);
            attacker.setNoMotion();
            attacker.setEquipment(EquipmentSlot.MAINHAND,
                    new ItemStack(material(config.maceMaterial, Material.IRON_AXE)));
            attacker.setPos(new Vec3d(
                    center.x,
                    center.y + config.breezeHeight + curElevation + config.maceHeight,
                    center.z
            ));
            attacker.setYaw(0.0F);
            attacker.setPitch(45.0F);
            applySkin(attacker);
            trackEntity(attacker);
            activeItems.add(attacker);
        } catch (Throwable t) {
            attacker = null;
        }

        if (attacker == null) {
            sleepTicks(4);
            return;
        }

        maceItem = attacker;
        maceFromY = center.y + config.breezeHeight + curElevation + config.maceHeight;
        maceToY = center.y + config.breezeHeight + curElevation + 0.05D;
        maceTicks = Math.max(2, config.timings.maceFallTicks);
        maceT = 0.0D;
        maceActive = true;

        sound(new Vec3d(center.x, maceFromY, center.z), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);

        sleepTicks(maceTicks);

        // удар: замах рукой, звук, частицы
        maceActive = false;
        Vec3d hit = new Vec3d(center.x, maceToY, center.z);
        try {
            ((VirtualPlayer) maceItem).playAnimation(EntityAnimation.SWING_MAIN_ARM);
        } catch (Throwable ignored) {
        }
        // частицы булавы: порыв ветра (как у mace smash), взмах и пыль
        Vec3d chest = hit.add(0.0D, 1.0D, 0.0D);
        fxHit(maceParticle, chest, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        fxHit(gustParticle, chest, 6, 0.35D, 0.3D, 0.35D, 0.05D);
        fxHit(smokeParticle, hit, 8, 0.35D, 0.25D, 0.35D, 0.02D);
        fxHit(Particle.SWEEP_ATTACK, hit, 3, 0.8D, 0.2D, 0.8D, 0.0D);
        fxHit(itemParticle, hit, 8, 0.4D, 0.3D, 0.4D, 0.0D);
        sound(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.6F);
        sound(hit, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 0.6F);
        sound(hit, parseSound(config.timings.hurtSound, Sound.ENTITY_BREEZE_JUMP), 1.0F, 0.85F);

        // вихрь дёргается от урона, пока падает
        shakeTicks = 8;

        // все призы, кроме выигрыша, резко падают вниз
        dropOthers(winnerIdx);

        // игрок исчезает через пару тиков, а Вихрь падает сразу
        maceHold = 4;
    }

    /** Ставит на игрока скин (значение/подпись подтягиваются один раз). */
    private void applySkin(VirtualPlayer player) {
        try {
            String[] skin = skinCache;
            if (skin != null && skin.length == 2) {
                player.setTexture(skin[0], skin[1]);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Резко уронить все призы, кроме выигрыша. */
    private void dropOthers(int winnerIdx) {
        if (othersDropped) return;
        othersDropped = true;

        for (int i = 0; i < ringItems.size(); i++) {
            if (i == winnerIdx) continue;
            VirtualEntity item = ringItems.get(i);
            if (item == null) continue;

            Vec3d pos = item.getPos();
            if (pos == null) pos = ringPos(slots[i]);

            fallingItems.add(item);
            fallingPos.add(new Vec3d(pos.x, pos.y, pos.z));
            fallingNames.add(i < ringNames.size() ? ringNames.get(i) : null);

            if (i % 5 == 0) fx(trailParticle, pos, 1, 0.2D, 0.2D, 0.2D, 0.0D);

            ringItems.set(i, null);
            if (i < ringNames.size()) ringNames.set(i, null);
        }

        if (!fallingItems.isEmpty()) {
            fallingP = 0.0D;
            fallingActive = true;
            sound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 0.55F);
        }
    }

    /** От удара булавы Вихрь резко падает. */
    private void breezeSlam() throws InterruptedException {
        if (!hasVortex()) return;

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
        fxHit(maceParticle, impact, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        fxHit(gustParticle, impact, 8, 0.7D, 0.4D, 0.7D, 0.05D);
        fxHit(smokeParticle, impact, 10, 0.6D, 0.35D, 0.6D, 0.02D);
        fxHit(itemParticle, impact, 10, 0.6D, 0.5D, 0.6D, 0.0D);
        puff(2);

        // если булавы нет — призы падают именно здесь
        dropOthers(winnerIdx);

        spawnWave();
        sleepTicks(2);

        // страховка: если что-то осталось в кольце — убираем (кроме выигрыша)
        for (int i = 0; i < ringItems.size(); i++) {
            if (i == winnerIdx || ringItems.get(i) == null) continue;
            removeAndForget(ringItems.get(i));
            ringItems.set(i, null);
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
        burst(winnerItem.getPos() == null ? center : winnerItem.getPos(), 8);
        sleepTicks(Math.max(10, ticks - 10));
    }

    private void breezeOutro() throws InterruptedException {
        if (!hasVortex() || config.timings.breezeStayTicks <= 0) return;
        breezeSpinTarget = config.breezeSpin * 0.6D;
        sleepTicks(config.timings.breezeStayTicks);
    }

    // ── искры и разлёт ──────────────────────────────────────────────────────

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
        maceActive = false;
        waveTicks = 0;
        maceHold = 0;
        shakeTicks = 0;
        fallingActive = false;
        othersDropped = false;
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
        fallingItems.clear();
        fallingNames.clear();
        fallingPos.clear();
        entryFrom.clear();
        entryT.clear();
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
        fx(trailParticle, pos, 2, 0.2D, 0.2D, 0.2D, 0.0D);
    }

    private void burst(Vec3d pos, int count) {
        fxHit(itemParticle, pos, Math.max(3, count / 2), 0.6D, 0.6D, 0.6D, 0.0D);
    }

    /** Атмосферные частицы (вихрь, следы, дым). */
    private void fx(Particle particle, Vec3d pos, int count, double ox, double oy, double oz, double extra) {
        if (!config.particles || !config.particlesAmbient) return;
        rawFx(particle, pos, count, ox, oy, oz, extra);
    }

    /** Частицы ударов: булава, приземление, урон. */
    private void fxHit(Particle particle, Vec3d pos, int count, double ox, double oy, double oz, double extra) {
        if (!config.particles || !config.particlesImpact) return;
        rawFx(particle, pos, count, ox, oy, oz, extra);
    }

    private void rawFx(Particle particle, Vec3d pos, int count, double ox, double oy, double oz, double extra) {
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

    // ── скин игрока ─────────────────────────────────────────────────────────

    /** Асинхронно подтягивает скин ника (Mojang API) — один раз на сервер. */
    private static void prefetchSkin(String name) {
        if (name == null || name.trim().isEmpty()) return;
        if (skinCache != null || skinLoading) return;
        skinLoading = true;

        Thread thread = new Thread(() -> {
            try {
                skinCache = lookupSkin(name.trim());
            } catch (Throwable ignored) {
            }
        }, "ElytrixSkin");
        thread.setDaemon(true);
        thread.start();
    }

    private static String[] lookupSkin(String name) {
        String uuid = null;
        try {
            String json = http("https://api.mojang.com/users/profiles/minecraft/" + name);
            String id = jsonField(json, "id");
            if (id != null && !id.isEmpty()) uuid = id;
        } catch (Throwable ignored) {
        }
        if (uuid == null) return null;

        try {
            String json = http("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            String value = jsonField(json, "value");
            String signature = jsonField(json, "signature");
            if (value != null && signature != null) {
                return new String[]{value, signature};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String http(String url) throws Exception {
        java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        con.setConnectTimeout(3000);
        con.setReadTimeout(3000);
        con.setRequestProperty("User-Agent", "ElytrixAddonSphere");
        try (java.io.InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    /** Достаёт строковое значение поля из простого JSON. */
    private static String jsonField(String json, String field) {
        if (json == null) return null;
        int key = json.indexOf("\"" + field + "\"");
        if (key < 0) return null;
        int colon = json.indexOf(':', key + field.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
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
        final boolean particles;
        final boolean particlesAmbient;
        final boolean particlesImpact;
        final boolean breeze;
        final boolean vortexMode;
        final String vortexParticle;
        final String maceParticle;
        final String gustParticle;
        final String smokeParticle;
        final int vortexEvery;
        final double vortexRadius;
        final double breezeSpin;
        final double breezeRise;
        final double breezeHeight;
        final String breezeParticle;
        final String ambientParticle;
        final int ambientEvery;
        final double winnerOrbitRadius;
        final double winnerOrbitSpeed;
        final double bobAmplitude;
        final double bobSpeed;
        final double dropHeight;
        final boolean mace;
        final String maceMaterial;
        final String maceName;
        final double maceHeight;
        final Timings timings;

        private Config(CashedYamlContext c) {
            this.radius = clamp(get(c, "radius", 2.5D), 0.8D, 6.0D);
            this.itemCount = (int) clamp(get(c, "itemCount", 10), 4, 24);
            this.rotationSpeed = clamp(get(c, "rotationSpeed", 7.0D), 1.0D, 20.0D);
            this.height = clamp(get(c, "height", 0.35D), 0.0D, 4.0D);
            this.standMode = !getStr(c, "displayMode", "STAND").trim().equalsIgnoreCase("ITEM");
            this.nameOffset = clamp(get(c, "nameOffset", 1.15D), 0.0D, 3.0D);
            this.particleVector = getStr(c, "particleVector", "FIREWORK");
            this.particleItems = getStr(c, "particleItems", "REDSTONE");
            this.red = (int) clamp(get(c, "red", 255), 0, 255);
            this.green = (int) clamp(get(c, "green", 105), 0, 255);
            this.blue = (int) clamp(get(c, "blue", 180), 0, 255);
            this.particles = getBool(c, "particles", true);
            this.particlesAmbient = getBool(c, "particlesAmbient", false);
            this.particlesImpact = getBool(c, "particlesImpact", true);
            this.breeze = getBool(c, "breeze", true);
            this.vortexMode = getStr(c, "breezeMode", "BREEZE").trim().equalsIgnoreCase("VORTEX");
            this.vortexParticle = getStr(c, "vortexParticle", "WHITE_SMOKE");
            this.maceParticle = getStr(c, "maceParticle", "GUST_EMITTER_LARGE");
            this.gustParticle = getStr(c, "gustParticle", "GUST");
            this.smokeParticle = getStr(c, "smokeParticle", "WHITE_SMOKE");
            this.vortexEvery = (int) clamp(get(c, "vortexEvery", 3), 1, 20);
            this.vortexRadius = clamp(get(c, "vortexRadius", 0.55D), 0.1D, 2.0D);
            this.breezeSpin = clamp(get(c, "breezeSpin", 6.0D), 0.0D, 30.0D);
            this.breezeRise = clamp(get(c, "breezeRise", 2.4D), 0.0D, 6.0D);
            this.breezeHeight = clamp(get(c, "breezeHeight", -0.45D), -2.0D, 2.0D);
            this.breezeParticle = getStr(c, "breezeParticle", "CLOUD");
            this.ambientParticle = getStr(c, "ambientParticle", "WHITE_SMOKE");
            this.ambientEvery = (int) clamp(get(c, "ambientEvery", 14), 0, 200);
            this.winnerOrbitRadius = clamp(get(c, "winnerOrbitRadius", 1.05D), 0.0D, 4.0D);
            this.winnerOrbitSpeed = clamp(get(c, "winnerOrbitSpeed", 8.0D), 0.0D, 20.0D);
            this.bobAmplitude = clamp(get(c, "bobAmplitude", 0.0D), 0.0D, 1.2D);
            this.bobSpeed = clamp(get(c, "bobSpeed", 0.13D), 0.0D, 0.6D);
            this.dropHeight = clamp(get(c, "dropHeight", 5.0D), 0.0D, 12.0D);
            this.mace = getBool(c, "mace", true);
            this.maceMaterial = getStr(c, "maceMaterial", "MACE");
            this.maceName = getStr(c, "maceName", "rooyzee");
            this.maceHeight = clamp(get(c, "maceHeight", 12.0D), 1.0D, 30.0D);
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
        final String hurtSound;

        Timings(CashedYamlContext c) {
            this.ascentSteps = (int) clamp(get(c, "ascentSteps", 8), 2, 15);
            this.ascentGap = (int) clamp(get(c, "ascentGap", 2), 1, 5);
            this.orbitTicks = (int) clamp(get(c, "orbitTicks", 75), 20, 1200);
            this.breezeUpTicks = (int) clamp(get(c, "breezeUpTicks", 20), 2, 200);
            this.breezeHoverTicks = (int) clamp(get(c, "breezeHoverTicks", 6), 0, 100);
            this.maceFallTicks = (int) clamp(get(c, "maceFallTicks", 8), 2, 100);
            this.breezeDownTicks = (int) clamp(get(c, "breezeDownTicks", 4), 2, 200);
            this.winnerOrbitTicks = (int) clamp(get(c, "winnerOrbitTicks", 80), 20, 400);
            this.breezeStayTicks = (int) clamp(get(c, "breezeStayTicks", 12), 0, 200);
            this.spawnSound = str(c, "spawnSound", "BLOCK_NOTE_BLOCK_PLING");
            this.winSound = str(c, "winSound", "ENTITY_PLAYER_LEVELUP");
            this.impactSound = str(c, "impactSound", "ENTITY_GENERIC_EXPLODE");
            this.hurtSound = str(c, "hurtSound", "ENTITY_GENERIC_HURT");
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
