package dev.scootermc.ride;

import dev.scootermc.ScooterMC;
import dev.scootermc.audio.Sfx;
import dev.scootermc.audio.SoundEngine;
import dev.scootermc.config.DriveMode;
import dev.scootermc.config.ScooterConfig;
import dev.scootermc.model.Assembly;
import dev.scootermc.model.Scooter;
import dev.scootermc.render.RigPose;
import dev.scootermc.render.ScooterRig;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.util.Vector;

/**
 * One player riding one scooter.
 *
 * <h2>How the rider is moved</h2>
 * The player is never a passenger - a passenger renders in a sitting pose, which is wrong for a
 * kick scooter and would hide the deck. Instead their walk speed is set to zero so WASD stops
 * moving them client-side, their key state is read from {@link Player#getCurrentInput()}, and the
 * scooter drives them with one velocity packet per tick. Movement therefore stays
 * client-authoritative: the client keeps doing collision, step-up and slopes itself, which is what
 * makes the ride smooth.
 *
 * <p>Walk speed is set to exactly zero deliberately. The client's field-of-view multiplier is
 * {@code (movementSpeedAttribute / abilities.walkingSpeed + 1) / 2}, guarded by
 * {@code walkingSpeed != 0}. Zeroing the *attribute* instead would halve the rider's FOV; zeroing
 * the ability skips the whole term and leaves the view alone.
 *
 * <p>Vertical motion is integrated here rather than left to the client, because a velocity packet
 * overwrites all three axes. {@link Physics#gravityStep} reproduces vanilla's numbers exactly so
 * falls behave normally.
 */
public final class RideSession {

    /** Why a ride finished; some reasons are worth a sound or a message. */
    public enum EndReason { DISMOUNT, SNEAK, CRASH, BATTERY, DEATH, QUIT, TELEPORT, WORLD, SHUTDOWN, BLOCKED }

    private final ScooterMC plugin;
    private final ScooterConfig config;
    private final Assembly assembly;
    private final Player player;
    private final Scooter scooter;
    private final ScooterRig rig;
    private final SoundEngine sound;

    private double speed;              // m/s, signed
    private double heading;            // Minecraft yaw the frame points along
    private double steerVisual;        // degrees; positive turns the front wheel to the left
    private double throttleLevel;      // 0..1 thumb throttle
    private double wheelRoll;          // radians
    private double verticalVelocity;   // blocks/tick, our own copy of the client's gravity

    private Vector lastPosition;
    /** Consecutive stalled ticks before the scooter accepts that it has hit something. */
    private static final int BLOCKED_TICKS_FOR_CRASH = 3;
    /** Stops a scrape along a wall turning into a stream of crashes. */
    private static final int CRASH_COOLDOWN_TICKS = 30;

    private int blockedTicks;
    private int crashCooldown;
    private int kickCooldown;
    private int hopCooldown;
    private int airborneTicks;
    private boolean lowBatteryWarned;
    private boolean wasOnGround = true;
    private int ticks;

    private float savedWalkSpeed;
    private Location fakeLightAt;
    private EndReason pendingEnd = EndReason.DISMOUNT;

    public RideSession(ScooterMC plugin, Player player, Scooter scooter, ScooterRig rig) {
        this.plugin = plugin;
        this.config = plugin.config();
        this.assembly = plugin.assembly();
        this.player = player;
        this.scooter = scooter;
        this.rig = rig;
        this.sound = new SoundEngine(config);
        this.heading = player.getLocation().getYaw();
        this.lastPosition = player.getLocation().toVector();
    }

    public Player player() {
        return player;
    }

    public Scooter scooter() {
        return scooter;
    }

    public ScooterRig rig() {
        return rig;
    }

    public double speedMs() {
        return speed;
    }

    public double throttle() {
        return throttleLevel;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        savedWalkSpeed = player.getWalkSpeed();
        player.setWalkSpeed(0f);
        applyFallProtection(true);
        scooter.rider(player.getUniqueId());
        sound.start(player.getLocation());
        // A real controller lights the dash the moment it wakes up.
        pushPose();
    }

    public void end(EndReason reason) {
        applyFallProtection(false);
        clearFakeLight();
        try {
            player.setWalkSpeed(savedWalkSpeed <= 0f ? 0.2f : savedWalkSpeed);
        } catch (IllegalArgumentException ignored) {
            player.setWalkSpeed(0.2f);
        }
        if (reason != EndReason.SHUTDOWN && player.isOnline()) {
            sound.stop(player.getLocation());
        }
        scooter.rider(null);
    }

    // ------------------------------------------------------------------ per-tick

