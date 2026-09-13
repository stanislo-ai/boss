package dev.scootermc.render;

import dev.scootermc.config.ScooterConfig;
import dev.scootermc.item.ScooterItems;
import dev.scootermc.model.Assembly;
import dev.scootermc.model.Scooter;
import dev.scootermc.util.Keys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws one scooter out of Display entities.
 *
 * <h2>Two rendering strategies</h2>
 * <ul>
 *   <li><b>Parked</b> - a single Display carrying the combined model, plus an Interaction entity to
 *       click on. Cheap, and a parked scooter has nothing to animate.</li>
 *   <li><b>Ridden</b> - the articulated rig: body, fork, bars, dash, both lamps and two wheels, so
 *       the wheels spin and the fork and bars steer.</li>
 * </ul>
 *
 * <h2>Why the ridden rig is mounted on the player</h2>
 * A Display that is teleported by the server lags the rider by the interpolation window, which at
 * 45 km/h is most of a block - the scooter would visibly trail behind the rider's own client-side
 * position. Mounting the displays on the player instead makes the client position them from its own
 * predicted player position, so there is no lag at all.
 *
 * <p>Vanilla only lets an entity carry one passenger - {@code Entity.canAddPassenger} returns
 * {@code passengers.isEmpty()}, and {@code Player} does not override it - but Bukkit's
 * {@code addPassenger} calls {@code startRiding(vehicle, true)}, and that force flag skips the
 * check. So all eight parts ride the player directly; every passenger resolves to the same
 * attachment point and positions itself with its own transformation.
 *
 * <p>If direct mounting ever stops working the rig chains the parts off each other instead
 * (player -> body -> fork -> ...), and if even that fails it falls back to teleporting them, which
 * looks slightly delayed but always works. {@code /scooter selftest} reports which is in use.
 *
 * <p>The transform maths matches tools/preview.py, which is checked against the combined model by
 * {@code python3 tools/preview.py --check}.
 */
public final class ScooterRig {

    /** How the ridden rig follows the player. */
    public enum Mode { MOUNTED, TELEPORT }

    private final Assembly assembly;
    private final ScooterConfig config;
    private final ScooterItems items;
    private final Scooter scooter;

    private final Map<String, ItemDisplay> parts = new LinkedHashMap<>();
    private final Map<String, Transformation> lastSent = new LinkedHashMap<>();
    private ItemDisplay parked;
    private Interaction hitbox;
    private final List<Entity> chain = new ArrayList<>();

    private Mode mode = Mode.MOUNTED;
    private boolean chained;
    private boolean ridden;
    private double attachmentY = 1.35;      // refined from the live entity every tick
    private final double standOffsetZ;

    public ScooterRig(Assembly assembly, ScooterConfig config, ScooterItems items, Scooter scooter) {
        this.assembly = assembly;
        this.config = config;
        this.items = items;
        this.scooter = scooter;
        // The rider stands in the middle of the deck, so the design origin sits a few centimetres
        // ahead of their feet rather than exactly under them.
        this.standOffsetZ = assembly.deckCentreZ();
    }

    public boolean isRidden() {
        return ridden;
    }

    public Mode mode() {
        return mode;
    }

    /** True when the parts had to be hung off each other instead of straight onto the rider. */
    public boolean chained() {
        return chained;
    }

    // ------------------------------------------------------------------ parked

    /** Spawns the parked representation at the scooter's stored location. */
    public void spawnParked() {
        despawn();
        Location origin = scooter.location();
        if (origin == null || !origin.isWorldLoaded()) {
            return;
        }
        Assembly.Part full = assembly.full();
        parked = spawnDisplay(origin, full.key(), full.itemModel(scooter.color()));
        parked.setShadowRadius(0.55f);
        parked.setShadowStrength(0.7f);
        // A parked scooter is drawn about its own design origin, so no stand compensation.
        applyTransform(parked, full.key(),
                transformFor(full, scooter.heading(), 0.0, 0.0, 0.0), false);

        hitbox = origin.getWorld().spawn(origin, Interaction.class, e -> {
            e.setInteractionWidth((float) Math.max(assembly.length() * 0.8, 0.9));
            e.setInteractionHeight((float) assembly.totalHeight());
            e.setResponsive(true);
            e.setPersistent(false);
            tag(e, "hitbox");
        });
        ridden = false;
    }

    // ------------------------------------------------------------------ ridden

    /**
     * Swaps to the articulated rig and attaches it to the rider.
     *
     * @return the mode actually achieved; TELEPORT means mounting was refused
     */
    public Mode spawnRidden(Player rider, Mode preferred) {
        despawn();
        Location at = rider.getLocation();
        for (String key : assembly.rigParts()) {
            Assembly.Part p = assembly.part(key);
            parts.put(key, spawnDisplay(at, key, p.itemModel(scooter.color())));
        }
        Assembly.Part wheel = assembly.wheel();
        parts.put("wheel_front", spawnDisplay(at, "wheel_front", wheel.itemModel(scooter.color())));
        parts.put("wheel_rear", spawnDisplay(at, "wheel_rear", wheel.itemModel(scooter.color())));

        mode = preferred;
        if (preferred == Mode.MOUNTED && !attach(rider)) {
            mode = Mode.TELEPORT;
        }
        for (ItemDisplay d : parts.values()) {
            d.setTeleportDuration(mode == Mode.MOUNTED ? 0 : 2);
        }
        ridden = true;
        return mode;
    }

