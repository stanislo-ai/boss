package dev.scootermc.config;

import java.util.Locale;

/**
 * The three speed modes of a KuKirin G2 Pro: 20 / 40 / 45 km/h.
 *
 * <p>The tuning per mode is loaded from config.yml; the enum only fixes the order and identity.
 */
public enum DriveMode {
    ECO,
    DRIVE,
    SPORT;

    /** Per-mode tuning, in SI units, resolved from the config at load time. */
    public record Tuning(double maxSpeedMs, double accel, double whPerKm) {
    }

    public DriveMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public DriveMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** Steps up or down the modes without wrapping, for the hotbar-scroll gear selector. */
    public DriveMode shift(int direction) {
        int i = Math.max(0, Math.min(values().length - 1, ordinal() + Integer.signum(direction)));
        return values()[i];
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DriveMode byName(String name, DriveMode fallback) {
        if (name == null) {
            return fallback;
        }
        for (DriveMode m : values()) {
            if (m.name().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return fallback;
    }
}
