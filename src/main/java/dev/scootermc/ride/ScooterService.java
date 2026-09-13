package dev.scootermc.ride;

import dev.scootermc.ScooterMC;
import dev.scootermc.audio.Sfx;
import dev.scootermc.config.ScooterConfig;
import dev.scootermc.model.Scooter;
import dev.scootermc.model.ScooterColor;
import dev.scootermc.render.ScooterRig;
import dev.scootermc.store.ScooterStore;
import dev.scootermc.util.Keys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * The registry: every scooter in the world, its renderer, and who is riding it.
 *
 * <p>Also owns the single repeating task that advances every active ride. One task for all riders
 * keeps ordering deterministic and makes shutdown a single, reliable teardown.
 */
public final class ScooterService {

    private final ScooterMC plugin;
    private final ScooterConfig config;
    private final ScooterStore store;

    private final Map<UUID, Scooter> scooters = new LinkedHashMap<>();
    private final Map<UUID, ScooterRig> rigs = new HashMap<>();
    private final Map<UUID, RideSession> rides = new HashMap<>();

    private int tickTask = -1;
    private int saveTask = -1;
    private boolean dirty;

    public ScooterService(ScooterMC plugin, ScooterStore store) {
        this.plugin = plugin;
        this.config = plugin.config();
        this.store = store;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        purgeOrphans();
        for (Scooter scooter : store.load()) {
            scooters.put(scooter.id(), scooter);
            spawnRig(scooter);
        }
        plugin.getLogger().info("Loaded " + scooters.size() + " scooter(s)");

        tickTask = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::tickAll, 1L, 1L);
        saveTask = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            sweepAbandoned();
            if (dirty) {
                saveNow();
            }
        }, 20L * config.saveIntervalSeconds, 20L * config.saveIntervalSeconds);
    }

    /**
     * Clears away scooters that have been parked and untouched for too long, so a busy server does
     * not slowly fill up with them. Disabled by default.
     */
    private void sweepAbandoned() {
        if (config.despawnParkedMinutes <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - config.despawnParkedMinutes * 60_000L;
        List<Scooter> stale = new ArrayList<>();
        for (Scooter scooter : scooters.values()) {
            if (!scooter.ridden() && scooter.parkedSince() < cutoff) {
                stale.add(scooter);
            }
        }
        stale.forEach(this::remove);
        if (!stale.isEmpty()) {
            plugin.getLogger().info("Removed " + stale.size() + " scooter(s) parked for more than "
                    + config.despawnParkedMinutes + " minutes");
        }
    }

    public void shutdown() {
        if (tickTask != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTask);
            tickTask = -1;
        }
        if (saveTask != -1) {
            plugin.getServer().getScheduler().cancelTask(saveTask);
            saveTask = -1;
        }
        for (RideSession session : new ArrayList<>(rides.values())) {
            endRide(session.player(), RideSession.EndReason.SHUTDOWN);
        }
        saveNow();
        for (ScooterRig rig : rigs.values()) {
            rig.despawn();
        }
        rigs.clear();
        scooters.clear();
    }

    public void saveNow() {
        store.save(scooters.values());
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    /**
     * Removes display entities left behind by a hard crash.
     *
     * <p>Rig entities are spawned non-persistent, so this should normally find nothing; it exists
     * because "should normally" is not a guarantee when a server is killed mid-tick.
     */
    private void purgeOrphans() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getPersistentDataContainer().has(Keys.scooterId(), PersistentDataType.STRING)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover scooter display entit(ies)");
        }
    }

    /** Called when a chunk loads: anything tagged but not ours is a leftover. */
    public void purgeUnknown(Entity[] entities) {
        for (Entity e : entities) {
            String id = e.getPersistentDataContainer().get(Keys.scooterId(), PersistentDataType.STRING);
            if (id == null) {
                continue;
            }
            ScooterRig rig = rigs.get(parse(id));
            if (rig == null) {
                e.remove();
            }
        }
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ registry

    public Collection<Scooter> all() {
        return Collections.unmodifiableCollection(scooters.values());
    }

    public Scooter byId(UUID id) {
        return scooters.get(id);
    }

    /** Resolves the eight-character handle shown in messages and used by commands. */
    public Scooter byShortId(String shortId) {
        for (Scooter s : scooters.values()) {
            if (s.shortId().equalsIgnoreCase(shortId)) {
                return s;
            }
        }
        return null;
    }

    public List<Scooter> ownedBy(UUID owner) {
        List<Scooter> out = new ArrayList<>();
        for (Scooter s : scooters.values()) {
            if (s.isOwner(owner)) {
                out.add(s);
            }
        }
        return out;
    }

    public int count() {
        return scooters.size();
    }

    public boolean atGlobalLimit() {
        return scooters.size() >= config.maxTotal;
    }

    public boolean atPlayerLimit(Player player) {
        return !player.hasPermission("scootermc.limit.bypass")
                && ownedBy(player.getUniqueId()).size() >= config.maxPerPlayer;
    }

    /** Places a new scooter in the world. */
    public Scooter place(Location where, double heading, ScooterColor color, double batteryWh,
                         double odometerMetres, boolean locked, Player owner) {
        Scooter scooter = new Scooter(UUID.randomUUID(),
                owner == null ? null : owner.getUniqueId(),
                owner == null ? "server" : owner.getName(),
                color, config.defaultMode, batteryWh, where, heading);
        scooter.odometerMetres(odometerMetres);
        scooter.locked(locked);
        scooter.touchParked();
        scooters.put(scooter.id(), scooter);
        spawnRig(scooter);
        markDirty();
        if (where.getWorld() != null) {
            where.getWorld().playSound(where, Sfx.DEPLOY, org.bukkit.SoundCategory.NEUTRAL, 0.8f, 1f);
        }
        return scooter;
    }

    /** Removes a scooter entirely; its item form, if any, is the caller's business. */
    public void remove(Scooter scooter) {
        RideSession ride = rideOf(scooter);
        if (ride != null) {
            endRide(ride.player(), RideSession.EndReason.BLOCKED);
        }
        ScooterRig rig = rigs.remove(scooter.id());
        if (rig != null) {
            rig.despawn();
        }
        scooters.remove(scooter.id());
        markDirty();
    }

    public ScooterRig rigOf(Scooter scooter) {
        return rigs.get(scooter.id());
    }

    private void spawnRig(Scooter scooter) {
        ScooterRig rig = new ScooterRig(plugin.assembly(), config, plugin.items(), scooter);
        rigs.put(scooter.id(), rig);
        rig.spawnParked();
    }

    /** Finds the scooter an Interaction or Display entity belongs to. */
    public Scooter byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer()
                .get(Keys.scooterId(), PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        UUID uuid = parse(id);
        return uuid == null ? null : scooters.get(uuid);
    }

    // ------------------------------------------------------------------ riding

    public RideSession rideOf(Player player) {
        return rides.get(player.getUniqueId());
    }

    public RideSession rideOf(Scooter scooter) {
        for (RideSession s : rides.values()) {
            if (s.scooter().id().equals(scooter.id())) {
                return s;
            }
        }
        return null;
    }

    public boolean isRiding(Player player) {
        return rides.containsKey(player.getUniqueId());
    }

    public Collection<RideSession> rides() {
        return Collections.unmodifiableCollection(rides.values());
    }

    /** @return true if the rider is now on the scooter */
    public boolean startRide(Player player, Scooter scooter) {
        if (rides.containsKey(player.getUniqueId()) || scooter.ridden()) {
            return false;
        }
        ScooterRig rig = rigs.get(scooter.id());
        if (rig == null) {
            return false;
        }
        ScooterRig.Mode mode = rig.spawnRidden(player, ScooterRig.Mode.MOUNTED);
        if (mode == ScooterRig.Mode.TELEPORT) {
            plugin.getLogger().warning("Could not mount the scooter rig on " + player.getName()
                    + "; falling back to teleport rendering, which will look slightly delayed");
        }
        RideSession session = new RideSession(plugin, player, scooter, rig);
        rides.put(player.getUniqueId(), session);
        session.start();
        markDirty();
        return true;
    }

    public void endRide(Player player, RideSession.EndReason reason) {
        RideSession session = rides.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.end(reason);
        plugin.hud().hide(player);

        Scooter scooter = session.scooter();
        ScooterRig rig = rigs.get(scooter.id());
        if (rig != null) {
            rig.despawn();
            // Park it where the rider left it, facing the way they were travelling.
            Location park = restingPlace(player.getLocation(), scooter.heading());
            scooter.location(park);
            scooter.touchParked();
            if (scooters.containsKey(scooter.id())) {
                rig.spawnParked();
            }
        }
        markDirty();
        if (reason == RideSession.EndReason.BATTERY) {
            plugin.lang().send(player, "ride.battery-empty");
        }
    }

    /**
     * Where a scooter ends up when the rider steps off: on the ground under them, nudged out of
     * the way so it does not sit inside the player's own hitbox.
     */
    private Location restingPlace(Location riderAt, double heading) {
        double rad = Math.toRadians(heading + 90.0);
        Location park = riderAt.clone().add(-Math.sin(rad) * 0.55, 0.0, Math.cos(rad) * 0.55);
        park.setYaw((float) heading);
        park.setPitch(0f);
        return park;
    }

    // ------------------------------------------------------------------ tick

    private void tickAll() {
        if (rides.isEmpty()) {
            return;
        }
        for (RideSession session : new ArrayList<>(rides.values())) {
            boolean keep;
            try {
                keep = session.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Ride tick failed for " + session.player().getName()
                                + "; dismounting them to stay safe", e);
                keep = false;
            }
            if (!keep) {
                endRide(session.player(), session.endReason());
            }
        }
    }
}
