package ru.rooyzee.elytrixaddonsphere;

import dev.by1337.bc.CaseBlock;
import dev.by1337.bc.animation.AbstractAnimation;
import dev.by1337.bc.animation.AnimationContext;
import dev.by1337.bc.prize.Prize;
import dev.by1337.bc.prize.PrizeSelector;
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
import java.util.Map;
import java.util.Locale;

public class SphereAnimator extends AbstractAnimation {

    private final Prize winner;
    private final Config config;
    private final Vec3d center;
    private Vec3d ringCenter;
    private final Sound spawnSound;
    private final Sound winSound;
    private final Particle trailParticle;
    private final Particle itemParticle;
    private final Particle.DustOptions dustOptions;

    private final List<VirtualEntity> activeItems = new ArrayList<>();
    private VirtualEntity winnerItem;
    private VirtualEntity[] nameStands;

    /** Вихрь (Breeze) в центре кейса, пакетный моб из VirtualEntityApi. */
    private VirtualEntity breezeEntity;
    private float breezeYaw;
    private double riseOffset;
    private final Particle breezeParticle;

    /** Позиции, которые уже отправлены клиентам — лишние пакеты не шлём. */
    private final Map<VirtualEntity, Vec3d> lastPos = new IdentityHashMap<>();
    /** Угол, под которым приз летает вокруг Вихря. */
    private double winnerOrbitAngle;

    private double orbitAngle = 0.0D;
    private double ringRadius;
    private double swirlAngle = 0.0D;
    private int swirlTick;

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

        this.config = Config.read(config);

        this.ringRadius = this.config.radius;
        this.ringCenter = this.center.add(0.0D, this.config.height, 0.0D);

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

    @Override
    protected void onStart() {
        caseBlock.hideHologram();
        caseBlock.hideBlock();
    }

    @Override
    protected void animate() throws InterruptedException {
        int count = Math.max(4, Math.min(24, config.itemCount));
        int winnerIdx = RANDOM.nextInt(count);

        double[] slot = new double[count];
        for (int i = 0; i < count; i++) {
            slot[i] = (2.0D * Math.PI * i) / count;
        }

        List<VirtualEntity> items = new ArrayList<>(count);
        nameStands = new VirtualEntity[count];

        try {
            spawnBreeze();
            ascent(count, winnerIdx, slot, items);
            orbit(count, items, slot);
            breezeUpDown(count, items, slot);
            converge(count, items, slot);
            showcase(count, winnerIdx, items, slot);
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

    /**
     * Ставит Вихрь (Breeze) в центр кейса. Пакетный моб — не лагает, не бьётся,
     * не зависит от чанков и физики.
     */
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
            breezePuff(6);
        } catch (Throwable t) {
            // VirtualEntityApi не установлен или версия сервера < 1.20.4
            breezeEntity = null;
        }
    }

    private Vec3d breezePos(double rise) {
        return new Vec3d(center.x, center.y + config.breezeHeight + rise, center.z);
    }

    /** Вращение Вихря вокруг своей оси. */
    private void spinBreeze(double degrees) {
        if (breezeEntity == null || degrees == 0.0D) return;
        breezeYaw = (float) ((breezeYaw + degrees) % 360.0D);
        breezeEntity.setYaw(breezeYaw);
    }

    /** Частицы вокруг Вихря. */
    private void breezePuff(int count) {
        if (breezeEntity == null) return;
        Vec3d pos = breezeEntity.getPos();
        if (pos == null) pos = breezePos(riseOffset + 0.9D);
        spawnParticle(breezeParticle, pos.add(0.0D, 0.9D, 0.0D), count, 0.45D, 0.6D, 0.45D, 0.02D);
    }

