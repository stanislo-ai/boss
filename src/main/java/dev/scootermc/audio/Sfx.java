package dev.scootermc.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/** Sound keys from the resource pack, plus the loop lengths the generator recorded. */
public final class Sfx {

    public static final String MOTOR = "scootermc:scooter.motor_loop";
    public static final String ROLL = "scootermc:scooter.tyre_roll";
    public static final String THROTTLE = "scootermc:scooter.throttle";
    public static final String BRAKE = "scootermc:scooter.brake";
    public static final String REGEN = "scootermc:scooter.regen";
    public static final String HORN = "scootermc:scooter.horn";
    public static final String BELL = "scootermc:scooter.bell";
    public static final String KICK = "scootermc:scooter.kick";
    public static final String CRASH = "scootermc:scooter.crash";
    public static final String POWER_ON = "scootermc:scooter.power_on";
    public static final String POWER_OFF = "scootermc:scooter.power_off";
    public static final String BEEP = "scootermc:scooter.beep";
    public static final String MODE = "scootermc:scooter.mode";
    public static final String LOW_BATTERY = "scootermc:scooter.low_battery";
    public static final String DEPLOY = "scootermc:scooter.deploy";
    public static final String PICKUP = "scootermc:scooter.pickup";
    public static final String SUSPENSION = "scootermc:scooter.suspension";
    public static final String LOCK = "scootermc:scooter.lock";

    private static final Map<String, Double> LOOPS = new HashMap<>();

    private Sfx() {
    }

    /** Reads the loop lengths written by tools/gen_sounds.py, so retriggering stays in step. */
    public static void load(Plugin plugin) {
        LOOPS.clear();
        LOOPS.put(MOTOR, 0.5);
        LOOPS.put(ROLL, 0.5);
        try (InputStream in = plugin.getResource("sounds.json")) {
            if (in == null) {
                return;
            }
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String ns = root.get("namespace").getAsString();
            JsonObject loops = root.getAsJsonObject("loops");
            for (String key : loops.keySet()) {
                LOOPS.put(ns + ":" + key, loops.get(key).getAsDouble());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read bundled sounds.json (" + e
                    + "); using default loop lengths");
        }
    }

    public static double loopSeconds(String key) {
        return LOOPS.getOrDefault(key, 0.5);
    }
}
