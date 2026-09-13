package dev.scootermc;

import dev.scootermc.audio.Sfx;
import dev.scootermc.cmd.ScooterCommand;
import dev.scootermc.config.ScooterConfig;
import dev.scootermc.hud.Hud;
import dev.scootermc.item.ScooterItems;
import dev.scootermc.lang.Lang;
import dev.scootermc.listen.ScooterListeners;
import dev.scootermc.model.Assembly;
import dev.scootermc.pack.ResourcePackService;
import dev.scootermc.ride.RideSession;
import dev.scootermc.ride.ScooterService;
import dev.scootermc.store.ScooterStore;
import dev.scootermc.util.Keys;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ScooterMC - rideable electric scooters modelled on a KuKirin G2 Pro.
 *
 * <p>See docs/RESEARCH.md for how the reference machine, the rider-input handling and the model
 * geometry were established, and README.md for setup.
 */
public final class ScooterMC extends JavaPlugin {

    private ScooterConfig config;
    private Lang lang;
    private Assembly assembly;
    private ScooterItems items;
    private Hud hud;
    private ScooterService service;
    private ResourcePackService resourcePack;
    private BlockData litBlockData;

    @Override
    public void onEnable() {
        Keys.init(this);
        saveDefaultConfig();

        try {
            assembly = Assembly.load(this);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not read the scooter assembly description; "
                    + "the plugin cannot run without it", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        config = new ScooterConfig(getConfig(), getLogger());
        lang = new Lang(this);
        lang.load(config.language);
        Sfx.load(this);
        items = new ScooterItems(config, lang);
        hud = new Hud(config, lang);
        litBlockData = createLitBlockData();

        resourcePack = new ResourcePackService(this, config, lang);
        resourcePack.start();

        service = new ScooterService(this, new ScooterStore(getDataFolder(), config, getLogger()));
        service.start();

        getServer().getPluginManager().registerEvents(new ScooterListeners(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ScooterCommand.build(this),
                        "Manage and ride electric scooters",
                        java.util.List.of("sc", "scootermc")));

        // A reload while players are online must not leave anyone frozen.
        for (Player player : getServer().getOnlinePlayers()) {
            RideSession.scrub(player);
        }

        getLogger().info("ScooterMC enabled - " + service.count() + " scooter(s), "
                + lang.available().size() + " language(s)");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            for (RideSession session : service.rides()) {
                session.clearFakeLight();
            }
            service.shutdown();
        }
        if (resourcePack != null) {
            resourcePack.shutdown();
        }
        if (hud != null) {
            hud.hideAll();
        }
        // Whatever happened, nobody stays frozen.
        for (Player player : Bukkit.getOnlinePlayers()) {
            RideSession.scrub(player);
        }
    }

    /** Re-reads config.yml and the language files. Scooters and rides are left alone. */
    public void reload() {
        reloadConfig();
        config = new ScooterConfig(getConfig(), getLogger());
        lang.load(config.language);
        items = new ScooterItems(config, lang);
        hud = new Hud(config, lang);
        litBlockData = createLitBlockData();
        getLogger().info("Configuration reloaded");
    }

    /**
     * A full-strength light block, sent to clients only so the headlight can never modify or grief
     * the world. Null if the server does not know the block, which should not happen on 1.21.
     */
    private BlockData createLitBlockData() {
        try {
            return Bukkit.createBlockData(Material.LIGHT, "[level=15]");
        } catch (IllegalArgumentException e) {
            getLogger().warning("Could not build a light block for the headlight: " + e.getMessage());
            return null;
        }
    }

    public ScooterConfig config() {
        return config;
    }

    public Lang lang() {
        return lang;
    }

    public Assembly assembly() {
        return assembly;
    }

    public ScooterItems items() {
        return items;
    }

    public Hud hud() {
        return hud;
    }

    public ScooterService service() {
        return service;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    public BlockData litBlockData() {
        return litBlockData;
    }
}
