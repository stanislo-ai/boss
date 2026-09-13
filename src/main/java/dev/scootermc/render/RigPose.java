package dev.scootermc.render;

import org.bukkit.Location;

/**
 * Everything the renderer needs to draw a scooter for one tick.
 *
 * @param origin     world position of the scooter's design origin (ground, mid-wheelbase)
 * @param yaw        Minecraft yaw the frame points along
 * @param steerDeg   front-wheel angle; positive turns the wheel to the rider's right
 * @param rollRad    accumulated wheel rotation about the axle
 * @param lightsOn   headlight and tail light lit
 * @param dashOn     dash backlight lit
 */
public record RigPose(Location origin, double yaw, double steerDeg, double rollRad,
                      boolean lightsOn, boolean dashOn) {
}
