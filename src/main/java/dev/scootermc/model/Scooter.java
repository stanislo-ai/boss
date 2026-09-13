package dev.scootermc.model;

import dev.scootermc.config.DriveMode;
import java.util.UUID;
import org.bukkit.Location;

/**
 * A single scooter: the data that survives a restart, plus the live pose while it exists in a
 * world. Rendering lives in {@link dev.scootermc.render.ScooterRig}, riding in
 * {@link dev.scootermc.ride.RideSession}.
 */
public final class Scooter {

    private final UUID id;
    private UUID owner;
    private String ownerName;
    private ScooterColor color;
    private DriveMode mode;

    private double batteryWh;
    private double odometerMetres;
    private boolean locked;
    private boolean headlightOn;
    private long createdAt;
    private long parkedSince;

    private Location location;
    private double heading;

    private UUID rider;

    public Scooter(UUID id, UUID owner, String ownerName, ScooterColor color, DriveMode mode,
                   double batteryWh, Location location, double heading) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.color = color;
        this.mode = mode;
        this.batteryWh = batteryWh;
        this.location = location;
        this.heading = heading;
        this.createdAt = System.currentTimeMillis();
        this.parkedSince = this.createdAt;
    }

    public UUID id() {
        return id;
    }

    /** Short human-friendly handle, used in commands and messages. */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
    }

    public String ownerName() {
        return ownerName == null ? "?" : ownerName;
    }

    public ScooterColor color() {
        return color;
    }

    public void color(ScooterColor color) {
        this.color = color;
    }

    public DriveMode mode() {
        return mode;
    }

    public void mode(DriveMode mode) {
        this.mode = mode;
    }

    public double batteryWh() {
        return batteryWh;
    }

    public void batteryWh(double wh) {
        this.batteryWh = wh;
    }

    public double batteryPercent(double capacityWh) {
        if (capacityWh <= 0) {
            return 0;
        }
        return Math.max(0.0, Math.min(100.0, batteryWh / capacityWh * 100.0));
    }

    public boolean empty() {
        return batteryWh <= 1e-6;
    }

    public double odometerMetres() {
        return odometerMetres;
    }

    public void addOdometer(double metres) {
        this.odometerMetres += Math.max(0.0, metres);
    }

    public void odometerMetres(double metres) {
        this.odometerMetres = metres;
    }

    public boolean locked() {
        return locked;
    }

    public void locked(boolean locked) {
        this.locked = locked;
    }

    public boolean headlightOn() {
        return headlightOn;
    }

    public void headlightOn(boolean on) {
        this.headlightOn = on;
    }

    public long createdAt() {
        return createdAt;
    }

    public void createdAt(long at) {
        this.createdAt = at;
    }

    public long parkedSince() {
        return parkedSince;
    }

    public void touchParked() {
        this.parkedSince = System.currentTimeMillis();
    }

    public Location location() {
        return location == null ? null : location.clone();
    }

    public void location(Location location) {
        this.location = location == null ? null : location.clone();
    }

    public double heading() {
        return heading;
    }

    public void heading(double heading) {
        this.heading = heading;
    }

    public UUID rider() {
        return rider;
    }

    public void rider(UUID rider) {
        this.rider = rider;
    }

    public boolean ridden() {
        return rider != null;
    }

    public boolean isOwner(UUID who) {
        return owner != null && owner.equals(who);
    }
}