    /** True when the parts ended up mounted, whether directly or chained. */
    private boolean attach(Player rider) {
        if (mountDirectly(rider)) {
            chained = false;
            return true;
        }
        detach();
        if (mountAsChain(rider)) {
            chained = true;
            return true;
        }
        detach();
        return false;
    }

    private boolean mountDirectly(Player rider) {
        for (ItemDisplay d : parts.values()) {
            if (!rider.addPassenger(d)) {
                return false;
            }
            chain.add(d);
        }
        // Only trust it if the vehicle really is carrying all of them.
        return rider.getPassengers().containsAll(chain);
    }

    private boolean mountAsChain(Player rider) {
        Entity previous = rider;
        for (ItemDisplay d : parts.values()) {
            if (!previous.addPassenger(d)) {
                return false;
            }
            chain.add(d);
            previous = d;
        }
        return true;
    }

    private void detach() {
        for (Entity e : chain) {
            if (e.isValid()) {
                e.leaveVehicle();
            }
        }
        chain.clear();
    }

    // ------------------------------------------------------------------ per-tick update

    /** Redraws the ridden rig. {@code anchor} is the rider, or null when parked. */
    public void update(RigPose pose, Player anchor) {
        if (!ridden) {
            return;
        }
        double lift = 0.0;
        if (mode == Mode.MOUNTED && anchor != null && !chain.isEmpty()) {
            Location head = chain.get(0).getLocation();
            double dy = head.getY() - anchor.getLocation().getY();
            // Ignore an implausible reading (the very first tick, before the server has positioned
            // the passenger) and keep the last good attachment height.
            if (dy > 0.0 && dy < 4.0) {
                attachmentY = dy;
            }
            lift = -attachmentY;
        } else if (mode == Mode.TELEPORT) {
            for (ItemDisplay d : parts.values()) {
                if (d.isValid()) {
                    d.teleport(pose.origin());
                }
            }
        }

        double yaw = pose.yaw();
        double steer = pose.steerDeg();
        double roll = pose.rollRad();
        double baseY = lift + config.verticalOffset;

        for (String key : assembly.rigParts()) {
            ItemDisplay d = parts.get(key);
            if (d == null || !d.isValid()) {
                continue;
            }
            Assembly.Part p = assembly.part(key);
            applyTransform(d, key, transformFor(p, yaw, steer, baseY, standOffsetZ), true);
            applyLampBrightness(d, key, pose);
        }

        Assembly.Part wheel = assembly.wheel();
        applyWheel(parts.get("wheel_front"), "wheel_front", wheel, yaw, steer, roll, baseY, true);
        applyWheel(parts.get("wheel_rear"), "wheel_rear", wheel, yaw, steer, roll, baseY, false);
    }

    private void applyWheel(ItemDisplay d, String key, Assembly.Part wheel, double yaw,
                            double steer, double roll, double baseY, boolean front) {
        if (d == null || !d.isValid()) {
            return;
        }
        double axleZ = front ? assembly.frontAxleZ() : assembly.rearAxleZ();
        Vector3f pivot = new Vector3f(0f, (float) assembly.axleY(), (float) axleZ);
        Quaternionf articulation = new Quaternionf().rotationX((float) roll);
        if (front) {
            rotateAboutSteerAxis(pivot, steer);
            // steer first, then roll: the axle itself has been swung round the steering axis
            articulation = new Quaternionf()
                    .rotationY((float) Math.toRadians(steer))
                    .mul(articulation);
        }
        pivot.y += (float) baseY;
        pivot.z -= (float) standOffsetZ;
        applyTransform(d, key, buildTransform(pivot, articulation, yaw, wheel.scale()),
                config.animateWheels);
    }

    /**
     * Places one part.
     *
     * <p>Translation runs in world axes, so the design-space pivot is yawed here; the left rotation
     * is the articulation followed by the frame yaw. See docs/RESEARCH.md section 5.
     *
     * @param standOffset how far forward the design origin sits from the anchor point; zero when
     *                    the anchor already is the design origin, as it is for a parked scooter
     */
    private Transformation transformFor(Assembly.Part part, double yaw, double steer,
                                        double baseY, double standOffset) {
        Vector3f pivot = new Vector3f((float) part.pivotX(), (float) part.pivotY(),
                (float) part.pivotZ());
        Quaternionf articulation = new Quaternionf();
        if (part.steers()) {
            rotateAboutSteerAxis(pivot, steer);
            articulation.rotationY((float) Math.toRadians(steer));
        }
        pivot.y += (float) baseY;
        pivot.z -= (float) standOffset;
        return buildTransform(pivot, articulation, yaw, part.scale());
    }

