package dev.scootermc.store;

import dev.scootermc.config.DriveMode;
import dev.scootermc.config.ScooterConfig;
import dev.scootermc.model.Scooter;
import dev.scootermc.model.ScooterColor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Flat-file persistence for parked scooters.
 *
 * <p>The visual entities are deliberately non-persistent, so this file is the only durable record
 * of a scooter. Saving is atomic (write to a temporary file, then move) so a crash mid-save cannot
 * leave a truncated store behind.
 */
public final class ScooterStore {

    private final File file;
    private final Logger log;
    private final ScooterConfig config;

    public ScooterStore(File dataFolder, ScooterConfig config, Logger log) {
        this.file = new File(dataFolder, "scooters.yml");
        this.config = config;
        this.log = log;
    }

    public List<Scooter> load() {
        List<Scooter> out = new ArrayList<>();
        if (!file.exists()) {
            return out;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("scooters");
        if (root == null) {
            return out;
        }
        int skipped = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                Scooter scooter = read(key, s);
                if (scooter == null) {
                    skipped++;
                } else {
                    out.add(scooter);
                }
            } catch (RuntimeException e) {
                skipped++;
                log.log(Level.WARNING, "Skipping malformed scooter '" + key + "'", e);
            }
        }
        if (skipped > 0) {
            log.warning(skipped + " scooter(s) could not be loaded and were skipped");
        }
        return out;
    }

    private Scooter read(String key, ConfigurationSection s) {
        UUID id = UUID.fromString(key);
        String worldName = s.getString("world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // The world may simply not be loaded yet; keeping the entry would need a
            // world-aware placeholder, so warn once and drop it rather than lose the file.
            log.warning("Scooter " + key + " lives in unknown world '" + worldName + "'");
            return null;
        }
        Location loc = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
        String ownerRaw = s.getString("owner", "");
        UUID owner = ownerRaw.isEmpty() ? null : UUID.fromString(ownerRaw);

        Scooter scooter = new Scooter(id, owner, s.getString("owner-name", "?"),
                ScooterColor.byId(s.getString("color"), ScooterColor.DEFAULT),
                DriveMode.byName(s.getString("mode"), config.defaultMode),
                clampBattery(s.getDouble("battery", config.batteryCapacityWh)),
                loc, s.getDouble("heading", 0.0));
        scooter.odometerMetres(Math.max(0.0, s.getDouble("odometer", 0.0)));
        scooter.locked(s.getBoolean("locked", false));
        scooter.headlightOn(s.getBoolean("headlight", false));
        scooter.createdAt(s.getLong("created", System.currentTimeMillis()));
        return scooter;
    }

    private double clampBattery(double wh) {
        return Math.max(0.0, Math.min(config.batteryCapacityWh, wh));
    }

    public void save(Iterable<Scooter> scooters) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "ScooterMC parked scooters.",
                "Generated automatically - edit only while the server is stopped."));
        for (Scooter scooter : scooters) {
            Location loc = scooter.location();
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            String base = "scooters." + scooter.id();
            yaml.set(base + ".world", loc.getWorld().getName());
            yaml.set(base + ".x", round(loc.getX()));
            yaml.set(base + ".y", round(loc.getY()));
            yaml.set(base + ".z", round(loc.getZ()));
            yaml.set(base + ".heading", round(scooter.heading()));
            yaml.set(base + ".owner", scooter.owner() == null ? "" : scooter.owner().toString());
            yaml.set(base + ".owner-name", scooter.ownerName());
            yaml.set(base + ".color", scooter.color().id());
            yaml.set(base + ".mode", scooter.mode().name());
            yaml.set(base + ".battery", round(scooter.batteryWh()));
            yaml.set(base + ".odometer", round(scooter.odometerMetres()));
            yaml.set(base + ".locked", scooter.locked());
            yaml.set(base + ".headlight", scooter.headlightOn());
            yaml.set(base + ".created", scooter.createdAt());
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            yaml.save(tmp);
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
