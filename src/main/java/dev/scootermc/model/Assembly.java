package dev.scootermc.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * The rig description emitted by tools/gen_models.py.
 *
 * <p>Scales, pivots and the wheel geometry all come from the same generator that produced the
 * models, so the Java rig cannot drift away from the resource pack. Editing the scooter's shape is
 * a matter of re-running the generator; nothing here needs touching.
 */
public final class Assembly {

    /** One renderable piece of the scooter. */
    public record Part(String key, double scale, double pivotX, double pivotY, double pivotZ,
                       boolean tinted, boolean steers, boolean rolls, String itemModelTemplate) {

        /** Resource-pack item model id for a given paint colour. */
        public String itemModel(ScooterColor color) {
            return tinted ? itemModelTemplate.formatted(color.id()) : itemModelTemplate;
        }
    }

    private final Map<String, Part> parts;
    private final List<String> rigParts;
    private final String wheelPart;
    private final String fullPart;

    // geometry, converted from millimetres to blocks once
    private final double tyreRadius;
    private final double axleY;
    private final double frontAxleZ;
    private final double rearAxleZ;
    private final double steerAxisZ;
    private final double deckTop;
    private final double deckCentreZ;
    private final double length;
    private final double width;
    private final double totalHeight;

    private Assembly(JsonObject root) {
        JsonObject geo = root.getAsJsonObject("geometry");
        tyreRadius = mm(geo, "tyre_radius_mm");
        axleY = mm(geo, "axle_y_mm");
        frontAxleZ = mm(geo, "front_axle_z_mm");
        rearAxleZ = mm(geo, "rear_axle_z_mm");
        steerAxisZ = mm(geo, "steer_axis_z_mm");
        deckTop = mm(geo, "deck_top_mm");
        deckCentreZ = (mm(geo, "deck_z0_mm") + mm(geo, "deck_z1_mm")) / 2.0;
        length = mm(geo, "length_mm");
        width = mm(geo, "width_mm");
        totalHeight = mm(geo, "total_height_mm");

        Map<String, Part> map = new LinkedHashMap<>();
        JsonObject partsJson = root.getAsJsonObject("parts");
        for (String key : partsJson.keySet()) {
            JsonObject p = partsJson.getAsJsonObject(key);
            JsonArray pivot = p.getAsJsonArray("pivot_mm");
            map.put(key, new Part(
                    key,
                    p.get("scale").getAsDouble(),
                    pivot.get(0).getAsDouble() / 1000.0,
                    pivot.get(1).getAsDouble() / 1000.0,
                    pivot.get(2).getAsDouble() / 1000.0,
                    p.get("tinted").getAsBoolean(),
                    p.get("steer").getAsBoolean(),
                    p.get("roll").getAsBoolean(),
                    p.get("item_model").getAsString()));
        }
        this.parts = Map.copyOf(map);

        List<String> rig = new ArrayList<>();
        for (var e : root.getAsJsonArray("rig_parts")) {
            rig.add(e.getAsString());
        }
        this.rigParts = List.copyOf(rig);
        this.wheelPart = root.get("wheel_part").getAsString();
        this.fullPart = root.get("full_part").getAsString();

        for (String key : rigParts) {
            require(key);
        }
        require(wheelPart);
        require(fullPart);
    }

    public static Assembly load(Plugin plugin) throws IOException {
        try (InputStream in = plugin.getResource("assembly.json")) {
            if (in == null) {
                throw new IOException("assembly.json is missing from the plugin jar");
            }
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return new Assembly(root);
        } catch (RuntimeException e) {
            throw new IOException("assembly.json is malformed: " + e, e);
        }
    }

    private static double mm(JsonObject o, String key) {
        return o.get(key).getAsDouble() / 1000.0;
    }

    private void require(String key) {
        if (!parts.containsKey(key)) {
            throw new IllegalStateException("assembly.json references unknown part '" + key + "'");
        }
    }

    public Part part(String key) {
        Part p = parts.get(key);
        if (p == null) {
            throw new IllegalArgumentException("no such part: " + key);
        }
        return p;
    }

    /** Body panels, lamps and the dash - everything but the two wheels. */
    public List<String> rigParts() {
        return rigParts;
    }

    public Part wheel() {
        return part(wheelPart);
    }

    /** The single-model scooter used for the inventory item and for parked machines. */
    public Part full() {
        return part(fullPart);
    }

    public double tyreRadius() {
        return tyreRadius;
    }

    public double axleY() {
        return axleY;
    }

    public double frontAxleZ() {
        return frontAxleZ;
    }

    public double rearAxleZ() {
        return rearAxleZ;
    }

    public double steerAxisZ() {
        return steerAxisZ;
    }

    public double deckTop() {
        return deckTop;
    }

    /** Where along the deck the rider stands, relative to the design origin. */
    public double deckCentreZ() {
        return deckCentreZ;
    }

    public double length() {
        return length;
    }

    public double width() {
        return width;
    }

    public double totalHeight() {
        return totalHeight;
    }
}
