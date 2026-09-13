package dev.scootermc.listen;

import dev.scootermc.ScooterMC;
import dev.scootermc.audio.Sfx;
import dev.scootermc.config.DriveMode;
import dev.scootermc.model.Scooter;
import dev.scootermc.ride.RideSession;
import dev.scootermc.ride.ScooterService;
import dev.scootermc.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Everything the plugin reacts to.
 *
 * <p>The controls a rider has beyond WASD come from repurposing inputs that raise cancellable
 * events: swap-hands (F) toggles the headlight and the hotbar scroll wheel selects the drive mode.
 * Both are cancelled while riding, so nothing is really swapped or the held slot changed.
 *
 * <p>The drop key would have been the obvious third binding, but dropping with an empty hand
 * raises no event at all - so that control would have been silently dead for any rider not
 * carrying something.
 */
public final class ScooterListeners implements Listener {

    private final ScooterMC plugin;
    private final ScooterService service;

    public ScooterListeners(ScooterMC plugin) {
        this.plugin = plugin;
        this.service = plugin.service();
    }

    // ------------------------------------------------------------------ placing and riding

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // While riding, right-click sounds the horn and left-click rings the bell.
        RideSession ride = service.rideOf(player);
        if (ride != null) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            event.setCancelled(true);
            Location at = player.getLocation();
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK ->
                        at.getWorld().playSound(at, Sfx.HORN, SoundCategory.NEUTRAL, 1.0f, 1.0f);
                case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK ->
                        at.getWorld().playSound(at, Sfx.BELL, SoundCategory.NEUTRAL, 0.9f, 1.0f);
                default -> {
                }
            }
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (!plugin.items().isScooter(held)) {
            return;
        }
        event.setCancelled(true);

        if (!player.hasPermission("scootermc.place")) {
            plugin.lang().send(player, "error.no-permission");
            return;
        }
        if (service.atGlobalLimit()) {
            plugin.lang().send(player, "error.server-limit");
            return;
        }
        if (service.atPlayerLimit(player)) {
            plugin.lang().send(player, "error.player-limit",
                    "limit", String.valueOf(plugin.config().maxPerPlayer));
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.isEmpty() || event.getBlockFace() != BlockFace.UP) {
            // A scooter stands on its wheels; it only makes sense on top of something solid.
            plugin.lang().send(player, "error.no-room");
            return;
        }
        Location where = target.getLocation().add(0.5, 0.0, 0.5);
        double heading = normalise(player.getLocation().getYaw() + 180.0);

        Scooter scooter = service.place(where, heading, plugin.items().colorOf(held),
                plugin.items().batteryOf(held), plugin.items().odometerOf(held),
                plugin.items().lockedOf(held), player);
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        plugin.lang().send(player, "scooter.placed", "id", scooter.shortId());
    }

    /** Right-click to ride; sneak and right-click to fold it back into an item. */
    @EventHandler(ignoreCancelled = true)
    public void onRightClickScooter(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Scooter scooter = service.byEntity(event.getRightClicked());
        if (scooter == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            pickUp(event.getPlayer(), scooter);
        } else {
            mount(event.getPlayer(), scooter);
        }
    }

    /**
     * Left-clicking a parked scooter also folds it up, where the server reports the hit.
     *
     * <p>Interaction entities do not raise a damage event on every build, which is why the
     * dependable route is sneak plus right-click; this is a convenience on top of it.
     */
    @EventHandler(ignoreCancelled = true)
    public void onLeftClickScooter(EntityDamageByEntityEvent event) {
        Scooter scooter = service.byEntity(event.getEntity());
        if (scooter == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            pickUp(player, scooter);
        }
    }

    private void pickUp(Player player, Scooter scooter) {
        if (scooter.ridden()) {
            plugin.lang().send(player, "error.in-use");
            return;
        }
        if (!mayUse(player, scooter)) {
            plugin.lang().send(player, "error.locked", "owner", scooter.ownerName());
            return;
        }
        ItemStack item = plugin.items().from(scooter, plugin.lang().languageOf(player));
        service.remove(scooter);
        Location at = player.getLocation();
        at.getWorld().playSound(at, Sfx.PICKUP, SoundCategory.NEUTRAL, 0.8f, 1.0f);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> at.getWorld().dropItemNaturally(at, rest));
        plugin.lang().send(player, "scooter.picked-up", "id", scooter.shortId());
    }

    /** Our display entities must never take damage from anything. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageDisplay(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer()
                .has(Keys.scooterId(), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    // isOnGround is client-reported and deprecated for anti-cheat use; here it only decides
    // whether it makes sense to step onto a scooter, so the client's own view is fine.
    @SuppressWarnings("deprecation")
    private void mount(Player player, Scooter scooter) {
        if (!player.hasPermission("scootermc.ride")) {
            plugin.lang().send(player, "error.no-permission");
            return;
        }
        if (service.isRiding(player)) {
            return;
        }
        if (scooter.ridden()) {
            plugin.lang().send(player, "error.in-use");
            return;
        }
        if (!mayUse(player, scooter)) {
            plugin.lang().send(player, "error.locked", "owner", scooter.ownerName());
            player.playSound(player.getLocation(), Sfx.LOCK, SoundCategory.NEUTRAL, 0.8f, 1.0f);
            return;
        }
        if (plugin.config().batteryDrain && scooter.empty()) {
            plugin.lang().send(player, "error.flat-battery");
            return;
        }
        if (plugin.config().requireGroundToRide && !player.isOnGround()) {
            plugin.lang().send(player, "error.not-grounded");
            return;
        }
        if (service.startRide(player, scooter)) {
            plugin.lang().send(player, "ride.started",
                    "battery", String.format("%.0f",
                            scooter.batteryPercent(plugin.config().batteryCapacityWh)),
                    "mode", plugin.lang().raw(plugin.lang().languageOf(player),
                            "mode." + scooter.mode().configKey()));
            plugin.lang().send(player, "ride.controls");
        }
    }

    private boolean mayUse(Player player, Scooter scooter) {
        if (!scooter.locked()) {
            return true;
        }
        return scooter.isOwner(player.getUniqueId()) || player.hasPermission("scootermc.admin");
    }

    // ------------------------------------------------------------------ rider extra controls

    /**
     * F (swap hands) toggles the headlight.
     *
     * <p>Swap-hands fires even with nothing in either hand, which is why the headlight lives here
     * rather than on the drop key: dropping with an empty hand raises no event at all, so that
     * binding was silently dead for any rider not carrying something.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        RideSession ride = service.rideOf(event.getPlayer());
        if (ride == null) {
            return;
        }
        event.setCancelled(true);
        Scooter scooter = ride.scooter();
        boolean on = !scooter.headlightOn();
        scooter.headlightOn(on);
        if (!on) {
            ride.clearFakeLight();
        }
        service.markDirty();
        ride.sound().toRider(event.getPlayer(), Sfx.BEEP, 0.6f, on ? 1.4f : 0.9f);
        plugin.lang().send(event.getPlayer(), on ? "ride.light-on" : "ride.light-off");
    }

    /** The hotbar scroll wheel works as the gear selector: up for a faster mode, down for slower. */
    @EventHandler(ignoreCancelled = true)
    public void onHotbarScroll(PlayerItemHeldEvent event) {
        RideSession ride = service.rideOf(event.getPlayer());
        if (ride == null) {
            return;
        }
        event.setCancelled(true);
        final int hotbar = 9;
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta > hotbar / 2) {                 // the wheel wrapped round the end of the hotbar
            delta -= hotbar;
        } else if (delta < -hotbar / 2) {
            delta += hotbar;
        }
        if (delta == 0) {
            return;
        }
        Scooter scooter = ride.scooter();
        // Scrolling towards a lower slot number reads as "up" on the wheel.
        DriveMode next = scooter.mode().shift(-delta);
        if (next == scooter.mode()) {
            return;
        }
        scooter.mode(next);
        service.markDirty();
        ride.sound().toRider(event.getPlayer(), Sfx.MODE, 0.7f, 1.0f);
        plugin.lang().send(event.getPlayer(), "ride.mode-changed",
                "mode", plugin.lang().raw(plugin.lang().languageOf(event.getPlayer()),
                        "mode." + next.configKey()),
                "speed", String.format("%.0f",
                        plugin.config().tuning(next).maxSpeedMs() * 3.6));
    }

    // ------------------------------------------------------------------ ending a ride

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.endRide(event.getPlayer(), RideSession.EndReason.QUIT);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        service.endRide(event.getEntity(), RideSession.EndReason.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!service.isRiding(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        // Ignore the sub-block nudges some plugins perform; a real teleport ends the ride.
        if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < 4.0) {
            return;
        }
        service.endRide(event.getPlayer(), RideSession.EndReason.TELEPORT);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.endRide(event.getPlayer(), RideSession.EndReason.WORLD);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && service.isRiding(event.getPlayer())) {
            service.endRide(event.getPlayer(), RideSession.EndReason.DISMOUNT);
        }
    }

    // ------------------------------------------------------------------ housekeeping

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // If the server died mid-ride the player could come back frozen; undo that unconditionally.
        RideSession.scrub(event.getPlayer());
        plugin.resourcePack().offerOnJoin(event.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        service.purgeUnknown(event.getEntities().toArray(new Entity[0]));
    }

    private static double normalise(double yaw) {
        double d = yaw % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }
}