    /**
     * Advances the ride by one tick.
     *
     * <p>{@code Player#isOnGround} is deprecated because it is client-reported, but that is
     * precisely the value wanted here: the client owns this player's movement, so its own idea of
     * standing on something is what the physics has to agree with.
     *
     * @return false when the ride should end, for the reason given by {@link #endReason()}
     */
    @SuppressWarnings("deprecation")
    public boolean tick() {
        ticks++;
        if (!player.isOnline() || player.isDead()) {
            pendingEnd = EndReason.DEATH;
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.isFlying()
                || player.isInsideVehicle() || player.isGliding()) {
            pendingEnd = EndReason.DISMOUNT;
            return false;
        }
        if (rig.stale()) {
            pendingEnd = EndReason.BLOCKED;
            return false;
        }

        Input in = player.getCurrentInput();
        if (config.dismountOnSneak && in.isSneak()) {
            pendingEnd = EndReason.SNEAK;
            return false;
        }

        Location loc = player.getLocation();
        Vector position = loc.toVector();
        boolean onGround = player.isOnGround();

        // ---- what actually happened last tick -------------------------------------------------
        Vector delta = position.clone().subtract(lastPosition);
        double travelled = Math.hypot(delta.getX(), delta.getZ());
        double expected = Math.abs(Physics.msToBlocksPerTick(speed));
        double grade = travelled > 1e-4 ? delta.getY() / travelled : 0.0;
        grade = Physics.clamp(grade, -2.0, 2.0);

        // An obstruction shows up as the client moving us less far than we asked. A single short
        // tick is not evidence of one though: movement packets arrive on the client's schedule,
        // not the server's, so under jitter one tick can look stalled while the next catches up.
        // Requiring several in a row means only something that is really in the way registers.
        boolean crashed = false;
        boolean shortTick = ticks > 2 && expected > 0.08 && travelled < expected * 0.45;
        blockedTicks = shortTick ? blockedTicks + 1 : 0;
        if (crashCooldown > 0) {
            crashCooldown--;
        }
        if (blockedTicks >= BLOCKED_TICKS_FOR_CRASH) {
            if (config.crashEnabled && Math.abs(speed) >= config.crashMinSpeedMs
                    && crashCooldown == 0) {
                crashed = true;
                crashCooldown = CRASH_COOLDOWN_TICKS;
            }
            speed = Math.copySign(travelled * Physics.TICKS_PER_SECOND, speed);
            blockedTicks = 0;
        }
        lastPosition = position;

        // ---- steering -------------------------------------------------------------------------
        double target = loc.getYaw();
        if (in.isLeft()) {
            target -= config.keySteerDegPerTick;      // Minecraft yaw decreases turning left
        }
        if (in.isRight()) {
            target += config.keySteerDegPerTick;
        }
        double previousHeading = heading;
        double rate = Physics.steerRate(config.steerRateDegPerTick, speed, activeTuning().maxSpeedMs(),
                config.steerFalloff);
        heading = Physics.approachAngle(heading, target, rate);
        double headingRate = Physics.angleDelta(previousHeading, heading);
        // Model +X points east at yaw 0, which is the rider's left, so the visual angle is negated.
        double wantedSteer = Physics.clamp(-headingRate * 3.4,
                -config.visualSteerLimitDeg, config.visualSteerLimitDeg);
        steerVisual = Physics.approach(steerVisual, wantedSteer, 0.35);

        // ---- longitudinal ---------------------------------------------------------------------
        DriveMode.Tuning tune = activeTuning();
        boolean batteryDead = scooter.empty() && config.batteryDrain;
        boolean wantThrottle = in.isForward() && !batteryDead;
        boolean braking = in.isBackward();

        boolean lockedOut = config.kickStartEnabled && Math.abs(speed) < config.kickStartSpeedMs;
        double throttleTarget = (wantThrottle && !lockedOut && !braking) ? 1.0 : 0.0;
        throttleLevel = Physics.approach(throttleLevel, throttleTarget, config.throttleRamp);

        double accel;
        if (braking) {
            if (speed > 0.05) {
                accel = -config.brakeDecel;
            } else {
                // Walk-assist reverse, deliberately slow.
                accel = speed > -config.reverseMaxMs ? -config.brakeDecel * 0.35 : 0.0;
            }
        } else {
            // The motor's force is fixed, so a heavier load accelerates more slowly.
            accel = Physics.driveAccel(tune.accel() * config.loadFactor, throttleLevel, speed,
                    tune.maxSpeedMs(), config.rollingResistance);
            if (throttleLevel < 0.02 && Math.abs(speed) > 0.01) {
                accel -= config.coastDecel * Math.signum(speed);
            }
        }
        if (onGround) {
            accel += Physics.slopeAccel(grade,
                    config.climbLimitDeg * Math.min(1.0, config.loadFactor));
        }
        speed += accel * Physics.TICK_SECONDS;
        speed = Physics.clamp(speed, -config.reverseMaxMs, tune.maxSpeedMs() * 1.02);
        if (!wantThrottle && !braking && Math.abs(speed) < 0.06) {
            speed = 0.0;
        }

        // ---- water ----------------------------------------------------------------------------
        if (config.waterStalls && isSubmerged(loc)) {
            speed *= 0.55;
            throttleLevel = 0.0;
            if (ticks % 20 == 0) {
                sound.toRider(player, Sfx.LOW_BATTERY, 0.5f, 1.6f);
                plugin.lang().send(player, "ride.water");
            }
        }

        // ---- jump: kick off, or hop a kerb ----------------------------------------------------
        boolean hopping = false;
        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (hopCooldown > 0) {
            hopCooldown--;
        }
        if (in.isJump() && onGround) {
            if (config.kickStartEnabled && Math.abs(speed) < config.kickStartSpeedMs) {
                if (kickCooldown == 0) {
                    speed += config.kickImpulseMs;
                    kickCooldown = config.kickCooldownTicks;
                    sound.oneShot(loc, Sfx.KICK, 0.7f, 0.95f + (float) Math.random() * 0.1f);
                }
            } else if (config.hopEnabled && hopCooldown == 0) {
                hopping = true;
                hopCooldown = config.hopCooldownTicks;
                sound.oneShot(loc, Sfx.SUSPENSION, 0.5f, 1.25f);
            }
        }

        // ---- vertical --------------------------------------------------------------------------
        if (hopping) {
            verticalVelocity = config.hopVelocity;
        } else if (onGround && verticalVelocity <= 0.0) {
            // A small downward bias keeps the wheels glued to slopes and steps.
            verticalVelocity = -0.0784;
        } else {
            verticalVelocity = Physics.gravityStep(verticalVelocity);
        }

        if (onGround) {
            if (!wasOnGround && airborneTicks > 4) {
                sound.oneShot(loc, Sfx.SUSPENSION, 0.55f, 0.95f);
            }
            airborneTicks = 0;
        } else {
            airborneTicks++;
        }
        wasOnGround = onGround;

        // ---- push the rider ---------------------------------------------------------------------
        double blocksPerTick = Physics.msToBlocksPerTick(speed);
        double rad = Math.toRadians(heading);
        player.setVelocity(new Vector(-Math.sin(rad) * blocksPerTick,
                verticalVelocity,
                Math.cos(rad) * blocksPerTick));

        // ---- battery, odometer -------------------------------------------------------------------
        double metres = Math.abs(travelled);
        scooter.addOdometer(metres);
        if (config.batteryDrain) {
            double used = Physics.driveEnergyWh(tune.whPerKm(), metres) * throttleLevel
                    + Physics.loadEnergyWh(config.controllerIdleWatts, 1)
                    + (scooter.headlightOn() ? Physics.loadEnergyWh(config.headlightWatts, 1) : 0.0);
            scooter.batteryWh(Math.max(0.0, scooter.batteryWh() - used));
            double percent = scooter.batteryPercent(config.batteryCapacityWh);
            if (!lowBatteryWarned && percent <= config.lowBatteryPercent) {
                lowBatteryWarned = true;
                sound.toRider(player, Sfx.LOW_BATTERY, 0.7f, 1.0f);
                plugin.lang().send(player, "ride.low-battery",
                        "percent", String.format("%.0f", percent));
            } else if (lowBatteryWarned && percent > config.lowBatteryPercent + 2) {
                lowBatteryWarned = false;
            }
            if (scooter.empty() && Math.abs(speed) < 0.2) {
                pendingEnd = EndReason.BATTERY;
                return false;
            }
        }

        // ---- crash -------------------------------------------------------------------------------
        if (crashed) {
            sound.oneShot(loc, Sfx.CRASH, 0.9f, 0.95f + (float) Math.random() * 0.12f);
            if (config.crashDamage > 0) {
                player.damage(config.crashDamage);
            }
            speed *= config.crashSpeedKept;
            throttleLevel = 0.0;
            plugin.lang().send(player, "ride.crash");
        }

        // ---- output --------------------------------------------------------------------------
        wheelRoll += Physics.wheelRoll(Physics.msToBlocksPerTick(speed), assembly.tyreRadius());
        scooter.location(loc);
        scooter.heading(heading);
        pushPose();
        sound.tick(loc, speed, tune.maxSpeedMs(), throttleLevel, braking, onGround);
        updateFakeLight(loc);
        emitHeadlightBeam(loc);
        plugin.hud().show(this);
        return true;
    }