    /**
     * Вихрь резко взлетает вверх и плавно опускается, кольцо призов летит вместе с ним.
     */
    private void breezeUpDown(int count, List<VirtualEntity> items, double[] slot) throws InterruptedException {
        int up = config.timings.breezeUpTicks;
        int down = config.timings.breezeDownTicks;

        if (breezeEntity == null || config.breezeRise <= 0.0D || up + down <= 0) {
            setRise(0.0D);
            return;
        }

        // взлёт: резкий старт, мягкое торможение у верхней точки
        for (int t = 1; t <= up; t++) {
            double p = (double) t / up;
            double e = 1.0D - Math.pow(1.0D - p, 3.0D);
            setRise(config.breezeRise * e);

            spinBreeze(config.breezeSpin * (0.6D + 0.9D * p));
            orbitAngle += Math.toRadians(config.rotationSpeed * (1.0D + 1.1D * p));

            moveRing(count, items, slot);
            drawItemSwirl(items, slot);
            if (t % 2 == 0) breezePuff(2);
            sleepTicks(1L);
        }

        // падение: плавное, без рывка
        for (int t = 1; t <= down; t++) {
            double p = (double) t / down;
            double e = 1.0D - smoothStep(p);
            setRise(config.breezeRise * e);

            spinBreeze(config.breezeSpin * (1.5D - 0.5D * p));
            orbitAngle += Math.toRadians(config.rotationSpeed * (2.1D - 1.1D * p));

            moveRing(count, items, slot);
            drawItemSwirl(items, slot);
            if (t % 2 == 0) breezePuff(1);
            sleepTicks(1L);
        }

        setRise(0.0D);
    }

    /** Двигает сущность только если позиция реально изменилась — меньше пакетов. */
    private void moveTo(VirtualEntity entity, Vec3d pos) {
        if (entity == null || pos == null) return;
        Vec3d prev = lastPos.get(entity);
        if (prev != null) {
            double dx = prev.x - pos.x;
            double dy = prev.y - pos.y;
            double dz = prev.z - pos.z;
            if (dx * dx + dy * dy + dz * dz < 2.5e-5D) return;
        }
        lastPos.put(entity, pos);
        entity.setPos(pos);
    }

    /** Плавно ведёт всё кольцо призов (и их имена) по орбите. */
    private void moveRing(int count, List<VirtualEntity> items, double[] slot) {
        for (int i = 0; i < count; i++) {
            Vec3d pos = ringPos(i, slot);
            moveTo(items.get(i), pos);
            if (nameStands[i] != null) {
                moveTo(nameStands[i], pos.add(0.0D, config.nameOffset, 0.0D));
            }
        }
    }

    private void setRise(double rise) {
        riseOffset = rise;
        ringCenter = center.add(0.0D, config.height + rise, 0.0D);
        if (breezeEntity != null) {
            breezeEntity.setPos(breezePos(rise));
        }
    }

