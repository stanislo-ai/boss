package dev.scootermc.config;

import dev.scootermc.ride.Physics;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed, validated view of config.yml.
 *
 * <p>Everything is read once into final fields so the hot ride loop never touches the YAML tree,
 * and every value is range-checked here rather than being trusted deep inside the physics.
 */
public final class ScooterConfig {

    public enum Units { KMH, MPH }

    /** Kerb weight of a KuKirin G2 Pro, used with the rider mass to scale the motor's pull. */
    public static final double SCOOTER_MASS_KG = 25.0;
    /** The rider mass the per-mode acceleration figures are quoted at. */
    private static final double REFERENCE_RIDER_KG = 80.0;

    // general
    public final String language;
    public final Material baseMaterial;

    // resource pack
    public final boolean packEnabled;
    public final String packUrl;
    public final String packSha1;
    public final boolean packRequired;
    public final boolean packPromptOnJoin;
    public final boolean packServerEnabled;
    public final int packServerPort;
    public final String packServerAddress;

    // ride
    public final double riderMassKg;
    public final double maxLoadKg;
    /**
     * Multiplier on motor acceleration and climbing ability from the load being carried.
     * 1.0 at the reference rider; a heavier rider gets less, an overloaded one much less.
     */
    public final double loadFactor;
    public final boolean kickStartEnabled;
    public final double kickStartSpeedMs;
    public final double kickImpulseMs;
    public final int kickCooldownTicks;
    public final double brakeDecel;
    public final double coastDecel;
    public final double rollingResistance;
    public final double reverseMaxMs;
    public final double throttleRamp;
    public final double steerRateDegPerTick;
    public final double steerFalloff;
    public final double keySteerDegPerTick;
    public final double visualSteerLimitDeg;
    public final double climbLimitDeg;
    public final boolean hopEnabled;
    public final double hopVelocity;
    public final int hopCooldownTicks;
    public final boolean waterStalls;
    public final double fallDamageMultiplier;
    public final double safeFallBonus;
    public final boolean dismountOnSneak;
    public final boolean crashEnabled;
    public final double crashMinSpeedMs;
    public final double crashDamage;
    public final double crashSpeedKept;
    public final boolean requireGroundToRide;

    // modes
    private final Map<DriveMode, DriveMode.Tuning> modes = new EnumMap<>(DriveMode.class);
    public final DriveMode defaultMode;

    // battery
    public final double batteryCapacityWh;
    public final boolean batteryDrain;
    public final double headlightWatts;
    public final double controllerIdleWatts;
    public final int lowBatteryPercent;

    // headlight
    public final boolean headlightEnabled;
    public final boolean headlightFakeLight;
    public final boolean headlightParticles;
    public final int headlightParticleInterval;

    // hud
    public final boolean hudActionBar;
    public final boolean hudBossBar;
    public final Units hudUnits;

    // audio
    public final boolean soundsEnabled;
    public final double motorVolume;
    public final double rollVolume;
    public final double motorMinPitch;
    public final double motorMaxPitch;

    // render
    public final double verticalOffset;
    public final float viewRange;
    public final boolean animateWheels;
    public final double transformEpsilon;

    // limits
    public final int maxPerPlayer;
    public final int maxTotal;
    public final int despawnParkedMinutes;
    public final int saveIntervalSeconds;

