package dev.scootermc.pack;

import com.sun.net.httpserver.HttpServer;
import dev.scootermc.config.ScooterConfig;
import dev.scootermc.lang.Lang;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Delivers the resource pack.
 *
 * <p>The pack ships inside the plugin jar and is written out to the data folder on first run, so a
 * server owner can either host it themselves or let the plugin serve it from a small built-in HTTP
 * endpoint. Nothing is served unless it is explicitly switched on in the config.
 */
public final class ResourcePackService {

    private static final String PACK_RESOURCE = "resourcepack.zip";
    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("scootermc-pack".getBytes());

    private final Plugin plugin;
    private final ScooterConfig config;
    private final Lang lang;
    private final Logger log;

    private File packFile;
    private byte[] packBytes;
    private String sha1;
    private String url;
    private HttpServer http;

    public ResourcePackService(Plugin plugin, ScooterConfig config, Lang lang) {
        this.plugin = plugin;
        this.config = config;
        this.lang = lang;
        this.log = plugin.getLogger();
    }

    public void start() {
        extractPack();
        if (!config.packEnabled) {
            return;
        }
        if (config.packServerEnabled) {
            startHttpServer();
        } else if (!config.packUrl.isBlank()) {
            url = config.packUrl;
            sha1 = config.packSha1.isBlank() ? sha1 : config.packSha1.toLowerCase(java.util.Locale.ROOT);
            if (sha1 == null || sha1.isBlank()) {
                log.warning("resource-pack.sha1 is empty. Clients will re-download the pack every "
                        + "time they join; set it to the SHA-1 of the hosted file.");
            }
        } else {
            log.warning("resource-pack.enabled is true but no url is set and the built-in server "
                    + "is off, so no pack will be sent.");
        }
        if (url != null) {
            log.info("Resource pack will be offered from " + url);
        }
    }

    public void shutdown() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    /** True when there is something to send. */
    public boolean available() {
        return url != null;
    }

    public String url() {
        return url;
    }

    public String sha1() {
        return sha1;
    }

    public File packFile() {
        return packFile;
    }

    public void offerOnJoin(Player player) {
        if (config.packEnabled && config.packPromptOnJoin) {
            send(player);
        }
    }

    public boolean send(Player player) {
        if (url == null) {
            return false;
        }
        player.setResourcePack(PACK_UUID, url, sha1 == null ? "" : sha1,
                lang.get(player, "pack.prompt"), config.packRequired);
        return true;
    }

    // ------------------------------------------------------------------ internals

    /** Copies the bundled pack into the data folder, refreshing it when the jar's copy changes. */
    private void extractPack() {
        packFile = new File(plugin.getDataFolder(), PACK_RESOURCE);
        try (InputStream in = plugin.getResource(PACK_RESOURCE)) {
            if (in == null) {
                log.warning("No " + PACK_RESOURCE + " inside the plugin jar; the resource pack "
                        + "features are unavailable. Build with tools/gen_models.py first.");
                return;
            }
            byte[] bundled = in.readAllBytes();
            if (!packFile.exists() || packFile.length() != bundled.length) {
                plugin.getDataFolder().mkdirs();
                Files.write(packFile.toPath(), bundled);
                log.info("Wrote " + packFile.getName() + " (" + bundled.length / 1024 + " KiB)");
            }
            packBytes = Files.readAllBytes(packFile.toPath());
            sha1 = hex(MessageDigest.getInstance("SHA-1").digest(packBytes));
            log.info("Resource pack SHA-1 is " + sha1);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not prepare the resource pack", e);
        }
    }

    private void startHttpServer() {
        if (packBytes == null) {
            log.warning("The built-in resource pack server has nothing to serve.");
            return;
        }
        try {
            http = HttpServer.create(new InetSocketAddress(config.packServerPort), 0);
            http.createContext("/scootermc.zip", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, packBytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(packBytes);
                } catch (IOException ignored) {
                    // client went away mid-download; nothing useful to do
                }
                exchange.close();
            });
            http.setExecutor(null);
            http.start();
            String host = config.packServerAddress.isBlank()
                    ? plugin.getServer().getIp().isBlank() ? "127.0.0.1" : plugin.getServer().getIp()
                    : config.packServerAddress;
            url = "http://" + host + ":" + config.packServerPort + "/scootermc.zip";
            if (config.packServerAddress.isBlank()) {
                log.warning("resource-pack.built-in-server.public-address is empty, so the pack URL "
                        + "was guessed as " + url + ". Set it to a host your players can reach.");
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not start the resource pack server on port "
                    + config.packServerPort, e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
