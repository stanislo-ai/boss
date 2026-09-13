package dev.scootermc.ride;

/**
 * The scooter's physical model.
 *
 * <p>Deliberately free of Bukkit types so it can be unit tested. Every quantity is SI unless the
 * name says otherwise: speeds in m/s, accelerations in m/s^2, angles in degrees, energy in watt
 * hours. One Minecraft block is treated as one metre, which is the game's own convention and makes
 * the KuKirin numbers in the config transfer across unchanged.
 */
public final class Physics {

    /** Minecraft's gravity is 0.08 blocks/tick^2, i.e. 32 m/s^2 - not Earth's. */
    public static final double MC_GRAVITY_PER_TICK = 0.08;
    public static final double MC_DRAG_PER_TICK = 0.98;
    public static final double TERMINAL_VELOCITY = -3.92;

    /** Real gravity, used for gradient resistance so climb behaviour matches the 19 degree spec. */
    public static final double GRAVITY = 9.81;

    public static final int TICKS_PER_SECOND = 20;
    public static final double TICK_SECONDS = 1.0 / TICKS_PER_SECOND;

    private Physics() {
    }

    // ------------------------------------------------------------------ units

    public static double kmhToMs(double kmh) {
        return kmh / 3.6;
    }

    public static double msToKmh(double ms) {
        return ms * 3.6;
    }

    /** Blocks travelled per tick for a speed in m/s (1 block == 1 m). */
    public static double msToBlocksPerTick(double ms) {
        return ms * TICK_SECONDS;
    }

    // ------------------------------------------------------------------ drive

    /**
     * Quadratic drag coefficient chosen so the scooter naturally settles at {@code vMax}.
     *
     * <p>Solving {@code accel - rollingResistance - k*vMax^2 = 0} means acceleration tapers off
     * towards the top speed the way a real scooter does, instead of being clipped by a hard cap.
     */
    public static double dragK(double accel, double rollingResistance, double vMax) {
        if (vMax <= 0.01) {
            return 0.0;
        }
        return Math.max(0.0, (accel - rollingResistance) / (vMax * vMax));
    }

    /**
     * Net longitudinal acceleration from the motor, drag and rolling resistance.
     *
     * @param throttle 0..1 thumb-throttle position
     * @param v        current speed, m/s (may be negative when reversing)
     */
    public static double driveAccel(double accel, double throttle, double v, double vMax,
                                    double rollingResistance) {
        double k = dragK(accel, rollingResistance, vMax);
        double drag = k * v * Math.abs(v);
        double roll = rollingResistance * Math.signum(v);
        return accel * clamp01(throttle) - drag - roll;
    }

    /**
     * Gradient resistance. Positive {@code grade} (rise over run) pulls the scooter back.
     *
     * <p>The motor cannot out-torque a slope steeper than the machine's rated climb, so above that
     * the returned deceleration is deliberately harsher than gravity alone.
     */
    public static double slopeAccel(double grade, double climbLimitDegrees) {
        double theta = Math.atan(grade);
        double a = -GRAVITY * Math.sin(theta);
        double limit = Math.toRadians(climbLimitDegrees);
        if (theta > limit) {
            a *= 1.0 + 2.0 * (theta - limit) / Math.max(limit, 1e-3);
        }
        return a;
    }

    /**
     * How far the scooter may swing its heading in one tick.
     *
     * <p>Real scooters understeer badly with speed; {@code falloff} is the fraction of steering
     * authority lost at top speed.
     */
    public static double steerRate(double baseRatePerTick, double v, double vMax, double falloff) {
        double frac = vMax <= 0.01 ? 0.0 : clamp01(Math.abs(v) / vMax);
        return baseRatePerTick * (1.0 - clamp01(falloff) * frac);
    }

    /** Normalises an angle to (-180, 180]. */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d <= -180.0) {
            d += 360.0;
        } else if (d > 180.0) {
            d -= 360.0;
        }
        return d;
    }

    /** Shortest signed angular difference from {@code from} to {@code to}. */
    public static double angleDelta(double from, double to) {
        return wrapDegrees(to - from);
    }

    /** Moves {@code current} towards {@code target} by at most {@code maxStep} degrees. */
    public static double approachAngle(double current, double target, double maxStep) {
        double delta = angleDelta(current, target);
        if (Math.abs(delta) <= maxStep) {
            return wrapDegrees(target);
        }
        return wrapDegrees(current + Math.copySign(maxStep, delta));
    }

    /** Exponential approach used for the throttle ramp and the visual steering angle. */
    public static double approach(double current, double target, double rate) {
        return current + (target - current) * clamp01(rate);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    // ------------------------------------------------------------------ wheels

    /**
     * How far the wheels have rotated, in radians, after travelling {@code metres}.
     * Positive is forward roll about the axle's +X axis.
     */
    public static double wheelRoll(double metres, double radiusMetres) {
        if (radiusMetres <= 1e-6) {
            return 0.0;
        }
        return metres / radiusMetres;
    }

    // ------------------------------------------------------------------ energy

    /** Watt hours consumed moving {@code metres} at the given consumption rate. */
    public static double driveEnergyWh(double whPerKm, double metres) {
        return whPerKm * metres / 1000.0;
    }

    /** Watt hours consumed by a constant load over a number of ticks. */
    public static double loadEnergyWh(double watts, int ticks) {
        return watts * (ticks * TICK_SECONDS) / 3600.0;
    }

    // ------------------------------------------------------------------ gravity

    /**
     * One tick of Minecraft's own vertical integration.
     *
     * <p>The plugin owns the player's velocity while riding, so it has to reproduce vanilla
     * gravity exactly - otherwise falls either float or accelerate wrongly.
     */
    public static double gravityStep(double vy) {
        return Math.max(TERMINAL_VELOCITY, (vy - MC_GRAVITY_PER_TICK) * MC_DRAG_PER_TICK);
    }

    // ------------------------------------------------------------------ audio

    /**
     * Motor pitch for a given speed. Minecraft clamps sound pitch to 0.5..2.0, so the curve is
     * mapped into that window with a square root to keep low speeds audible and distinct.
     */
    public static double motorPitch(double v, double vMax, double minPitch, double maxPitch) {
        double frac = vMax <= 0.01 ? 0.0 : clamp01(Math.abs(v) / vMax);
        return clamp(minPitch + (maxPitch - minPitch) * Math.sqrt(frac), 0.5, 2.0);
    }

    /**
     * Ticks between re-triggers of a looping sample.
     *
     * <p>Playing a sound at pitch p also plays it p times faster, so a {@code loopSeconds} sample
     * finishes in {@code loopSeconds / p}. Retriggering a hair early leaves a small overlap rather
     * than an audible gap.
     */
    public static int loopPeriodTicks(double loopSeconds, double pitch, double overlap) {
        double seconds = loopSeconds / Math.max(pitch, 0.01) * (1.0 - clamp01(overlap));
        return Math.max(1, (int) Math.round(seconds * TICKS_PER_SECOND));
    }
}