    private Transformation buildTransform(Vector3f pivot, Quaternionf articulation, double yaw,
                                          double scale) {
        // Minecraft yaw 0 faces +Z and grows towards -X, hence the negated angle.
        float yawRad = (float) Math.toRadians(-yaw);
        Quaternionf frame = new Quaternionf().rotationY(yawRad);
        Vector3f translation = frame.transform(new Vector3f(pivot));
        Quaternionf left = new Quaternionf(frame).mul(articulation);
        float s = (float) scale;
        return new Transformation(translation, left, new Vector3f(s, s, s), new Quaternionf());
    }

    private void rotateAboutSteerAxis(Vector3f pivot, double steerDeg) {
        float axisZ = (float) assembly.steerAxisZ();
        Vector3f rel = new Vector3f(pivot.x, pivot.y, pivot.z - axisZ);
        new Quaternionf().rotationY((float) Math.toRadians(steerDeg)).transform(rel);
        pivot.set(rel.x, rel.y, rel.z + axisZ);
    }

    /**
     * Sends a transformation, skipping updates that would not be visible.
     *
     * <p>Eight parts times twenty ticks is a lot of metadata packets; most ticks only one or two
     * parts have actually moved far enough to matter.
     */
    private void applyTransform(ItemDisplay d, String key, Transformation t, boolean interpolate) {
        Transformation previous = lastSent.get(key);
        if (previous != null && near(previous, t)) {
            return;
        }
        lastSent.put(key, t);
        if (interpolate) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
        }
        d.setTransformation(t);
    }

    private boolean near(Transformation a, Transformation b) {
        double eps = config.transformEpsilon;
        if (eps <= 0) {
            return false;
        }
        return a.getTranslation().distanceSquared(b.getTranslation()) < eps * eps
                && quaternionDistance(a.getLeftRotation(), b.getLeftRotation()) < eps;
    }

    private static double quaternionDistance(Quaternionf a, Quaternionf b) {
        float dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        return 1.0 - Math.abs(dot);
    }

    /** Lamps and the dash glow by overriding their Display brightness; item models cannot emit. */
    private void applyLampBrightness(ItemDisplay d, String key, RigPose pose) {
        Boolean lit = switch (key) {
            case "lamp_front", "lamp_rear" -> pose.lightsOn();
            case "dash" -> pose.dashOn();
            default -> null;
        };
        if (lit == null) {
            return;
        }
        Display.Brightness want = lit ? new Display.Brightness(15, 15) : null;
        Display.Brightness have = d.getBrightness();
        if (want == null ? have != null : !want.equals(have)) {
            d.setBrightness(want);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    private ItemDisplay spawnDisplay(Location at, String partKey, String itemModel) {
        return at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(items.displayStack(itemModel));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setViewRange(config.viewRange);
            e.setShadowRadius(0f);
            e.setShadowStrength(0f);
            e.setTeleportDuration(0);
            e.setInterpolationDuration(0);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setPersistent(false);          // never written to the region file, so no orphans
            tag(e, partKey);
        });
    }

    private void tag(Entity e, String part) {
        e.getPersistentDataContainer()
                .set(Keys.scooterId(), PersistentDataType.STRING, scooter.id().toString());
        e.getPersistentDataContainer().set(Keys.partName(), PersistentDataType.STRING, part);
    }

    public void despawn() {
        detach();
        for (ItemDisplay d : parts.values()) {
            if (d != null && !d.isDead()) {
                d.remove();
            }
        }
        parts.clear();
        lastSent.clear();
        if (parked != null) {
            parked.remove();
            parked = null;
        }
        if (hitbox != null) {
            hitbox.remove();
            hitbox = null;
        }
        ridden = false;
    }

    /** True if any of this rig's entities has been removed from under us (chunk unload, /kill). */
    public boolean stale() {
        if (parked != null && parked.isDead()) {
            return true;
        }
        if (hitbox != null && hitbox.isDead()) {
            return true;
        }
        for (ItemDisplay d : parts.values()) {
            if (d == null || d.isDead()) {
                return true;
            }
        }
        return false;
    }

    public Interaction hitbox() {
        return hitbox;
    }

    /** Repaints without rebuilding: only the item model changes. */
    public void refreshColor() {
        if (parked != null && parked.isValid()) {
            parked.setItemStack(items.displayStack(assembly.full().itemModel(scooter.color())));
        }
        for (Map.Entry<String, ItemDisplay> e : parts.entrySet()) {
            String key = e.getKey();
            Assembly.Part p = key.startsWith("wheel") ? assembly.wheel() : assembly.part(key);
            if (e.getValue().isValid()) {
                e.getValue().setItemStack(items.displayStack(p.itemModel(scooter.color())));
            }
        }
    }
}
