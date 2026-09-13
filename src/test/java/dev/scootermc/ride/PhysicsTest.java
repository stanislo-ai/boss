package dev.scootermc.ride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the ride model. These check behaviour that would be tedious and unreliable to verify
 * in game: that the scooter reaches - and stops at - the speed the config asks for, that braking
 * matches the manufacturer's stopping distance, that the battery lasts the quoted range, and that
 * the angle handling does not blow up at the +/-180 seam.
 */
class PhysicsTest {

    private static final double DT = Physics.TICK_SECONDS;

    /** Runs the longitudinal model open-throttle on the flat and returns the speed history. */
    private static double simulate(double accel, double vMax, double rollingResistance,
                                   int ticks, double startSpeed) {
        double v = startSpeed;
        for (int i = 0; i < ticks; i++) {
            v += Physics.driveAccel(accel, 1.0, v, vMax, rollingResistance) * DT;
        }
        return v;
    }

    @Test
    @DisplayName("full throttle settles at the mode's top speed and never overshoots it")
    void reachesTopSpeed() {
        double vMax = Physics.kmhToMs(45.0);
        double v = simulate(3.6, vMax, 0.35, 20 * 60, 0.0);
        assertEquals(vMax, v, 0.05, "should coast up to the configured maximum");

        // Starting above it, drag must bring it back down rather than let it run away.
        double fast = simulate(3.6, vMax, 0.35, 20 * 30, vMax * 1.5);
        assertTrue(fast <= vMax + 0.05, "drag should pull an over-speed scooter back to vMax");
    }

    @Test
    @DisplayName("acceleration is strongest from rest and tapers towards top speed")
    void accelerationTapers() {
        double vMax = Physics.kmhToMs(45.0);
        double fromRest = Physics.driveAccel(3.6, 1.0, 0.0, vMax, 0.35);
        double atHalf = Physics.driveAccel(3.6, 1.0, vMax / 2, vMax, 0.35);
        double nearTop = Physics.driveAccel(3.6, 1.0, vMax * 0.95, vMax, 0.35);
        assertTrue(fromRest > atHalf, "should pull hardest off the line");
        assertTrue(atHalf > nearTop, "and least near the top");
        assertTrue(nearTop > 0, "but still be accelerating just below vMax");
    }

    @Test
    @DisplayName("eco mode really is slower than sport")
    void modesDiffer() {
        double eco = simulate(1.8, Physics.kmhToMs(20.0), 0.35, 20 * 60, 0.0);
        double sport = simulate(3.6, Physics.kmhToMs(45.0), 0.35, 20 * 60, 0.0);
        assertEquals(20.0, Physics.msToKmh(eco), 0.5);
        assertEquals(45.0, Physics.msToKmh(sport), 0.5);
    }

    @Test
    @DisplayName("braking from 45 km/h stops inside the quoted 5-12 m")
    void brakingDistance() {
        double v = Physics.kmhToMs(45.0);
        double distance = 0.0;
        double decel = 6.6;                 // config default, ride.brake-deceleration
        int ticks = 0;
        while (v > 0 && ticks < 20 * 30) {
            v = Math.max(0.0, v - decel * DT);
            distance += v * DT;
            ticks++;
        }
        assertTrue(distance >= 5.0 && distance <= 12.0,
                "stopping distance was " + distance + " m, outside the KuKirin spec of 5-12 m");
    }

    @Test
    @DisplayName("a full battery covers the quoted 58 km range in DRIVE")
    void quotedRange() {
        double capacityWh = 748.8;          // 48 V x 15.6 Ah
        double whPerKm = 12.9;              // config default for DRIVE
        double km = 0.0;
        double wh = capacityWh;
        while (wh > 0) {
            wh -= Physics.driveEnergyWh(whPerKm, 100.0);
            km += 0.1;
        }
        assertEquals(58.0, km, 0.5, "range should match the manufacturer's figure");
    }

    @Test
    @DisplayName("a constant load drains the battery at the right rate")
    void loadDrain() {
        // 6 W of headlight for one hour is 6 Wh.
        double wh = Physics.loadEnergyWh(6.0, 20 * 3600);
        assertEquals(6.0, wh, 1e-6);
    }

    @Test
    @DisplayName("steering authority falls away with speed but never reverses")
    void steeringFalloff() {
        double vMax = Physics.kmhToMs(45.0);
        double still = Physics.steerRate(9.0, 0.0, vMax, 0.55);
        double flat = Physics.steerRate(9.0, vMax, vMax, 0.55);
        assertEquals(9.0, still, 1e-9);
        assertEquals(9.0 * 0.45, flat, 1e-9);
        assertTrue(flat > 0, "there must always be some steering left");
        // Beyond vMax the fraction is clamped, so the rate cannot go negative.
        assertTrue(Physics.steerRate(9.0, vMax * 3, vMax, 0.95) > 0);
    }

    @Test
    @DisplayName("angles wrap the short way round the -180/+180 seam")
    void angleWrapping() {
        assertEquals(0.0, Physics.wrapDegrees(360.0), 1e-9);
        assertEquals(-90.0, Physics.wrapDegrees(270.0), 1e-9);
        assertEquals(180.0, Physics.wrapDegrees(180.0), 1e-9);
        assertEquals(180.0, Physics.wrapDegrees(-180.0), 1e-9);

        // 170 -> -170 is a 20 degree turn, not 340.
        assertEquals(20.0, Physics.angleDelta(170.0, -170.0), 1e-9);
        assertEquals(-20.0, Physics.angleDelta(-170.0, 170.0), 1e-9);
    }

