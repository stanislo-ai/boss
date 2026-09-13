package dev.scootermc.hud;

import dev.scootermc.config.ScooterConfig;
import dev.scootermc.lang.Lang;
import dev.scootermc.model.Scooter;
import dev.scootermc.ride.Physics;
import dev.scootermc.ride.RideSession;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** The rider's dash: speed, battery, drive mode and headlight state. */
public final class Hud {

    private final ScooterConfig config;
    private final Lang lang;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public Hud(ScooterConfig config, Lang lang) {
        this.config = config;
        this.lang = lang;
    }

    public void show(RideSession session) {
        Player player = session.player();
        Scooter scooter = session.scooter();

        double speed = Math.abs(session.speedMs());
        double displayed = config.hudUnits == ScooterConfig.Units.MPH
                ? speed * 2.236936 : Physics.msToKmh(speed);
        String unit = lang.raw(lang.languageOf(player),
                config.hudUnits == ScooterConfig.Units.MPH ? "unit.mph" : "unit.kmh");
        double percent = scooter.batteryPercent(config.batteryCapacityWh);

        Object[] tags = {
                "speed", String.format(Locale.ROOT, "%.0f", displayed),
                "unit", unit,
                "battery", String.format(Locale.ROOT, "%.0f", percent),
                "bar", batteryBar(percent),
                "mode", lang.raw(lang.languageOf(player), "mode." + scooter.mode().configKey()),
                "light", lang.raw(lang.languageOf(player),
                        scooter.headlightOn() ? "state.light-on" : "state.light-off"),
                "odometer", String.format(Locale.ROOT, "%.2f", scooter.odometerMetres() / 1000.0),
        };

        if (config.hudActionBar) {
            player.sendActionBar(lang.get(player, "hud.action-bar", tags));
        }
        if (config.hudBossBar) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> {
                BossBar b = BossBar.bossBar(Component.empty(), 1f,
                        BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                player.showBossBar(b);
                return b;
            });
            bar.name(lang.get(player, "hud.boss-bar", tags));
            bar.progress((float) Physics.clamp01(percent / 100.0));
            bar.color(percent <= config.lowBatteryPercent ? BossBar.Color.RED
                    : percent < 50 ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
        }
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        if (config.hudActionBar) {
            player.sendActionBar(Component.empty());
        }
    }

    public void hideAll() {
        bars.clear();
    }

    /**
     * Ten-segment battery gauge, the same shape as the one painted on the dash.
     *
     * <p>Returned as a Component rather than markup: placeholders are inserted unparsed so that a
     * translator can never accidentally (or deliberately) inject formatting through a message file.
     */
    private static Component batteryBar(double percent) {
        int filled = (int) Math.round(Physics.clamp(percent, 0, 100) / 10.0);
        TextColor full = percent <= 15 ? NamedTextColor.RED
                : percent <= 40 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        Component bar = Component.empty();
        for (int i = 0; i < 10; i++) {
            bar = bar.append(Component.text('|', i < filled ? full : NamedTextColor.DARK_GRAY));
        }
        return bar;
    }
}
