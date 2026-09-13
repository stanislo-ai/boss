package dev.scootermc.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Every namespaced key the plugin owns, created once. */
public final class Keys {

    private static NamespacedKey scooterId;
    private static NamespacedKey partName;
    private static NamespacedKey itemColor;
    private static NamespacedKey itemBattery;
    private static NamespacedKey itemOdometer;
    private static NamespacedKey itemLocked;
    private static NamespacedKey playerLanguage;
    private static NamespacedKey safeFall;
    private static NamespacedKey fallDamage;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        scooterId = new NamespacedKey(plugin, "scooter_id");
        partName = new NamespacedKey(plugin, "part");
        itemColor = new NamespacedKey(plugin, "color");
        itemBattery = new NamespacedKey(plugin, "battery_wh");
        itemOdometer = new NamespacedKey(plugin, "odometer_m");
        itemLocked = new NamespacedKey(plugin, "locked");
        playerLanguage = new NamespacedKey(plugin, "language");
        safeFall = new NamespacedKey(plugin, "safe_fall");
        fallDamage = new NamespacedKey(plugin, "fall_damage");
    }

    public static NamespacedKey scooterId() {
        return scooterId;
    }

    public static NamespacedKey partName() {
        return partName;
    }

    public static NamespacedKey itemColor() {
        return itemColor;
    }

    public static NamespacedKey itemBattery() {
        return itemBattery;
    }

    public static NamespacedKey itemOdometer() {
        return itemOdometer;
    }

    public static NamespacedKey itemLocked() {
        return itemLocked;
    }

    public static NamespacedKey playerLanguage() {
        return playerLanguage;
    }

    public static NamespacedKey safeFall() {
        return safeFall;
    }

    public static NamespacedKey fallDamage() {
        return fallDamage;
    }
}
