package dev.scootermc.model;

import java.util.Locale;
import java.util.Random;
import net.kyori.adventure.text.format.TextColor;

/** Paint colours. Must stay in step with COLORS in tools/gen_models.py. */
public enum ScooterColor {

    ORANGE(0xFF6A00),
    RED(0xE03024),
    BLUE(0x2A6FE0),
    CYAN(0x12C2C0),
    GREEN(0x37B44A),
    PURPLE(0x8E4AE8),
    YELLOW(0xF2C218),
    WHITE(0xE6E9ED),
    PINK(0xFF4FA3);

    /** The stock KuKirin G2 Pro finish. */
    public static final ScooterColor DEFAULT = ORANGE;

    private final int rgb;

    ScooterColor(int rgb) {
        this.rgb = rgb;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public TextColor textColor() {
        return TextColor.color(rgb);
    }

    public static ScooterColor byId(String id, ScooterColor fallback) {
        if (id != null) {
            for (ScooterColor c : values()) {
                if (c.name().equalsIgnoreCase(id)) {
                    return c;
                }
            }
        }
        return fallback;
    }

    public static ScooterColor random(Random random) {
        return values()[random.nextInt(values().length)];
    }
}