    private void pushPose() {
        rig.update(new RigPose(player.getLocation(), heading, steerVisual, wheelRoll,
                scooter.headlightOn(), true), player);
    }

    private DriveMode.Tuning activeTuning() {
        return config.tuning(scooter.mode());
    }

    public EndReason endReason() {
        return pendingEnd;
    }

    // ------------------------------------------------------------------ helpers

    private boolean isSubmerged(Location loc) {
        Material at = loc.getBlock().getType();
        return at == Material.WATER || at == Material.LAVA || player.isSwimming();
    }

    /**
     * A client-side-only light block in front of the scooter. The world is never touched, so the
     * headlight cannot grief terrain or leave light blocks behind if the server stops abruptly.
     */
    private void updateFakeLight(Location loc) {
        if (!config.headlightEnabled || !config.headlightFakeLight) {
            return;
        }
        if (!scooter.headlightOn()) {
            clearFakeLight();
            return;
        }
        double rad = Math.toRadians(heading);
        Location want = loc.clone().add(-Math.sin(rad) * 1.5, 0.9, Math.cos(rad) * 1.5);
        Block block = want.getBlock();
        if (!block.getType().isAir()) {
            block = loc.clone().add(0, 1.0, 0).getBlock();
            if (!block.getType().isAir()) {
                clearFakeLight();
                return;
            }
        }
        Location target = block.getLocation();
        if (fakeLightAt != null && fakeLightAt.getWorld() == target.getWorld()
                && fakeLightAt.getBlockX() == target.getBlockX()
                && fakeLightAt.getBlockY() == target.getBlockY()
                && fakeLightAt.getBlockZ() == target.getBlockZ()) {
            return;
        }
        clearFakeLight();
        BlockData light = plugin.litBlockData();
        if (light == null) {
            return;
        }
        for (Player viewer : nearby(target)) {
            viewer.sendBlockChange(target, light);
        }
        fakeLightAt = target;
    }

