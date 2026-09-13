package dev.scootermc.lang;

import dev.scootermc.util.Keys;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Message catalogue with a server default language and a per-player override.
 *
 * <p>Language files are shipped in the jar and copied into {@code plugins/ScooterMC/lang/} on first
 * run so server owners can edit them. Text is MiniMessage, so operators get full colour and
 * formatting control without the plugin hard-coding any of it.
 */
public final class Lang {

    public static final String DEFAULT = "en";

    private final Plugin plugin;
    private final Logger log;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, Map<String, String>> catalogues = new LinkedHashMap<>();
    private String serverLanguage = DEFAULT;

    public Lang(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /** Bundled languages; each must exist as {@code lang/<code>.yml} in the jar. */
    public static final Set<String> BUNDLED = new LinkedHashSet<>(java.util.List.of("en", "pl"));

    public void load(String serverLanguage) {
        catalogues.clear();
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warning("Could not create " + dir);
        }
        for (String code : BUNDLED) {
            saveDefaultIfMissing(dir, code);
        }
        // Load every yml present, so operators can drop in their own translations.
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String code = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
                catalogues.put(code, flatten(YamlConfiguration.loadConfiguration(f)));
            }
        }
        // Always keep the in-jar English as the ultimate fallback, even if the file was mangled.
        Map<String, String> jarEnglish = loadFromJar(DEFAULT);
        catalogues.computeIfAbsent(DEFAULT, k -> jarEnglish);
        for (Map.Entry<String, String> e : jarEnglish.entrySet()) {
            catalogues.get(DEFAULT).putIfAbsent(e.getKey(), e.getValue());
        }

        String want = serverLanguage == null ? DEFAULT : serverLanguage.toLowerCase(Locale.ROOT);
        if (!catalogues.containsKey(want)) {
            log.warning("Language '" + want + "' has no lang/" + want + ".yml; using " + DEFAULT);
            want = DEFAULT;
        }
        this.serverLanguage = want;
        log.info("Loaded " + catalogues.size() + " language(s); server default is '" + want + "'");
    }

    public String serverLanguage() {
        return serverLanguage;
    }

    public Set<String> available() {
        return catalogues.keySet();
    }

    public boolean has(String code) {
        return code != null && catalogues.containsKey(code.toLowerCase(Locale.ROOT));
    }

    /** The language a specific receiver should see: their own override, else the server default. */
    public String languageOf(CommandSender sender) {
        if (sender instanceof Player p) {
            String own = p.getPersistentDataContainer().get(Keys.playerLanguage(), PersistentDataType.STRING);
            if (own != null && catalogues.containsKey(own)) {
                return own;
            }
        }
        return serverLanguage;
    }

    public void setLanguage(Player player, String code) {
        if (code == null) {
            player.getPersistentDataContainer().remove(Keys.playerLanguage());
        } else {
            player.getPersistentDataContainer()
                    .set(Keys.playerLanguage(), PersistentDataType.STRING, code.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Renders a message.
     *
     * @param placeholders alternating name/value pairs, usable as {@code <name>} in the template
     */
    public Component get(CommandSender who, String key, Object... placeholders) {
        return render(languageOf(who), key, placeholders);
    }

    public Component render(String language, String key, Object... placeholders) {
        String template = lookup(language, key);
        if (template == null) {
            return Component.text(key);
        }
        return mm.deserialize(template, resolvers(placeholders));
    }

    /** Plain unformatted string, for places that cannot take a Component (log lines, item NBT). */
    public String raw(String language, String key) {
        String t = lookup(language, key);
        return t == null ? key : t;
    }

    public void send(CommandSender who, String key, Object... placeholders) {
        Component c = get(who, key, placeholders);
        String prefix = lookup(languageOf(who), "prefix");
        who.sendMessage(prefix == null ? c : mm.deserialize(prefix).append(c));
    }

    public void sendRaw(CommandSender who, String key, Object... placeholders) {
        who.sendMessage(get(who, key, placeholders));
    }

    private static TagResolver resolvers(Object... placeholders) {
        if (placeholders.length == 0) {
            return TagResolver.empty();
        }
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be name/value pairs");
        }
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 0; i < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            if (value instanceof Component c) {
                b.resolver(Placeholder.component(name, c));
            } else {
                b.resolver(Placeholder.unparsed(name, String.valueOf(value)));
            }
        }
        return b.build();
    }

    private String lookup(String language, String key) {
        Map<String, String> cat = catalogues.get(language);
        String v = cat == null ? null : cat.get(key);
        if (v != null) {
            return v;
        }
        Map<String, String> fallback = catalogues.get(DEFAULT);
        return fallback == null ? null : fallback.get(key);
    }

    private void saveDefaultIfMissing(File dir, String code) {
        File out = new File(dir, code + ".yml");
        if (out.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) {
                log.warning("lang/" + code + ".yml is missing from the jar");
                return;
            }
            java.nio.file.Files.copy(in, out.toPath());
        } catch (IOException e) {
            log.warning("Could not write " + out + ": " + e.getMessage());
        }
    }

    private Map<String, String> loadFromJar(String code) {
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in == null) {
                return new LinkedHashMap<>();
            }
            return flatten(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    private static Map<String, String> flatten(YamlConfiguration yaml) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                Object v = yaml.get(key);
                if (v != null) {
                    out.put(key, String.valueOf(v));
                }
            }
        }
        return out;
    }
}
