package dev.scootermc.audio;

import dev.scootermc.config.ScooterConfig;
import dev.scootermc.ride.Physics;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Keeps a scooter's engine note going.
 *
 * <p>Minecraft has no way to loop a sample at a pitch that changes over time, so the motor and tyre
 * loops are re-triggered every time the previous play would have run out. Because pitch also scales
 * playback rate, the interval has to be divided by the pitch - a 0.5 s sample at pitch 1.8 is gone
 * in 0.28 s. Each retrigger is fired a fraction early, so consecutive plays overlap slightly
 * instead of leaving an audible hole.
 */
public final class SoundEngine {

    private static final double OVERLAP = 0.06;

    private final ScooterConfig config;

    private int motorCountdown;
    private int rollCountdown;
    private int brakeCountdown;
    private boolean running;

    public SoundEngine(ScooterConfig config) {
        this.config = config;
    }

    public void start(Location at) {
        running = true;
        motorCountdown = 0;
        rollCountdown = 0;
        brakeCountdown = 0;
        oneShot(at, Sfx.POWER_ON, 0.8f, 1.0f);
    }

    public void stop(Location at) {
        if (running) {
            oneShot(at, Sfx.POWER_OFF, 0.8f, 1.0f);
        }
        running = false;
    }

    /**
     * Drives the continuous layers for one tick.
     *
     * @param speed     current speed in m/s
     * @param topSpeed  the active mode's top speed, for pitch scaling
     * @param throttle  0..1, adds motor load to the volume
     * @param braking   true while the brake is applied
     * @param onGround  tyre noise only exists when the wheels are touching something
     */
    public void tick(Location at, double speed, double topSpeed, double throttle,
                     boolean braking, boolean onGround) {
        if (!config.soundsEnabled || !running || at.getWorld() == null) {
            return;
        }
        double absSpeed = Math.abs(speed);

        if (--motorCountdown <= 0) {
            double pitch = Physics.motorPitch(absSpeed, topSpeed,
                    config.motorMinPitch, config.motorMaxPitch);
            // Idle hum below walking pace, then load-dependent from there.
            double load = 0.25 + 0.75 * Physics.clamp01(throttle);
            double speedFrac = topSpeed <= 0.01 ? 0 : Physics.clamp01(absSpeed / topSpeed);
            double volume = config.motorVolume * (0.30 + 0.70 * speedFrac) * load;
            if (volume > 0.01) {
                play(at, Sfx.MOTOR, (float) volume, (float) pitch);
            }
            motorCountdown = Physics.loopPeriodTicks(Sfx.loopSeconds(Sfx.MOTOR), pitch, OVERLAP);
        }

        if (--rollCountdown <= 0) {
            if (onGround && absSpeed > 0.6) {
                double pitch = Physics.clamp(0.7 + absSpeed / Math.max(topSpeed, 1.0) * 0.9, 0.5, 2.0);
                double volume = config.rollVolume
                        * Physics.clamp01(absSpeed / Math.max(topSpeed, 1.0) + 0.15);
                play(at, Sfx.ROLL, (float) volume, (float) pitch);
                rollCountdown = Physics.loopPeriodTicks(Sfx.loopSeconds(Sfx.ROLL), pitch, OVERLAP);
            } else {
                rollCountdown = 4;
            }
        }

        if (braking && absSpeed > 1.2) {
            if (--brakeCountdown <= 0) {
                float pitch = (float) Physics.clamp(0.85 + absSpeed * 0.03, 0.5, 1.6);
                play(at, Sfx.BRAKE, 0.5f, pitch);
                play(at, Sfx.REGEN, 0.28f, pitch);
                brakeCountdown = 14;
            }
        } else {
            brakeCountdown = 0;
        }
    }

    /** A one-off effect, audible to everyone nearby. */
    public void oneShot(Location at, String key, float volume, float pitch) {
        if (config.soundsEnabled) {
            play(at, key, volume, pitch);
        }
    }

    /** A one-off effect only the rider hears - dash beeps and warnings. */
    public void toRider(Player player, String key, float volume, float pitch) {
        if (config.soundsEnabled) {
            player.playSound(player.getLocation(), key, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private void play(Location at, String key, float volume, float pitch) {
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, key, SoundCategory.NEUTRAL, volume, pitch);
        }
    }
}