    public ScooterConfig(FileConfiguration c, Logger log) {
        language = c.getString("language", "en").toLowerCase(Locale.ROOT);

        Material mat = Material.matchMaterial(c.getString("item.base-material", "PAPER"));
        if (mat == null || !mat.isItem()) {
            log.warning("item.base-material '" + c.getString("item.base-material")
                    + "' is not a valid item; falling back to PAPER");
            mat = Material.PAPER;
        }
        baseMaterial = mat;

        packEnabled = c.getBoolean("resource-pack.enabled", false);
        packUrl = c.getString("resource-pack.url", "");
        packSha1 = c.getString("resource-pack.sha1", "");
        packRequired = c.getBoolean("resource-pack.required", false);
        packPromptOnJoin = c.getBoolean("resource-pack.prompt-on-join", true);
        packServerEnabled = c.getBoolean("resource-pack.built-in-server.enabled", false);
        packServerPort = (int) clamp(c.getInt("resource-pack.built-in-server.port", 8123), 1, 65535);
        packServerAddress = c.getString("resource-pack.built-in-server.public-address", "");

        riderMassKg = clamp(c.getDouble("ride.rider-mass-kg", REFERENCE_RIDER_KG), 20.0, 300.0);
        maxLoadKg = clamp(c.getDouble("ride.max-load-kg", 120.0), 40.0, 500.0);
        // The motor produces a roughly fixed force, so acceleration goes as 1/mass.
        double factor = (REFERENCE_RIDER_KG + SCOOTER_MASS_KG) / (riderMassKg + SCOOTER_MASS_KG);
        if (riderMassKg > maxLoadKg) {
            log.warning("ride.rider-mass-kg (" + riderMassKg + ") is above ride.max-load-kg ("
                    + maxLoadKg + "); the scooter will be sluggish, as an overloaded one is");
            factor *= 0.8;
        }
        loadFactor = clamp(factor, 0.35, 1.6);
        kickStartEnabled = c.getBoolean("ride.kick-start.enabled", true);
        kickStartSpeedMs = Physics.kmhToMs(clamp(c.getDouble("ride.kick-start.engage-speed-kmh", 4.0), 0.0, 20.0));
        kickImpulseMs = Physics.kmhToMs(clamp(c.getDouble("ride.kick-start.impulse-kmh", 8.0), 1.0, 25.0));
        kickCooldownTicks = (int) clamp(c.getInt("ride.kick-start.cooldown-ticks", 8), 1, 200);
        brakeDecel = clamp(c.getDouble("ride.brake-deceleration", 6.6), 0.5, 40.0);
        coastDecel = clamp(c.getDouble("ride.coast-deceleration", 0.5), 0.0, 20.0);
        rollingResistance = clamp(c.getDouble("ride.rolling-resistance", 0.35), 0.0, 10.0);
        reverseMaxMs = Physics.kmhToMs(clamp(c.getDouble("ride.reverse-max-kmh", 6.0), 0.0, 25.0));
        throttleRamp = clamp(c.getDouble("ride.throttle-ramp", 0.14), 0.01, 1.0);
        steerRateDegPerTick = clamp(c.getDouble("ride.steering.max-rate-deg-per-tick", 9.0), 0.5, 90.0);
        steerFalloff = clamp(c.getDouble("ride.steering.speed-falloff", 0.55), 0.0, 0.95);
        keySteerDegPerTick = clamp(c.getDouble("ride.steering.key-steer-deg-per-tick", 2.6), 0.0, 45.0);
        visualSteerLimitDeg = clamp(c.getDouble("ride.steering.visual-limit-deg", 32.0), 1.0, 80.0);
        climbLimitDeg = clamp(c.getDouble("ride.climb-limit-deg", 19.0), 1.0, 89.0);
        hopEnabled = c.getBoolean("ride.hop.enabled", true);
        hopVelocity = clamp(c.getDouble("ride.hop.velocity", 0.42), 0.05, 2.0);
        hopCooldownTicks = (int) clamp(c.getInt("ride.hop.cooldown-ticks", 10), 1, 200);
        waterStalls = c.getBoolean("ride.water.stalls", true);
        fallDamageMultiplier = clamp(c.getDouble("ride.fall-damage-multiplier", 0.5), 0.0, 4.0);
        safeFallBonus = clamp(c.getDouble("ride.safe-fall-bonus", 2.0), 0.0, 20.0);
        dismountOnSneak = c.getBoolean("ride.dismount-on-sneak", true);
        crashEnabled = c.getBoolean("ride.crash.enabled", true);
        crashMinSpeedMs = Physics.kmhToMs(clamp(c.getDouble("ride.crash.min-speed-kmh", 18.0), 1.0, 60.0));
        crashDamage = clamp(c.getDouble("ride.crash.damage", 2.0), 0.0, 40.0);
        crashSpeedKept = clamp(c.getDouble("ride.crash.speed-kept", 0.15), 0.0, 1.0);
        requireGroundToRide = c.getBoolean("ride.require-ground-to-mount", true);

        for (DriveMode mode : DriveMode.values()) {
            String base = "modes." + mode.configKey() + ".";
            double kmh = clamp(c.getDouble(base + "max-speed-kmh", defaultSpeed(mode)), 1.0, 200.0);
            double accel = clamp(c.getDouble(base + "acceleration", defaultAccel(mode)), 0.1, 30.0);
            double wh = clamp(c.getDouble(base + "wh-per-km", defaultWhPerKm(mode)), 0.1, 500.0);
            modes.put(mode, new DriveMode.Tuning(Physics.kmhToMs(kmh), accel, wh));
        }
        defaultMode = DriveMode.byName(c.getString("modes.default", "DRIVE"), DriveMode.DRIVE);

        batteryCapacityWh = clamp(c.getDouble("battery.capacity-wh", 748.8), 1.0, 100000.0);
        batteryDrain = c.getBoolean("battery.drain", true);
        headlightWatts = clamp(c.getDouble("battery.headlight-watts", 6.0), 0.0, 500.0);
        controllerIdleWatts = clamp(c.getDouble("battery.controller-idle-watts", 2.0), 0.0, 500.0);
        lowBatteryPercent = (int) clamp(c.getInt("battery.low-warning-percent", 15), 0, 99);

        headlightEnabled = c.getBoolean("headlight.enabled", true);
        headlightFakeLight = c.getBoolean("headlight.fake-light-block", true);
        headlightParticles = c.getBoolean("headlight.particles", true);
        headlightParticleInterval = (int) clamp(c.getInt("headlight.particle-interval-ticks", 2), 1, 40);

        hudActionBar = c.getBoolean("hud.action-bar", true);
        hudBossBar = c.getBoolean("hud.boss-bar", false);
        hudUnits = "mph".equalsIgnoreCase(c.getString("hud.units", "kmh")) ? Units.MPH : Units.KMH;

        soundsEnabled = c.getBoolean("audio.enabled", true);
        motorVolume = clamp(c.getDouble("audio.motor-volume", 0.55), 0.0, 2.0);
        rollVolume = clamp(c.getDouble("audio.roll-volume", 0.35), 0.0, 2.0);
        motorMinPitch = clamp(c.getDouble("audio.motor-min-pitch", 0.62), 0.5, 2.0);
        motorMaxPitch = clamp(c.getDouble("audio.motor-max-pitch", 1.85), 0.5, 2.0);

        verticalOffset = clamp(c.getDouble("render.vertical-offset", 0.0), -1.0, 1.0);
        viewRange = (float) clamp(c.getDouble("render.view-range", 1.4), 0.1, 10.0);
        animateWheels = c.getBoolean("render.animate-wheels", true);
        transformEpsilon = clamp(c.getDouble("render.transform-epsilon", 0.0025), 0.0, 0.1);

        maxPerPlayer = (int) clamp(c.getInt("limits.max-scooters-per-player", 5), 1, 1000);
        maxTotal = (int) clamp(c.getInt("limits.max-total-scooters", 2000), 1, 200000);
        despawnParkedMinutes = (int) clamp(c.getInt("limits.despawn-parked-after-minutes", 0), 0, 100000);
        saveIntervalSeconds = (int) clamp(c.getInt("storage.save-interval-seconds", 120), 5, 3600);

        if (motorMinPitch > motorMaxPitch) {
            log.warning("audio.motor-min-pitch is above audio.motor-max-pitch; the motor will not "
                    + "change pitch with speed");
        }
    }

    public DriveMode.Tuning tuning(DriveMode mode) {
        return modes.get(mode);
    }

    /** Fastest of the configured modes, used for HUD scaling and steering falloff. */
    public double topSpeedMs() {
        double max = 0.0;
        for (DriveMode.Tuning t : modes.values()) {
            max = Math.max(max, t.maxSpeedMs());
        }
        return max;
    }

    private static double defaultSpeed(DriveMode m) {
        return switch (m) {
            case ECO -> 20.0;
            case DRIVE -> 40.0;
            case SPORT -> 45.0;
        };
    }

    private static double defaultAccel(DriveMode m) {
        return switch (m) {
            case ECO -> 1.8;
            case DRIVE -> 2.8;
            case SPORT -> 3.6;
        };
    }

    private static double defaultWhPerKm(DriveMode m) {
        return switch (m) {
            case ECO -> 9.0;
            case DRIVE -> 12.9;   // 748.8 Wh / 58 km, the quoted range
            case SPORT -> 17.0;
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
