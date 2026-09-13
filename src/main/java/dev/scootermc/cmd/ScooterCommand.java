package dev.scootermc.cmd;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.scootermc.ScooterMC;
import dev.scootermc.audio.Sfx;
import dev.scootermc.model.Scooter;
import dev.scootermc.model.ScooterColor;
import dev.scootermc.ride.RideSession;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The /scooter command tree, registered through Paper's Brigadier lifecycle so players get real
 * argument-by-argument completion and error highlighting rather than a flat string split.
 */
public final class ScooterCommand {

    private final ScooterMC plugin;

    private ScooterCommand(ScooterMC plugin) {
        this.plugin = plugin;
    }

    public static LiteralCommandNode<CommandSourceStack> build(ScooterMC plugin) {
        return new ScooterCommand(plugin).tree();
    }

    private LiteralCommandNode<CommandSourceStack> tree() {
        return Commands.literal("scooter")
                .requires(src -> src.getSender().hasPermission("scootermc.command"))
                .executes(this::help)
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("give")
                        .requires(perm("scootermc.command.give"))
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(c -> give(c, ScooterColor.DEFAULT))
                                .then(colorArg(this::give))))
                .then(Commands.literal("spawn")
                        .requires(perm("scootermc.command.spawn"))
                        .executes(c -> spawn(c, ScooterColor.DEFAULT))
                        .then(colorArg(this::spawn)))
                .then(Commands.literal("list")
                        .requires(perm("scootermc.command.list"))
                        .executes(this::listOwn)
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .requires(perm("scootermc.command.list.others"))
                                .executes(this::listOther)))
                .then(Commands.literal("info")
                        .requires(perm("scootermc.command.info"))
                        .executes(this::info))
                .then(Commands.literal("tp")
                        .requires(perm("scootermc.command.tp"))
                        .then(idArg().executes(this::teleport)))
                .then(Commands.literal("charge")
                        .requires(perm("scootermc.command.charge"))
                        .executes(c -> charge(c, Double.MAX_VALUE))
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                .executes(c -> charge(c, DoubleArgumentType.getDouble(c, "percent")))))
                .then(Commands.literal("paint")
                        .requires(perm("scootermc.command.paint"))
                        .then(colorArg(this::paint)))
                .then(Commands.literal("lock")
                        .requires(perm("scootermc.command.lock"))
                        .executes(c -> setLocked(c, true)))
                .then(Commands.literal("unlock")
                        .requires(perm("scootermc.command.lock"))
                        .executes(c -> setLocked(c, false)))
                .then(Commands.literal("remove")
                        .requires(perm("scootermc.command.remove"))
                        .then(idArg().executes(this::remove)))
                .then(Commands.literal("clear")
                        .requires(perm("scootermc.command.clear"))
                        .executes(this::clearOwn)
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(this::clearOther)))
                .then(Commands.literal("pack")
                        .requires(perm("scootermc.command.pack"))
                        .executes(this::packSelf)
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(this::packOthers)))
                .then(Commands.literal("lang")
                        .requires(perm("scootermc.command.lang"))
                        .then(Commands.argument("code", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    plugin.lang().available().forEach(b::suggest);
                                    b.suggest("reset");
                                    return b.buildFuture();
                                })
                                .executes(this::setLanguage)))
                .then(Commands.literal("diagnose")
                        .requires(perm("scootermc.admin"))
                        .executes(this::diagnose))
                .then(Commands.literal("selftest")
                        .requires(perm("scootermc.admin"))
                        .executes(this::selfTest))
                .then(Commands.literal("reload")
                        .requires(perm("scootermc.command.reload"))
                        .executes(this::reload))
                .build();
    }

    // ------------------------------------------------------------------ argument helpers

    private java.util.function.Predicate<CommandSourceStack> perm(String node) {
        return src -> src.getSender().hasPermission(node);
    }

    private interface ColorAction {
        int run(CommandContext<CommandSourceStack> ctx, ScooterColor color)
                throws CommandSyntaxException;
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            colorArg(ColorAction action) {
        return Commands.argument("color", StringArgumentType.word())
                .suggests((c, b) -> {
                    for (ScooterColor col : ScooterColor.values()) {
                        b.suggest(col.id());
                    }
                    return b.buildFuture();
                })
                .executes(c -> {
                    String raw = StringArgumentType.getString(c, "color");
                    ScooterColor col = ScooterColor.byId(raw, null);
                    if (col == null) {
                        plugin.lang().send(c.getSource().getSender(), "error.bad-color", "value", raw);
                        return 0;
                    }
                    return action.run(c, col);
                });
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> idArg() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> {
                    for (Scooter s : plugin.service().all()) {
                        b.suggest(s.shortId());
                    }
                    return b.buildFuture();
                });
    }

    private Scooter resolveId(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        Scooter scooter = plugin.service().byShortId(id);
        if (scooter == null) {
            plugin.lang().send(ctx.getSource().getSender(), "error.unknown-scooter", "id", id);
        }
        return scooter;
    }

    /** The scooter the sender is riding, or the nearest one within a few blocks. */
    private Scooter contextScooter(Player player) {
        RideSession ride = plugin.service().rideOf(player);
        if (ride != null) {
            return ride.scooter();
        }
        Scooter best = null;
        double bestDistance = 25.0;
        for (Scooter s : plugin.service().all()) {
            Location at = s.location();
            if (at == null || at.getWorld() != player.getWorld()) {
                continue;
            }
            double d = at.distanceSquared(player.getLocation());
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }

    private Player requirePlayer(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getSender() instanceof Player p) {
            return p;
        }
        plugin.lang().send(ctx.getSource().getSender(), "error.players-only");
        return null;
    }

    // ------------------------------------------------------------------ subcommands

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.lang().sendRaw(sender, "help.header", "version",
                plugin.getPluginMeta().getVersion());
        for (String line : plugin.lang().raw(plugin.lang().languageOf(sender), "help.body")
                .split("\\n")) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(line));
        }
        return 1;
    }

    private int give(CommandContext<CommandSourceStack> ctx, ScooterColor color)
            throws CommandSyntaxException {
        List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        CommandSender sender = ctx.getSource().getSender();
        for (Player target : targets) {
            ItemStack item = plugin.items().create(color, plugin.lang().languageOf(target));
            target.getInventory().addItem(item).values()
                    .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
            plugin.lang().send(target, "command.received", "color",
                    plugin.lang().raw(plugin.lang().languageOf(target), "color." + color.id()));
        }
        plugin.lang().send(sender, "command.given", "count", String.valueOf(targets.size()));
        return targets.size();
    }

    private int spawn(CommandContext<CommandSourceStack> ctx, ScooterColor color) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        if (plugin.service().atGlobalLimit()) {
            plugin.lang().send(player, "error.server-limit");
            return 0;
        }
        Location at = player.getLocation();
        Scooter scooter = plugin.service().place(
                new Location(at.getWorld(), Math.floor(at.getX()) + 0.5, at.getY(),
                        Math.floor(at.getZ()) + 0.5),
                at.getYaw() + 180.0, color, plugin.config().batteryCapacityWh, 0.0, false, player);
        plugin.lang().send(player, "scooter.placed", "id", scooter.shortId());
        return 1;
    }

    private int listOwn(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        return player == null ? 0 : listFor(ctx.getSource().getSender(), player);
    }

    private int listOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        return listFor(ctx.getSource().getSender(), targets.get(0));
    }

    private int listFor(CommandSender sender, Player owner) {
        List<Scooter> owned = plugin.service().ownedBy(owner.getUniqueId());
        plugin.lang().send(sender, "command.list-header",
                "player", owner.getName(), "count", String.valueOf(owned.size()));
        for (Scooter s : owned) {
            Location at = s.location();
            plugin.lang().sendRaw(sender, "command.list-entry",
                    "id", s.shortId(),
                    "color", plugin.lang().raw(plugin.lang().languageOf(sender),
                            "color." + s.color().id()),
                    "battery", String.format(Locale.ROOT, "%.0f",
                            s.batteryPercent(plugin.config().batteryCapacityWh)),
                    "world", at == null || at.getWorld() == null ? "?" : at.getWorld().getName(),
                    "x", at == null ? "?" : String.valueOf(at.getBlockX()),
                    "y", at == null ? "?" : String.valueOf(at.getBlockY()),
                    "z", at == null ? "?" : String.valueOf(at.getBlockZ()));
        }
        return owned.size();
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        Scooter s = contextScooter(player);
        if (s == null) {
            plugin.lang().send(player, "error.no-scooter-nearby");
            return 0;
        }
        double perKm = plugin.config().tuning(s.mode()).whPerKm();
        plugin.lang().send(player, "command.info",
                "id", s.shortId(),
                "owner", s.ownerName(),
                "color", plugin.lang().raw(plugin.lang().languageOf(player), "color." + s.color().id()),
                "mode", plugin.lang().raw(plugin.lang().languageOf(player),
                        "mode." + s.mode().configKey()),
                "battery", String.format(Locale.ROOT, "%.1f",
                        s.batteryPercent(plugin.config().batteryCapacityWh)),
                "wh", String.format(Locale.ROOT, "%.0f", s.batteryWh()),
                "range", String.format(Locale.ROOT, "%.1f",
                        perKm <= 0 ? 0 : s.batteryWh() / perKm),
                "odometer", String.format(Locale.ROOT, "%.2f", s.odometerMetres() / 1000.0),
                "locked", plugin.lang().raw(plugin.lang().languageOf(player),
                        s.locked() ? "state.locked" : "state.unlocked"));
        return 1;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        Scooter s = resolveId(ctx);
        if (s == null) {
            return 0;
        }
        if (!s.isOwner(player.getUniqueId()) && !player.hasPermission("scootermc.admin")) {
            plugin.lang().send(player, "error.not-yours");
            return 0;
        }
        Location at = s.location();
        if (at == null || at.getWorld() == null) {
            plugin.lang().send(player, "error.unknown-scooter", "id", s.shortId());
            return 0;
        }
        at.setYaw(player.getLocation().getYaw());
        at.setPitch(player.getLocation().getPitch());
        player.teleport(at);
        plugin.lang().send(player, "command.teleported", "id", s.shortId());
        return 1;
    }

    private int charge(CommandContext<CommandSourceStack> ctx, double percent) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        Scooter s = contextScooter(player);
        if (s == null) {
            plugin.lang().send(player, "error.no-scooter-nearby");
            return 0;
        }
        double capacity = plugin.config().batteryCapacityWh;
        double wh = percent >= 100 ? capacity : capacity * percent / 100.0;
        s.batteryWh(Math.min(capacity, Math.max(s.batteryWh(), wh)));
        plugin.service().markDirty();
        player.playSound(player.getLocation(), Sfx.POWER_ON, SoundCategory.NEUTRAL, 0.7f, 1.2f);
        plugin.lang().send(player, "command.charged", "id", s.shortId(),
                "battery", String.format(Locale.ROOT, "%.0f", s.batteryPercent(capacity)));
        return 1;
    }

    private int paint(CommandContext<CommandSourceStack> ctx, ScooterColor color) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        Scooter s = contextScooter(player);
        if (s == null) {
            plugin.lang().send(player, "error.no-scooter-nearby");
            return 0;
        }
        if (!s.isOwner(player.getUniqueId()) && !player.hasPermission("scootermc.admin")) {
            plugin.lang().send(player, "error.not-yours");
            return 0;
        }
        s.color(color);
        var rig = plugin.service().rigOf(s);
        if (rig != null) {
            rig.refreshColor();
        }
        plugin.service().markDirty();
        plugin.lang().send(player, "command.painted", "color",
                plugin.lang().raw(plugin.lang().languageOf(player), "color." + color.id()));
        return 1;
    }

    private int setLocked(CommandContext<CommandSourceStack> ctx, boolean locked) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        Scooter s = contextScooter(player);
        if (s == null) {
            plugin.lang().send(player, "error.no-scooter-nearby");
            return 0;
        }
        if (!s.isOwner(player.getUniqueId()) && !player.hasPermission("scootermc.admin")) {
            plugin.lang().send(player, "error.not-yours");
            return 0;
        }
        s.locked(locked);
        plugin.service().markDirty();
        player.playSound(player.getLocation(), Sfx.LOCK, SoundCategory.NEUTRAL, 0.7f,
                locked ? 1.0f : 1.3f);
        plugin.lang().send(player, locked ? "command.locked" : "command.unlocked",
                "id", s.shortId());
        return 1;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Scooter s = resolveId(ctx);
        if (s == null) {
            return 0;
        }
        if (sender instanceof Player p && !s.isOwner(p.getUniqueId())
                && !p.hasPermission("scootermc.admin")) {
            plugin.lang().send(sender, "error.not-yours");
            return 0;
        }
        plugin.service().remove(s);
        plugin.lang().send(sender, "command.removed", "id", s.shortId());
        return 1;
    }

    private int clearOwn(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        return player == null ? 0 : clearFor(ctx.getSource().getSender(), player);
    }

    private int clearOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ctx.getSource().getSender().hasPermission("scootermc.admin")) {
            plugin.lang().send(ctx.getSource().getSender(), "error.no-permission");
            return 0;
        }
        List<Player> targets = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource());
        return clearFor(ctx.getSource().getSender(), targets.get(0));
    }

    private int clearFor(CommandSender sender, Player owner) {
        List<Scooter> owned = plugin.service().ownedBy(owner.getUniqueId());
        owned.forEach(plugin.service()::remove);
        plugin.lang().send(sender, "command.cleared",
                "count", String.valueOf(owned.size()), "player", owner.getName());
        return owned.size();
    }

    private int packSelf(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        return sendPack(ctx.getSource().getSender(), List.of(player));
    }

    private int packOthers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return sendPack(ctx.getSource().getSender(),
                ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                        .resolve(ctx.getSource()));
    }

    private int sendPack(CommandSender sender, List<Player> targets) {
        if (!plugin.resourcePack().available()) {
            plugin.lang().send(sender, "error.no-pack");
            return 0;
        }
        int sent = 0;
        for (Player p : targets) {
            if (plugin.resourcePack().send(p)) {
                sent++;
            }
        }
        plugin.lang().send(sender, "command.pack-sent", "count", String.valueOf(sent));
        return sent;
    }

    private int setLanguage(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        if (player == null) {
            return 0;
        }
        String code = StringArgumentType.getString(ctx, "code").toLowerCase(Locale.ROOT);
        if ("reset".equals(code)) {
            plugin.lang().setLanguage(player, null);
            plugin.lang().send(player, "command.language-reset");
            return 1;
        }
        if (!plugin.lang().has(code)) {
            plugin.lang().send(player, "error.bad-language", "value", code,
                    "available", String.join(", ", plugin.lang().available()));
            return 0;
        }
        plugin.lang().setLanguage(player, code);
        plugin.lang().send(player, "command.language-set", "language", code);
        return 1;
    }

    private int diagnose(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        StringBuilder sb = new StringBuilder();
        sb.append("scooters=").append(plugin.service().count());
        sb.append(" rides=").append(plugin.service().rides().size());
        for (RideSession s : plugin.service().rides()) {
            sb.append("\n  ").append(s.player().getName())
                    .append(" rig=").append(s.rig().mode())
                    .append(s.rig().chained() ? " (chained)" : "")
                    .append(String.format(Locale.ROOT, " speed=%.1f km/h", s.speedMs() * 3.6))
                    .append(String.format(Locale.ROOT, " throttle=%.2f", s.throttle()));
        }
        sb.append("\npack: available=").append(plugin.resourcePack().available())
                .append(" sha1=").append(plugin.resourcePack().sha1())
                .append(" url=").append(plugin.resourcePack().url());
        sender.sendMessage(net.kyori.adventure.text.Component.text(sb.toString()));
        return 1;
    }

    /**
     * Verifies, on this actual server build, the two things the renderer depends on and that no
     * amount of unit testing can prove: that a scooter's rig really can be spawned and parked, and
     * that Display entities really can be chained onto a carrier as passengers.
     *
     * <p>Vanilla lets an entity carry only one passenger, so the ridden rig hangs its parts off
     * each other. If a future Minecraft build changed that, the plugin would silently fall back to
     * laggier teleport rendering - this makes the check explicit and runnable from the console.
     *
     * <p>Everything it spawns is cleaned up before it reports.
     */
    private int selfTest(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<String> results = new java.util.ArrayList<>();
        boolean ok = true;

        // 1. the assembly description
        try {
            var asm = plugin.assembly();
            int parts = asm.rigParts().size() + 2;
            results.add("PASS  assembly.json: " + parts + " rig parts, wheel radius "
                    + String.format(Locale.ROOT, "%.4f", asm.tyreRadius()) + " blocks");
        } catch (RuntimeException e) {
            ok = false;
            results.add("FAIL  assembly.json: " + e);
        }

        // 2. spawn and park a real scooter
        Location at = ctx.getSource().getLocation();
        Scooter probe = null;
        try {
            probe = plugin.service().place(at, 0.0, ScooterColor.DEFAULT,
                    plugin.config().batteryCapacityWh, 0.0, false, null);
            var rig = plugin.service().rigOf(probe);
            boolean spawned = rig != null && rig.hitbox() != null && !rig.stale();
            ok &= spawned;
            results.add((spawned ? "PASS" : "FAIL") + "  parked rig spawned at "
                    + at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY()
                    + "," + at.getBlockZ());
        } catch (RuntimeException e) {
            ok = false;
            results.add("FAIL  parked rig: " + e);
        } finally {
            if (probe != null) {
                plugin.service().remove(probe);
            }
        }

        // 3. the passenger chain
        org.bukkit.entity.Entity carrier = null;
        List<org.bukkit.entity.Entity> spawned = new java.util.ArrayList<>();
        try {
            carrier = at.getWorld().spawn(at, org.bukkit.entity.ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setPersistent(false);
            });
            spawned.add(carrier);
            int want = plugin.assembly().rigParts().size() + 2;
            int direct = 0;
            for (int i = 0; i < want; i++) {
                org.bukkit.entity.ItemDisplay d = at.getWorld()
                        .spawn(at, org.bukkit.entity.ItemDisplay.class, e -> e.setPersistent(false));
                spawned.add(d);
                if (!carrier.addPassenger(d)) {
                    break;
                }
                direct++;
            }
            boolean allDirect = direct == want && carrier.getPassengers().size() == want;
            ok &= allDirect;
            results.add((allDirect ? "PASS" : "FAIL") + "  " + direct + "/" + want
                    + " displays mounted directly on one carrier"
                    + (allDirect ? "" : " - the rig will chain them or fall back to teleporting"));

        } catch (RuntimeException e) {
            ok = false;
            results.add("FAIL  passenger chain: " + e);
        } finally {
            for (org.bukkit.entity.Entity e : spawned) {
                for (org.bukkit.entity.Entity p : e.getPassengers()) {
                    p.remove();
                }
                e.remove();
            }
        }

        // 4. the resource pack
        boolean pack = plugin.resourcePack().packFile() != null
                && plugin.resourcePack().packFile().isFile();
        results.add((pack ? "PASS" : "FAIL") + "  resource pack written to "
                + plugin.resourcePack().packFile() + " sha1=" + plugin.resourcePack().sha1());
        ok &= pack;

        // 5. languages
        boolean langs = plugin.lang().has("en") && plugin.lang().has("pl");
        ok &= langs;
        results.add((langs ? "PASS" : "FAIL") + "  languages loaded: "
                + String.join(", ", plugin.lang().available()));

        sender.sendMessage(net.kyori.adventure.text.Component.text(
                "ScooterMC self-test: " + (ok ? "ALL PASS" : "FAILURES") + "\n  "
                        + String.join("\n  ", results)));
        return ok ? 1 : 0;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reload();
        plugin.lang().send(ctx.getSource().getSender(), "command.reloaded");
        return 1;
    }
}