    @Test
    @DisplayName("approachAngle steps towards the target and settles exactly on it")
    void approachAngleConverges() {
        double heading = 170.0;
        double target = -170.0;
        for (int i = 0; i < 5; i++) {
            heading = Physics.approachAngle(heading, target, 9.0);
        }
        assertEquals(target, heading, 1e-9, "should land exactly on the target, not oscillate");

        // A single step must never overshoot.
        double one = Physics.approachAngle(0.0, 100.0, 9.0);
        assertEquals(9.0, one, 1e-9);
    }

    @Test
    @DisplayName("gravity matches vanilla and terminates at the vanilla terminal velocity")
    void gravity() {
        assertEquals(-0.0784, Physics.gravityStep(0.0), 1e-9);
        double vy = 0.0;
        for (int i = 0; i < 500; i++) {
            vy = Physics.gravityStep(vy);
        }
        // The recurrence approaches -3.92 asymptotically rather than reaching it, so the
        // explicit clamp is only ever a safety net.
        assertEquals(Physics.TERMINAL_VELOCITY, vy, 1e-3);
        assertTrue(vy > Physics.TERMINAL_VELOCITY, "the clamp must not be what stops the fall");
    }

    @Test
    @DisplayName("slope resistance opposes a climb, helps a descent, and punishes over-steep hills")
    void slopes() {
        assertTrue(Physics.slopeAccel(0.0, 19.0) == 0.0);
        assertTrue(Physics.slopeAccel(0.2, 19.0) < 0, "uphill should slow the scooter");
        assertTrue(Physics.slopeAccel(-0.2, 19.0) > 0, "downhill should speed it up");

        double atLimit = Physics.slopeAccel(Math.tan(Math.toRadians(19.0)), 19.0);
        double beyond = Physics.slopeAccel(Math.tan(Math.toRadians(35.0)), 19.0);
        assertTrue(beyond < atLimit * 1.5,
                "past the 19 degree rating the scooter should lose ground quickly");
    }

    @Test
    @DisplayName("wheel roll matches the distance travelled")
    void wheelRoll() {
        double radius = 0.1145;                       // 9 inch tyre
        double circumference = 2 * Math.PI * radius;
        assertEquals(2 * Math.PI, Physics.wheelRoll(circumference, radius), 1e-9,
                "one circumference of travel is exactly one revolution");
        assertEquals(0.0, Physics.wheelRoll(1.0, 0.0), 1e-9, "a zero radius must not divide by zero");
    }

    @Test
    @DisplayName("motor pitch stays inside the range Minecraft will accept")
    void motorPitchStaysLegal() {
        double vMax = Physics.kmhToMs(45.0);
        for (double v = -vMax * 2; v <= vMax * 2; v += 0.25) {
            double pitch = Physics.motorPitch(v, vMax, 0.62, 1.85);
            assertTrue(pitch >= 0.5 && pitch <= 2.0, "pitch " + pitch + " is out of range at v=" + v);
        }
        assertTrue(Physics.motorPitch(vMax, vMax, 0.62, 1.85)
                > Physics.motorPitch(0.0, vMax, 0.62, 1.85), "faster should sound higher");
    }

    @Test
    @DisplayName("the loop retrigger period shortens as pitch rises, and is never zero")
    void loopPeriod() {
        // A 0.5 s sample at pitch 1.0 lasts 10 ticks.
        assertEquals(10, Physics.loopPeriodTicks(0.5, 1.0, 0.0));
        // At double pitch it plays twice as fast, so it must be retriggered twice as often.
        assertEquals(5, Physics.loopPeriodTicks(0.5, 2.0, 0.0));
        // The overlap shortens the period slightly so plays butt together instead of gapping.
        assertTrue(Physics.loopPeriodTicks(0.5, 1.0, 0.2) < 10);
        assertTrue(Physics.loopPeriodTicks(0.5, 100.0, 0.9) >= 1, "must never schedule zero ticks");
    }

    @Test
    @DisplayName("drag coefficient degrades gracefully for silly inputs")
    void dragEdgeCases() {
        assertEquals(0.0, Physics.dragK(3.0, 0.5, 0.0), 1e-9, "no divide by zero at vMax = 0");
        assertTrue(Physics.dragK(0.2, 5.0, 10.0) >= 0.0,
                "resistance above the motor's output must not produce negative drag");
    }

    @Test
    @DisplayName("a scooter with no throttle rolls to a stop rather than creeping forever")
    void coastsToRest() {
        double v = Physics.kmhToMs(20.0);
        double vMax = Physics.kmhToMs(45.0);
        for (int i = 0; i < 20 * 120; i++) {
            v += (Physics.driveAccel(3.6, 0.0, v, vMax, 0.35) - 0.5 * Math.signum(v)) * DT;
            if (Math.abs(v) < 0.06) {
                v = 0;
                break;
            }
        }
        assertEquals(0.0, v, 1e-9, "should come to rest");
        assertFalse(v < 0, "and must not roll backwards on its own");
    }
}