    /** Пауза после взрыва: Вихрь ещё немного крутится и гаснет. */
    private void breezeOutro() throws InterruptedException {
        if (breezeEntity == null || config.timings.breezeStayTicks <= 0) return;
        for (int t = 0; t < config.timings.breezeStayTicks; t++) {
            spinBreeze(config.breezeSpin * 0.35D);
            if (t % 3 == 0) breezePuff(2);
            sleepTicks(1L);
        }
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

    private void ascent(int count, int winnerIdx, double[] slot, List<VirtualEntity> items) throws InterruptedException {
        int steps = config.timings.ascentSteps;
        int gap = config.timings.ascentGap;
        int total = (count - 1) * gap + steps + 1;

        Vec3d hidden = center.add(0.0D, 6.5D, 0.0D);

        int[] progress = new int[count];
        VirtualEntity[] spawned = new VirtualEntity[count];

        for (int t = 0; t < total; t++) {
            for (int i = 0; i < count; i++) {
                if (t < i * gap) continue;

                if (spawned[i] == null) {
                    Prize prize = (i == winnerIdx) ? winner : safePrize();
                    float yaw = (float) Math.toDegrees(slot[i]) - 90.0F;
                    spawned[i] = spawnDisplay(prize, hidden, yaw);
                    items.add(spawned[i]);

                    if (config.standMode) {
                        nameStands[i] = spawnNameStand(prize, hidden);
                    }

                    if (i == winnerIdx) {
                        winnerItem = spawned[i];
                    }

                    float pitch = 0.55F + 0.6F * ((float) i / count);
                    playSound(center, spawnSound, 0.3F, pitch);
                }

                progress[i]++;
                int s = progress[i] - 1;
                if (s < 1 || s > steps) continue;

                double f = 1.0D - smoothStep((double) s / steps);
                double ang = slot[i] + 0.8D * f;
                double r = config.radius * (1.0D - 0.35D * f);
                double y = config.height + 2.8D * f;

                Vec3d pos = new Vec3d(
                        center.x + Math.cos(ang) * r,
                        center.y + y,
                        center.z + Math.sin(ang) * r
                );
                moveTo(spawned[i], pos);

                if (nameStands[i] != null) {
                    moveTo(nameStands[i], pos.add(0.0D, config.nameOffset, 0.0D));
                }
            }
            sleepTicks(1L);
        }
    }

    private void orbit(int count, List<VirtualEntity> items, double[] slot) throws InterruptedException {
        int ticks = config.timings.orbitTicks;
        double baseRadius = config.radius;
        double speed = Math.toRadians(config.rotationSpeed);

        for (int t = 0; t < ticks; t++) {
            ringRadius = baseRadius * (1.0D - 0.10D * smoothStep((double) t / ticks));

            moveRing(count, items, slot);
            drawItemSwirl(items, slot);
            // плавный разгон вращения Вихря от 0.7 до 1.15 скорости
            spinBreeze(config.breezeSpin * (0.7D + 0.45D * Math.min(1.0D, (double) t / 20.0D)));
            if (t % 3 == 0) breezePuff(1);

            orbitAngle += speed;

            sleepTicks(1L);
        }
    }

    private void converge(int count, List<VirtualEntity> items, double[] slot) throws InterruptedException {
        int ticks = config.timings.convergenceTicks;
        double startRadius = ringRadius;

        for (int t = 0; t < ticks; t++) {
            double sm = smoothStep((double) t / ticks);

            ringRadius = startRadius * (1.0D - 0.5D * sm);

            moveRing(count, items, slot);
            drawItemSwirl(items, slot);
            spinBreeze(config.breezeSpin * (1.0D + 1.6D * sm));
            if (t % 2 == 0) breezePuff(2);

            orbitAngle += Math.toRadians(config.rotationSpeed * (1.0D + 2.2D * sm));

            sleepTicks(1L);
        }

        playSound(ringCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 0.7F);
    }

    private void showcase(int count, int winnerIdx, List<VirtualEntity> items, double[] slot) throws InterruptedException {
        int ticks = config.timings.winnerTicks;

        boolean orbitPrize = config.breeze && breezeEntity != null && config.winnerOrbitRadius > 0.0D;
        double displayY = config.standMode ? 1.0D : 0.0D;
        double yOff = config.standMode ? 0.0D : -0.15D;
        Vec3d showTop = ringCenter.add(0.0D, yOff + 0.2D, 0.0D);

        List<Integer> order = new ArrayList<>();
        for (int k = 1; k < count; k++) {
            order.add((winnerIdx + k) % count);
        }

        int flightTicks = 7;
        int lastDepart = order.size() - 1;
        int winnerFly = 9;
        int revealTick = winnerFly + 1;

        Vec3d wStart = ringPos(winnerIdx, slot);
        winnerOrbitAngle = Math.atan2(wStart.z - ringCenter.z, wStart.x - ringCenter.x);

        Vec3d[] startPos = new Vec3d[count];
        double[] dirX = new double[count];
        double[] dirZ = new double[count];
        int[] departedAt = new int[count];
        for (int i = 0; i < count; i++) {
            departedAt[i] = -1;
        }

        for (int t = 0; t < ticks; t++) {
            double spin = config.rotationSpeed * Math.max(0.15D,
                    3.2D - 3.05D * Math.min(1.0D, (double) t / Math.max(1, lastDepart)));
            orbitAngle += Math.toRadians(spin);

            for (int idx : order) {
                if (departedAt[idx] != -1) continue;
                Vec3d pos = ringPos(idx, slot);
                moveTo(items.get(idx), pos);

                if (nameStands[idx] != null) {
                    moveTo(nameStands[idx], pos.add(0.0D, config.nameOffset, 0.0D));
                }
            }

            if (orbitPrize) {
                double grow = smoothStep(Math.min(1.0D, (double) t / Math.max(1, winnerFly)));
                winnerOrbitAngle += Math.toRadians(config.winnerOrbitSpeed) * (0.35D + 0.65D * grow);

                double r = config.radius * (1.0D - grow) + config.winnerOrbitRadius * grow;
                double bob = 0.10D * Math.sin(0.09D * t);
                Vec3d target = new Vec3d(
                        ringCenter.x + Math.cos(winnerOrbitAngle) * r,
                        ringCenter.y + yOff + bob,
                        ringCenter.z + Math.sin(winnerOrbitAngle) * r
                );

                if (t <= winnerFly) {
                    double e = smoothStep((double) t / winnerFly);
                    moveTo(winnerItem, lerpVec(wStart, target, e).add(0.0D, 0.35D * Math.sin(Math.PI * e), 0.0D));
                } else {
                    moveTo(winnerItem, target);
                }

                if (nameStands[winnerIdx] != null && winnerItem != null) {
                    moveTo(nameStands[winnerIdx], winnerItem.getPos().add(0.0D, config.nameOffset, 0.0D));
                }
            } else if (t <= winnerFly) {
                double e = smoothStep((double) t / winnerFly);
                Vec3d wpos = lerpVec(wStart, showTop, e).add(0.0D, 0.4D * Math.sin(Math.PI * e), 0.0D);
                moveTo(winnerItem, wpos);

                if (nameStands[winnerIdx] != null) {
                    moveTo(nameStands[winnerIdx], wpos.add(0.0D, config.nameOffset, 0.0D));
                }
            }

            for (int k = 0; k < order.size(); k++) {
                int idx = order.get(k);
                if (departedAt[idx] != -1 || t < k) continue;

                departedAt[idx] = t;
                Vec3d sp = items.get(idx).getPos();
                startPos[idx] = sp == null ? ringPos(idx, slot) : sp;

                double rx = startPos[idx].x - ringCenter.x;
                double rz = startPos[idx].z - ringCenter.z;
                double rl = Math.sqrt(rx * rx + rz * rz);
                if (rl < 0.001D) {
                    rx = Math.cos(slot[idx]);
                    rz = Math.sin(slot[idx]);
                    rl = 1.0D;
                }
                dirX[idx] = rx / rl;
                dirZ[idx] = rz / rl;
            }

            for (int idx : order) {
                int d0 = departedAt[idx];
                if (d0 == -1) continue;
                int dt = t - d0;
                if (dt > flightTicks) continue;

                double e = smoothStep((double) dt / flightTicks);
                Vec3d pos = startPos[idx].add(dirX[idx] * 1.7D * e, 2.6D * e, dirZ[idx] * 1.7D * e);
                moveTo(items.get(idx), pos);

                if (nameStands[idx] != null) {
                    moveTo(nameStands[idx], pos.add(0.0D, config.nameOffset, 0.0D));
                }

                if (dt == flightTicks) {
                    poof(pos.add(0.0D, displayY, 0.0D));
                    removeAndForget(items.get(idx));

                    if (nameStands[idx] != null) {
                        removeAndForget(nameStands[idx]);
                        nameStands[idx] = null;
                    }
                }
            }

            if (t == revealTick) {
                playSound(showTop, winSound, 0.8F, 1.0F);
                burst(showTop);
            }

            if (t > revealTick) {
                if (!orbitPrize) {
                    int bt = t - revealTick;
                    double ramp = Math.min(1.0D, bt / 6.0D);
                    double bob = 0.1D * Math.sin(0.13D * bt) * ramp;
                    moveTo(winnerItem, showTop.add(0.0D, bob, 0.0D));
                }
                drawSwirl(showTop);
            }

            sleepTicks(1L);
        }

        // победитель остаётся висеть возле Вихря до конца анимации,
        // удаляется в cleanup() — «возле него остаётся только то, что выпало»
    }

    private Vec3d ringPos(int i, double[] slot) {
        double ang = slot[i] + orbitAngle;
        return new Vec3d(
                ringCenter.x + Math.cos(ang) * ringRadius,
                ringCenter.y,
                ringCenter.z + Math.sin(ang) * ringRadius
        );
    }

    private void drawItemSwirl(List<VirtualEntity> items, double[] slot) {
        if (swirlTick++ % 3 != 0) return;
        swirlAngle += 0.9D;

        double baseY = config.standMode ? 1.0D : 0.15D;

        for (int i = 0; i < items.size(); i++) {
            Vec3d pos = items.get(i).getPos();
            if (pos == null) continue;

            for (int k = 0; k < 1; k++) {
                double a = swirlAngle + slot[i] + k * Math.PI;
                Vec3d point = pos.add(
                        Math.cos(a) * 0.4D,
                        baseY + 0.12D * Math.sin(2.0D * a),
                        Math.sin(a) * 0.4D
                );
                if (isRedstone(trailParticle)) {
                    spawnParticle(Particle.REDSTONE, point, 0, 0.0D, 0.0D, 0.0D, 0.0D, dustOptions);
                } else {
                    spawnParticle(trailParticle, point, 0, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private void drawSwirl(Vec3d top) {
        if (swirlTick++ % 3 != 0) return;

        for (int k = 0; k < 2; k++) {
            swirlAngle += 0.55D;
            double y = 0.45D * Math.sin(swirlAngle * 0.5D);
            Vec3d pos = top.add(
                    Math.cos(swirlAngle) * 0.65D,
                    y,
                    Math.sin(swirlAngle) * 0.65D
            );
            if (isRedstone(itemParticle)) {
                spawnParticle(Particle.REDSTONE, pos, 0, 0.0D, 0.0D, 0.0D, 0.0D, dustOptions);
            } else {
                spawnParticle(itemParticle, pos, 0, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void poof(Vec3d pos) {
        if (pos == null) return;
        if (isRedstone(trailParticle)) {
            spawnParticle(Particle.REDSTONE, pos, 6, 0.22D, 0.22D, 0.22D, 0.0D, dustOptions);
        } else {
            spawnParticle(trailParticle, pos, 6, 0.22D, 0.22D, 0.22D, 0.0D);
        }
    }

    private void burst(Vec3d pos) {
        if (isRedstone(itemParticle)) {
            spawnParticle(Particle.REDSTONE, pos, 40, 0.5D, 0.5D, 0.5D, 0.0D, dustOptions);
        } else {
            spawnParticle(itemParticle, pos, 40, 0.5D, 0.5D, 0.5D, 0.0D);
        }
    }

    private Vec3d lerpVec(Vec3d a, Vec3d b, double t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private double smoothStep(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private Prize safePrize() {
        Prize p = prizeSelector.getRandomPrize();
        return p != null ? p : winner;
    }

    private void removeAndForget(VirtualEntity item) {
        if (item == null) return;
        activeItems.remove(item);
        try {
            removeEntity(item);
        } catch (Throwable ignored) {
        }
    }

    private void cleanup() {
        if (breezeEntity != null) {
            try {
                removeEntity(breezeEntity);
            } catch (Throwable ignored) {
            }
            breezeEntity = null;
        }
        for (VirtualEntity item : new ArrayList<>(activeItems)) {
            try {
                removeEntity(item);
            } catch (Throwable ignored) {
            }
        }
        activeItems.clear();
        lastPos.clear();
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

    private static class Config {
        final double radius;
        final int itemCount;
        final double rotationSpeed;
        final double height;
        final String displayMode;
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
        final double winnerOrbitRadius;
        final double winnerOrbitSpeed;
        final String breezeParticle;
        final Timings timings;

        private Config(CashedYamlContext c) {
            this.radius = clamp(get(c, "radius", 2.5D), 0.8D, 6.0D);
            this.itemCount = (int) clamp(get(c, "itemCount", 10), 4, 24);
            this.rotationSpeed = clamp(get(c, "rotationSpeed", 7.0D), 1.0D, 20.0D);
            this.height = clamp(get(c, "height", 0.35D), 0.0D, 4.0D);
            String mode = getStr(c, "displayMode", "STAND");
            this.displayMode = mode;
            this.standMode = !mode.trim().equalsIgnoreCase("ITEM");
            this.nameOffset = clamp(get(c, "nameOffset", 1.15D), 0.0D, 3.0D);
            this.particleVector = getStr(c, "particleVector", "REDSTONE");
            this.particleItems = getStr(c, "particleItems", "REDSTONE");
            this.red = (int) clamp(get(c, "red", 255), 0, 255);
            this.green = (int) clamp(get(c, "green", 105), 0, 255);
            this.blue = (int) clamp(get(c, "blue", 180), 0, 255);
            this.breeze = getBool(c, "breeze", true);
            this.breezeSpin = clamp(get(c, "breezeSpin", 14.0D), 0.0D, 30.0D);
            this.breezeRise = clamp(get(c, "breezeRise", 2.2D), 0.0D, 6.0D);
            this.breezeHeight = clamp(get(c, "breezeHeight", -0.45D), -2.0D, 2.0D);
            this.winnerOrbitRadius = clamp(get(c, "winnerOrbitRadius", 1.05D), 0.0D, 4.0D);
            this.winnerOrbitSpeed = clamp(get(c, "winnerOrbitSpeed", 3.5D), 0.0D, 20.0D);
            this.breezeParticle = getStr(c, "breezeParticle", "CLOUD");
            this.timings = new Timings(c);
        }

        static Config read(CashedYamlContext ctx) {
            return new Config(ctx);
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
        final int convergenceTicks;
        final int winnerTicks;
        final int breezeStayTicks;
        final String spawnSound;
        final String winSound;

        Timings(CashedYamlContext c) {
            this.ascentSteps = (int) clamp(get(c, "ascentSteps", 6), 2, 15);
            this.ascentGap = (int) clamp(get(c, "ascentGap", 2), 1, 5);
            this.orbitTicks = (int) clamp(get(c, "orbitTicks", 100), 20, 1200);
            this.breezeUpTicks = (int) clamp(get(c, "breezeUpTicks", get(c, "riseTicks", 8)), 0, 200);
            this.breezeDownTicks = (int) clamp(get(c, "breezeDownTicks", 22), 0, 200);
            this.convergenceTicks = (int) clamp(get(c, "convergenceTicks", 40), 10, 200);
            this.winnerTicks = (int) clamp(get(c, "winnerTicks", 45), 10, 200);
            this.breezeStayTicks = (int) clamp(get(c, "breezeStayTicks", 10), 0, 200);
            this.spawnSound = str(c, "spawnSound", "BLOCK_NOTE_BLOCK_PLING");
            this.winSound = str(c, "winSound", "ENTITY_PLAYER_LEVELUP");
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
