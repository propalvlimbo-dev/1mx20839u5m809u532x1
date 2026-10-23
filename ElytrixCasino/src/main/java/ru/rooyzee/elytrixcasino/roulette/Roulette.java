package ru.rooyzee.elytrixcasino.roulette;

import dev.by1337.virtualentity.api.entity.EntityAnimation;
import dev.by1337.virtualentity.api.entity.EquipmentSlot;
import dev.by1337.virtualentity.api.entity.HumanoidArm;
import dev.by1337.virtualentity.api.tracker.PlayerTracker;
import dev.by1337.virtualentity.api.virtual.VirtualEntity;
import dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand;
import dev.by1337.virtualentity.api.virtual.item.VirtualItem;
import dev.by1337.virtualentity.api.virtual.player.VirtualPlayer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.by1337.blib.geom.Vec3d;
import org.by1337.blib.geom.Vec3f;
import ru.rooyzee.elytrixcasino.Main;
import ru.rooyzee.elytrixcasino.util.Text;
import ru.rooyzee.elytrixcasino.util.Versions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Roulette {

    public enum State {
        IDLE,
        WALKING,
        SPINNING,
        RETURNING
    }

    private static final double POINTER_ANGLE = Math.PI / 2.0D;
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double NPC_EYE_HEIGHT = 1.62D;
    private static final byte ALL_SKIN_LAYERS = 0x7E;
    private static final double DECELERATION_START = 0.55D;
    private static final int REFUSAL_TICKS = 16;
    private static final String NAME_TEAM = "ec_npc";

    private final Main plugin;
    private final String id;
    private final Location npcHome;
    private final Location wheelCenter;

    private final List<VirtualArmorStand> stands = new ArrayList<>();
    private final List<Sector> slotSectors = new ArrayList<>();
    private final List<VirtualArmorStand> nameHolos = new ArrayList<>();
    private final List<String> holoTemplates = new ArrayList<>();

    private VirtualPlayer npc;
    private VirtualArmorStand pointer;
    private PlayerTracker tracker;
    private BukkitTask trackerTask;
    private BukkitTask animationTask;
    private BukkitTask jackpotTask;

    private Vector forward = new Vector(0, 0, 1);
    private Vector right = new Vector(1, 0, 0);
    private float standYaw;

    private State state = State.IDLE;
    private UUID currentPlayer;
    private long currentBet;
    private double rotation;
    private double angularSpeed;
    private boolean spawned;
    private int tickCounter;
    private boolean lookingAtPlayer;
    private boolean listedSupported = true;
    private BukkitTask refusalTask;
    private float currentYaw;
    private float currentPitch;
    private float targetYaw;
    private float targetPitch;
    private boolean manualRotation;

    public Roulette(Main plugin, String id, Location npcHome, Location wheelCenter) {
        this.plugin = plugin;
        this.id = id;
        this.npcHome = npcHome.clone();
        this.wheelCenter = wheelCenter.clone();
    }

    private RouletteSettings settings() {
        return plugin.getSettings();
    }

    public boolean spawn() {
        World world = npcHome.getWorld();
        if (world == null || wheelCenter.getWorld() == null || !world.equals(wheelCenter.getWorld())) {
            plugin.getLogger().warning("Рулетка " + id + " не заспавнена: мир не загружен или NPC и колесо в разных мирах.");
            return false;
        }
        if (spawned) {
            despawn();
        }

        computeAxes();
        createNpc();
        createWheel();

        List<VirtualEntity> entities = new ArrayList<>();
        entities.add(npc);
        entities.addAll(nameHolos);
        entities.addAll(stands);
        entities.add(pointer);

        Location center = npcHome.clone().add(wheelCenter).multiply(0.5D);
        tracker = new PlayerTracker(world, entities, new Vec3d(center.getX(), center.getY(), center.getZ()),
                settings().getViewRadius());
        trackerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);

        rotation = 0.0D;
        angularSpeed = 0.0D;
        state = State.IDLE;
        spawned = true;
        return true;
    }

    private void createNpc() {
        npc = VirtualPlayer.create();
        String profile = buildHiddenName();
        npc.setName(profile);
        hideNametag(profile);
        npc.setPos(new Vec3d(npcHome.getX(), npcHome.getY(), npcHome.getZ()));
        currentYaw = npcHome.getYaw();
        currentPitch = npcHome.getPitch();
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        npc.setYaw(currentYaw);
        npc.setPitch(currentPitch);
        npc.setPlayerModeCustomisation(ALL_SKIN_LAYERS);
        npc.setPlayerMainHand(HumanoidArm.RIGHT);
        npc.setSilent(true);
        npc.setNoGravity(true);
        npc.setOnGround(true);

        String skinValue = plugin.getSkinValue();
        String skinSignature = plugin.getSkinSignature();
        if (skinValue != null && !skinValue.isEmpty() && skinSignature != null && !skinSignature.isEmpty()) {
            npc.setTexture(skinValue, skinSignature);
        }

        if (!settings().getNpcHandItem().isEmpty()) {
            Material hand = Material.matchMaterial(settings().getNpcHandItem());
            if (hand != null && hand.isItem()) {
                npc.setEquipment(EquipmentSlot.MAINHAND, new ItemStack(hand));
            }
        }

        listedSupported = Versions.supportsTabListedFlag();
        if (settings().isHideFromTab() && listedSupported) {
            try {
                npc.setListed(false);
            } catch (Throwable ignored) {
                listedSupported = false;
            }
        }
        createNameHolo();
    }

    private String buildHiddenName() {
        String clean = id.replaceAll("[^a-zA-Z0-9_]", "");
        String raw = "ec_r_" + (clean.isEmpty() ? "npc" : clean);
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    private void createNameHolo() {
        nameHolos.clear();
        holoTemplates.clear();
        List<String> lines = settings().getHologramLines();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (String tpl : lines) {
            VirtualArmorStand stand = VirtualArmorStand.create();
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setNoBasePlate(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setCustomName(Text.component(Text.resolveHoloLine(tpl, 0)));
            stand.setCustomNameVisible(true);
            nameHolos.add(stand);
            holoTemplates.add(tpl);
        }
        followNameHolo();
    }

    private void followNameHolo() {
        if (nameHolos.isEmpty() || npc == null) {
            return;
        }
        Vec3d pos = npc.getPos();
        int total = nameHolos.size();
        double base = settings().getHologramHeight();
        double gap = settings().getHologramLineGap();
        for (int i = 0; i < total; i++) {
            double y = pos.getY() + base + (total - 1 - i) * gap;
            nameHolos.get(i).setPos(new Vec3d(pos.getX(), y, pos.getZ()));
        }
    }

    private void hideNametag(String entry) {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(NAME_TEAM);
            if (team == null) {
                team = board.registerNewTeam(NAME_TEAM);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        } catch (Throwable ignored) {
        }
    }

    private void createWheel() {
        List<Sector> sectors = settings().getSectors();
        int count = settings().getStandCount();
        int[] placed = new int[sectors.size()];
        int filled = 0;

        for (int s = 0; s < sectors.size() && filled < count; s++) {
            if (sectors.get(s).getWeight() > 0) {
                slotSectors.add(sectors.get(s));
                placed[s]++;
                filled++;
            }
        }
        int totalWeight = Math.max(1, settings().getTotalWeight());
        while (filled < count) {
            int bestIndex = -1;
            double bestDeficit = -1.0D;
            for (int s = 0; s < sectors.size(); s++) {
                double desired = (double) sectors.get(s).getWeight() * count / (double) totalWeight;
                double deficit = desired - placed[s];
                if (deficit > bestDeficit) {
                    bestDeficit = deficit;
                    bestIndex = s;
                }
            }
            if (bestIndex < 0) {
                bestIndex = filled % sectors.size();
            }
            slotSectors.add(sectors.get(bestIndex));
            placed[bestIndex]++;
            filled++;
        }

        for (int i = 0; i < count; i++) {
            Sector sector = slotSectors.get(i);

            VirtualArmorStand stand = VirtualArmorStand.create();
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setNoBasePlate(true);
            stand.setNoGravity(true);
            stand.setSilent(true);
            stand.setYaw(standYaw);
            stand.setEquipment(EquipmentSlot.HEAD, sector.createIcon());
            if (settings().isShowLabels()) {
                stand.setCustomName(Text.component(sector.getColoredLabel()));
                stand.setCustomNameVisible(true);
            }
            Location loc = slotLocation(i, 0.0D, settings().getWheelRadius());
            stand.setPos(new Vec3d(loc.getX(), loc.getY(), loc.getZ()));
            stands.add(stand);
        }

        pointer = VirtualArmorStand.create();
        pointer.setInvisible(true);
        pointer.setSmall(true);
        pointer.setNoBasePlate(true);
        pointer.setNoGravity(true);
        pointer.setSilent(true);
        pointer.setYaw(standYaw);
        pointer.setEquipment(EquipmentSlot.HEAD, new ItemStack(settings().getPointerItem()));
        float[] pose = settings().getPointerPose();
        pointer.setHeadPose(new Vec3f(pose[0], pose[1], pose[2]));
        Location pointerLoc = getPointerLocation();
        pointer.setPos(new Vec3d(pointerLoc.getX(), pointerLoc.getY(), pointerLoc.getZ()));
    }

    public Location getPointerLocation() {
        return wheelCenter.clone()
                .add(right.clone().multiply(settings().getPointerSideOffset()))
                .add(0.0D, settings().getWheelRadius() + settings().getPointerHeight() + settings().getItemYOffset(), 0.0D);
    }

    public void applySkin(String value, String signature) {
        if (npc == null || value == null || signature == null) {
            return;
        }
        npc.setTexture(value, signature);
        npc.respawn();
        hideFromTab();
    }

    private void lookAtWheel() {
        if (npc == null) {
            return;
        }
        Location target = wheelCenter.clone()
                .add(right.clone().multiply(settings().getNpcLookSideOffset()))
                .add(0.0D, settings().getNpcLookHeightOffset(), 0.0D);
        lookTowards(target.getX(), target.getY(), target.getZ());
    }

    private void lookAtPointer() {
        if (npc == null) {
            return;
        }
        Location target = getPointerLocation();
        lookTowards(target.getX(), target.getY(), target.getZ());
    }

    private void lookTowards(double x, double y, double z) {
        if (npc == null) {
            return;
        }
        Vec3d pos = npc.getPos();
        double dx = x - pos.getX();
        double dy = y - (pos.getY() + NPC_EYE_HEIGHT);
        double dz = z - pos.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        targetYaw = yawTo(dx, dz);
        targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    private void updateRotation() {
        if (npc == null || manualRotation) {
            return;
        }
        float speed = (state == State.WALKING || state == State.RETURNING)
                ? (float) settings().getWalkTurnSpeed()
                : (float) settings().getLookTurnSpeed();
        currentYaw = approachAngle(currentYaw, targetYaw, speed);
        currentPitch = approachAngle(currentPitch, targetPitch, speed);
        npc.setYaw(currentYaw);
        npc.setPitch(currentPitch);
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float diff = wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) {
            return wrapDegrees(target);
        }
        return wrapDegrees(current + Math.copySign(maxStep, diff));
    }

    private static float wrapDegrees(float angle) {
        float result = angle % 360.0F;
        if (result >= 180.0F) {
            result -= 360.0F;
        }
        if (result < -180.0F) {
            result += 360.0F;
        }
        return result;
    }

    private static float yawTo(double dx, double dz) {
        if (Math.abs(dx) < 1.0E-6D && Math.abs(dz) < 1.0E-6D) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static double wrapRadians(double angle) {
        angle %= TWO_PI;
        if (angle < 0.0D) {
            angle += TWO_PI;
        }
        return angle;
    }

    private void tick() {
        if (!spawned || tracker == null) {
            return;
        }
        tickCounter++;
        if (state == State.IDLE && refusalTask == null) {
            updateIdleLook();
        }
        updateRotation();
        followNameHolo();
        if (tickCounter % 3 == 0) {
            int waveStep = tickCounter / 3;
            for (int i = 0; i < nameHolos.size() && i < holoTemplates.size(); i++) {
                String tpl = holoTemplates.get(i);
                if (tpl.contains(Text.HOLO_WAVE_TOKEN)) {
                    nameHolos.get(i).setCustomName(Text.component(Text.resolveHoloLine(tpl, waveStep)));
                }
            }
        }
        tracker.tick();
        hideFromTab();
    }

    private void hideFromTab() {
        if (!settings().isHideFromTab() || npc == null) {
            return;
        }
        for (Player viewer : npc.getLastViewers()) {
            npc.sendRemovePlayerPacket(viewer);
        }
    }

    private void updateIdleLook() {
        if (npc == null) {
            return;
        }
        double radius = settings().getLookRadius();
        if (radius <= 0.0D) {
            return;
        }
        World world = npcHome.getWorld();
        if (world == null) {
            return;
        }
        double radiusSq = radius * radius;
        Player nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            if (player.isDead() || !player.isValid()) {
                continue;
            }
            double distanceSq = player.getLocation().distanceSquared(npcHome);
            if (distanceSq <= radiusSq && distanceSq < nearestSq) {
                nearestSq = distanceSq;
                nearest = player;
            }
        }
        if (nearest != null) {
            Location eye = nearest.getEyeLocation();
            lookTowards(eye.getX(), eye.getY(), eye.getZ());
            lookingAtPlayer = true;
        } else if (lookingAtPlayer) {
            targetYaw = npcHome.getYaw();
            targetPitch = npcHome.getPitch();
            lookingAtPlayer = false;
        }
    }

    public void despawn() {
        if (currentPlayer != null) {
            UUID refundId = currentPlayer;
            long refundAmount = currentBet;
            currentPlayer = null;
            currentBet = 0L;
            plugin.getActiveBets().release(refundId);
            if (refundAmount > 0L && plugin.getEconomy() != null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(refundId);
                plugin.getEconomy().depositPlayer(offline, refundAmount);
            }
        }
        state = State.IDLE;
        spawned = false;
        lookingAtPlayer = false;
        tickCounter = 0;
        rotation = 0.0D;
        angularSpeed = 0.0D;

        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        if (jackpotTask != null) {
            jackpotTask.cancel();
            jackpotTask = null;
        }
        if (refusalTask != null) {
            refusalTask.cancel();
            refusalTask = null;
        }
        manualRotation = false;
        if (trackerTask != null) {
            trackerTask.cancel();
            trackerTask = null;
        }
        if (tracker != null) {
            tracker.removeAll();
            tracker = null;
        }
        stands.clear();
        slotSectors.clear();
        nameHolos.clear();
        holoTemplates.clear();
        npc = null;
        pointer = null;
    }

    private void computeAxes() {
        Vector direction = wheelCenter.toVector().subtract(npcHome.toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = npcHome.getDirection();
            direction.setY(0.0D);
            if (direction.lengthSquared() < 1.0E-6D) {
                direction = new Vector(0.0D, 0.0D, 1.0D);
            }
        }
        forward = direction.normalize();
        right = new Vector(-forward.getZ(), 0.0D, forward.getX());
        standYaw = (float) Math.toDegrees(Math.atan2(forward.getX(), -forward.getZ()));
    }

    private Location slotLocation(int slot, double rot, double radius) {
        double angle = (TWO_PI * slot / settings().getStandCount()) + rot;
        double horizontal = Math.cos(angle) * radius;
        double vertical = Math.sin(angle) * radius;
        return wheelCenter.clone().add(right.getX() * horizontal,
                vertical + settings().getItemYOffset(), right.getZ() * horizontal);
    }

    private double effectiveRadius() {
        double radius = settings().getWheelRadius();
        if (!settings().isRadiusCompensation() || angularSpeed <= 0.0D) {
            return radius;
        }
        double half = Math.min(1.0D, 1.5D * angularSpeed);
        double factor = Math.cos(half);
        if (factor < 0.5D) {
            factor = 0.5D;
        }
        return radius / factor;
    }

    private void applyRotation(double newRotation) {
        rotation = wrapRadians(newRotation);
        double radius = effectiveRadius();
        for (int slot = 0; slot < stands.size(); slot++) {
            Location loc = slotLocation(slot, rotation, radius);
            stands.get(slot).setPos(new Vec3d(loc.getX(), loc.getY(), loc.getZ()));
        }
    }

    public boolean startGame(Player player, long bet) {
        if (!spawned) {
            return false;
        }
        if (state != State.IDLE) {
            plugin.send(player, "busy");
            return false;
        }
        if (bet < settings().getMinBet()) {
            plugin.send(player, "bet-too-low", "%min%", String.valueOf(settings().getMinBet()));
            return false;
        }
        if (bet > settings().getMaxBet()) {
            plugin.send(player, "bet-too-high", "%max%", String.valueOf(settings().getMaxBet()));
            return false;
        }

        UUID uuid = player.getUniqueId();
        ru.rooyzee.elytrixcasino.bet.ActiveBet existing = plugin.getActiveBets().getActiveBet(uuid);
        if (existing != null) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", existing.getGameType().getDisplayName(),
                    "%amount%", Text.formatMoney(existing.getAmount()));
            playRefusal(player);
            return false;
        }

        Economy economy = plugin.getEconomy();
        if (!economy.has(player, bet)) {
            plugin.send(player, "no-money");
            playRefusal(player);
            return false;
        }
        if (!plugin.getActiveBets().tryAcquire(uuid, ru.rooyzee.elytrixcasino.bet.GameType.ROULETTE, id, bet)) {
            plugin.send(player, "already-has-active-bet",
                    "%game%", ru.rooyzee.elytrixcasino.bet.GameType.ROULETTE.getDisplayName(),
                    "%amount%", Text.formatMoney(bet));
            playRefusal(player);
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, bet);
        if (!response.transactionSuccess()) {
            plugin.getActiveBets().release(uuid);
            plugin.send(player, "no-money");
            playRefusal(player);
            plugin.getLogger().warning("Не удалось снять ставку у " + player.getName() + ": " + response.errorMessage);
            return false;
        }

        state = State.WALKING;
        currentPlayer = uuid;
        currentBet = bet;
        plugin.send(player, "bet-placed", "%bet%", Text.formatMoney(bet));

        Location walkTo = wheelCenter.clone()
                .subtract(forward.clone().multiply(settings().getNpcSpinDistance()))
                .add(right.clone().multiply(settings().getNpcSideOffset()));
        walkTo.setY(npcHome.getY());
        walkNpc(walkTo, LookMode.WHEEL, this::beginSpin);
        return true;
    }

    private enum LookMode {
        WHEEL,
        TRAVEL
    }

    private void walkNpc(Location to, LookMode lookMode, Runnable after) {
        if (npc == null) {
            after.run();
            return;
        }
        final Vec3d from = npc.getPos();
        final int steps = settings().getWalkTicks();
        final float travelYaw = yawTo(to.getX() - from.getX(), to.getZ() - from.getZ());
        final boolean turnFirst = lookMode == LookMode.TRAVEL;

        animationTask = new BukkitRunnable() {
            private int step = 0;
            private int turnTicks = 0;
            private boolean turning = turnFirst;

            @Override
            public void run() {
                if (npc == null || !spawned) {
                    cancel();
                    return;
                }
                if (turning) {
                    targetYaw = travelYaw;
                    targetPitch = 0.0F;
                    turnTicks++;
                    if (Math.abs(wrapDegrees(currentYaw - travelYaw)) < 8.0F
                            || turnTicks >= settings().getTurnTicks()) {
                        turning = false;
                    }
                    return;
                }
                step++;
                double progress = Math.min(1.0D, (double) step / steps);
                double eased = progress * progress * (3.0D - 2.0D * progress);
                double x = from.getX() + (to.getX() - from.getX()) * eased;
                double y = from.getY() + (to.getY() - from.getY()) * eased;
                double z = from.getZ() + (to.getZ() - from.getZ()) * eased;
                npc.setPos(new Vec3d(x, y, z));
                npc.setOnGround(true);
                followNameHolo();
                if (lookMode == LookMode.WHEEL) {
                    lookAtWheel();
                } else {
                    targetYaw = travelYaw;
                    targetPitch = 0.0F;
                }
                if (step >= steps) {
                    cancel();
                    animationTask = null;
                    after.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void beginSpin() {
        if (!spawned || npc == null) {
            return;
        }
        state = State.SPINNING;

        final int slots = stands.size();
        final double slotAngle = TWO_PI / slots;
        final int winner = pickWinnerSlot();
        final double start = wrapRadians(rotation);
        double target = wrapRadians(POINTER_ANGLE - slotAngle * winner);
        double delta = wrapRadians(target - start);
        if (delta < 1.0E-6D) {
            delta = TWO_PI;
        }

        final int duration = settings().getSpinTicks();
        final int secondSwing = Math.min(duration - 1, settings().getSecondSwingTick());
        final int decelStart = Math.max(secondSwing + 1, (int) (duration * DECELERATION_START));

        final double[] weights = new double[duration + 1];
        final double[] cumulative = new double[duration + 1];
        double sum = 0.0D;
        for (int t = 1; t <= duration; t++) {
            double speed;
            if (t < secondSwing) {
                speed = settings().getFirstStageSpeed();
            } else if (t < decelStart) {
                speed = 1.0D;
            } else {
                double left = (double) (duration - t) / (double) (duration - decelStart);
                speed = left * left;
            }
            weights[t] = speed;
            sum += speed;
            cumulative[t] = sum;
        }
        final double totalWeight = sum;

        double allowed = settings().getMaxSpinSpeed() * totalWeight;
        int turns = (int) Math.floor((allowed - delta) / TWO_PI);
        turns = Math.max(1, Math.min(settings().getSpinTurns(), turns));

        final double total = delta + TWO_PI * turns;
        final double scale = total / totalWeight;
        final double endRotation = start + total;

        npc.playAnimation(EntityAnimation.SWING_MAIN_ARM);

        animationTask = new BukkitRunnable() {
            private int tick = 0;
            private int lastSlot = slotAtPointer(start);

            @Override
            public void run() {
                if (!spawned) {
                    cancel();
                    return;
                }
                tick++;
                int index = Math.min(tick, duration);

                angularSpeed = scale * weights[index];
                applyRotation(start + total * cumulative[index] / totalWeight);

                if (tick == secondSwing && npc != null) {
                    npc.playAnimation(EntityAnimation.SWING_MAIN_ARM);
                    lookAtPointer();
                }

                if (settings().isSpinSound()) {
                    int slot = slotAtPointer(rotation);
                    if (slot != lastSlot) {
                        lastSlot = slot;
                        playNearby(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, 1.4F);
                    }
                }

                if (tick >= duration) {
                    angularSpeed = 0.0D;
                    applyRotation(endRotation);
                    Sector landed = slotSectors.get(slotAtPointer(rotation));
                    cancel();
                    animationTask = null;
                    finish(landed);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private int pickWinnerSlot() {
        if (slotSectors.isEmpty()) {
            return 0;
        }
        List<Sector> sectors = settings().getSectors();
        int totalWeight = settings().getTotalWeight();
        if (sectors.isEmpty() || totalWeight <= 0) {
            return ThreadLocalRandom.current().nextInt(slotSectors.size());
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        Sector chosen = sectors.get(sectors.size() - 1);
        for (Sector sector : sectors) {
            cumulative += sector.getWeight();
            if (roll < cumulative) {
                chosen = sector;
                break;
            }
        }
        List<Integer> matchingSlots = new ArrayList<>();
        for (int i = 0; i < slotSectors.size(); i++) {
            if (slotSectors.get(i) == chosen
                    || Double.compare(slotSectors.get(i).getMultiplier(), chosen.getMultiplier()) == 0) {
                matchingSlots.add(i);
            }
        }
        if (matchingSlots.isEmpty()) {
            return ThreadLocalRandom.current().nextInt(slotSectors.size());
        }
        return matchingSlots.get(ThreadLocalRandom.current().nextInt(matchingSlots.size()));
    }

    private int slotAtPointer(double rot) {
        int slots = stands.size();
        if (slots <= 0) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        int bestSlot = 0;
        for (int i = 0; i < slots; i++) {
            double angle = TWO_PI * i / slots + rot;
            double diff = wrapRadians(angle - POINTER_ANGLE);
            if (diff > Math.PI) {
                diff -= TWO_PI;
            }
            double abs = Math.abs(diff);
            if (abs < best) {
                best = abs;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void finish(Sector sector) {
        if (sector.getMultiplier() >= settings().getJackpotMultiplier()) {
            playJackpot(getPointerLocation().add(0.0D, -0.35D, 0.0D));
        }
        long win = Math.round(currentBet * sector.getMultiplier());
        UUID playerId = currentPlayer;
        long bet = currentBet;
        if (playerId != null) {
            plugin.getActiveBets().release(playerId);
        }

        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (win > 0L) {
            if (player != null) {
                plugin.getEconomy().depositPlayer(player, win);
                plugin.send(player, "win",
                        "%player%", player.getName(),
                        "%amount%", Text.formatMoney(win),
                        "%mult%", Text.formatMultiplier(sector.getMultiplier()),
                        "%bet%", Text.formatMoney(bet));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            } else if (playerId != null) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
                plugin.getEconomy().depositPlayer(offline, win);
                plugin.getLogger().info("Выигрыш " + win + " начислен оффлайн-игроку " + playerId);
            }
        } else if (player != null) {
            plugin.send(player, "lose", "%bet%", Text.formatMoney(bet));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        }

        currentPlayer = null;
        currentBet = 0L;
        state = State.RETURNING;
        walkNpc(npcHome.clone(), LookMode.TRAVEL, () -> {
            if (npc != null) {
                npc.setPos(new Vec3d(npcHome.getX(), npcHome.getY(), npcHome.getZ()));
                npc.setOnGround(true);
                targetYaw = npcHome.getYaw();
                targetPitch = npcHome.getPitch();
            }
            lookingAtPlayer = false;
            state = State.IDLE;
        });
    }

    public void playRefusal(Player player) {
        if (npc == null || !spawned || refusalTask != null) {
            return;
        }
        Location eye = player.getEyeLocation();
        lookTowards(eye.getX(), eye.getY(), eye.getZ());
        final float baseYaw = targetYaw;
        final float basePitch = targetPitch;
        currentYaw = baseYaw;
        currentPitch = basePitch;
        manualRotation = true;
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8F, 1.2F);

        refusalTask = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                tick++;
                if (npc == null || !spawned || tick > REFUSAL_TICKS) {
                    if (npc != null) {
                        npc.setYaw(baseYaw);
                        npc.setPitch(basePitch);
                    }
                    currentYaw = baseYaw;
                    currentPitch = basePitch;
                    targetYaw = baseYaw;
                    targetPitch = basePitch;
                    manualRotation = false;
                    refusalTask = null;
                    cancel();
                    return;
                }
                double fade = 1.0D - (double) tick / REFUSAL_TICKS;
                float offset = (float) (Math.sin(tick * Math.PI / 4.0D) * 25.0D * fade);
                npc.setYaw(baseYaw + offset);
                npc.setPitch(basePitch);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void playJackpot(Location source) {
        int count = settings().getJackpotCount();
        if (count <= 0 || tracker == null) {
            return;
        }
        final List<VirtualItem> items = new ArrayList<>();
        final List<double[]> motion = new ArrayList<>();
        final ItemStack drop = new ItemStack(settings().getJackpotItem());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            VirtualItem item = VirtualItem.create();
            item.setItem(drop);
            item.setNoGravity(true);
            item.setSilent(true);
            item.setPos(new Vec3d(source.getX(), source.getY(), source.getZ()));
            tracker.addEntity(item);
            items.add(item);
            motion.add(new double[]{
                    random.nextDouble(-0.12D, 0.12D),
                    random.nextDouble(0.15D, 0.35D),
                    random.nextDouble(-0.12D, 0.12D),
                    source.getX(), source.getY(), source.getZ()
            });
        }
        playNearby(Sound.ENTITY_ITEM_PICKUP, 0.7F, 1.6F);

        final int lifetime = settings().getJackpotTicks();
        jackpotTask = new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                tick++;
                boolean stop = !spawned || tracker == null || tick >= lifetime;
                if (stop) {
                    if (tracker != null) {
                        for (VirtualItem item : items) {
                            tracker.removeEntity(item);
                        }
                    }
                    cancel();
                    jackpotTask = null;
                    return;
                }
                for (int i = 0; i < items.size(); i++) {
                    double[] data = motion.get(i);
                    data[1] -= 0.035D;
                    data[3] += data[0];
                    data[4] += data[1];
                    data[5] += data[2];
                    data[0] *= 0.96D;
                    data[2] *= 0.96D;
                    items.get(i).setPos(new Vec3d(data[3], data[4], data[5]));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void playNearby(Sound sound, float volume, float pitch) {
        World world = wheelCenter.getWorld();
        if (world == null) {
            return;
        }
        int radius = settings().getViewRadius();
        double radiusSq = (double) radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(wheelCenter) <= radiusSq) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    public String getId() { return id; }
    public State getState() { return state; }
    public boolean isBusy() { return state != State.IDLE; }
    public boolean isSpawned() { return spawned; }
    public Location getHomeLocation() { return npcHome.clone(); }
    public Location getWheelLocation() { return wheelCenter.clone(); }
    public int getNpcEntityId() { return npc == null ? -1 : npc.getId(); }

    public Location getNpcLocation() {
        if (npc == null) {
            return npcHome.clone();
        }
        Vec3d pos = npc.getPos();
        return new Location(npcHome.getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }
}