    /** A short warm beam thrown forward from the headlight, purely cosmetic. */
    private void emitHeadlightBeam(Location loc) {
        if (!config.headlightEnabled || !config.headlightParticles || !scooter.headlightOn()) {
            return;
        }
        if (ticks % config.headlightParticleInterval != 0 || loc.getWorld() == null) {
            return;
        }
        double rad = Math.toRadians(heading);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        for (double d = 1.1; d <= 2.6; d += 0.75) {
            Location at = loc.clone().add(dx * d, 0.85 - d * 0.12, dz * d);
            loc.getWorld().spawnParticle(Particle.DUST, at, 1, 0.06 * d, 0.05, 0.06 * d, 0.0,
                    new Particle.DustOptions(Color.fromRGB(255, 238, 198), 0.7f));
        }
    }

    public void clearFakeLight() {
        if (fakeLightAt == null) {
            return;
        }
        Location at = fakeLightAt;
        fakeLightAt = null;
        if (at.getWorld() == null || !at.isWorldLoaded()) {
            return;
        }
        BlockData real = at.getBlock().getBlockData();
        for (Player viewer : nearby(at)) {
            viewer.sendBlockChange(at, real);
        }
    }

    private Iterable<Player> nearby(Location at) {
        return at.getWorld().getNearbyPlayers(at, 64);
    }

    /** Suspension takes the sting out of a drop; both attributes are removed on dismount. */
    private void applyFallProtection(boolean on) {
        setModifier(Attribute.SAFE_FALL_DISTANCE, dev.scootermc.util.Keys.safeFall(),
                config.safeFallBonus, AttributeModifier.Operation.ADD_NUMBER, on);
        setModifier(Attribute.FALL_DAMAGE_MULTIPLIER, dev.scootermc.util.Keys.fallDamage(),
                config.fallDamageMultiplier - 1.0, AttributeModifier.Operation.ADD_SCALAR, on);
    }

    private void setModifier(Attribute attribute, org.bukkit.NamespacedKey key, double amount,
                             AttributeModifier.Operation op, boolean add) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .forEach(instance::removeModifier);
        if (add && Math.abs(amount) > 1e-9) {
            instance.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        }
    }

    /** Defensive cleanup for a player who somehow kept our modifiers across a restart. */
    public static void scrub(Player player) {
        for (Attribute attribute : new Attribute[]{Attribute.SAFE_FALL_DISTANCE,
                Attribute.FALL_DAMAGE_MULTIPLIER}) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.getModifiers().stream()
                    .filter(m -> m.getKey().getNamespace()
                            .equals(dev.scootermc.util.Keys.safeFall().getNamespace()))
                    .toList()
                    .forEach(instance::removeModifier);
        }
        if (player.getWalkSpeed() <= 0.0001f) {
            player.setWalkSpeed(0.2f);
        }
    }

    public SoundEngine sound() {
        return sound;
    }
}
