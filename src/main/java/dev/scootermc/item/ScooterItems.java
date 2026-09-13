package dev.scootermc.item;

import dev.scootermc.config.ScooterConfig;
import dev.scootermc.lang.Lang;
import dev.scootermc.model.Scooter;
import dev.scootermc.model.ScooterColor;
import dev.scootermc.util.Keys;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Turns scooters into carryable items and back.
 *
 * <p>The look comes entirely from the 1.21.4+ {@code minecraft:item_model} component, so the base
 * material is irrelevant to the appearance and stays configurable. Battery charge, odometer, paint
 * and lock state ride along in the item's persistent data, which is why picking a scooter up and
 * putting it down again does not reset it.
 */
public final class ScooterItems {

    private final ScooterConfig config;
    private final Lang lang;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ScooterItems(ScooterConfig config, Lang lang) {
        this.config = config;
        this.lang = lang;
    }

    /** Bare stack used inside a Display entity - no name, no lore, just the model. */
    public ItemStack displayStack(String itemModel) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(itemModel));
        stack.setItemMeta(meta);
        return stack;
    }

    /** A brand-new scooter item with a full battery. */
    public ItemStack create(ScooterColor color, String language) {
        return create(color, config.batteryCapacityWh, 0.0, false, language);
    }

    /** The carryable form of an existing scooter, preserving its state. */
    public ItemStack from(Scooter scooter, String language) {
        return create(scooter.color(), scooter.batteryWh(), scooter.odometerMetres(),
                scooter.locked(), language);
    }

    public ItemStack create(ScooterColor color, double batteryWh, double odometerMetres,
                            boolean locked, String language) {
        ItemStack stack = new ItemStack(config.baseMaterial);
        ItemMeta meta = stack.getItemMeta();

        meta.setItemModel(NamespacedKey.fromString("scootermc:scooter_" + color.id()));
        meta.setMaxStackSize(1);
        meta.displayName(mm.deserialize(lang.raw(language, "item.name"),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                                "color", lang.raw(language, "color." + color.id())))
                .decoration(TextDecoration.ITALIC, false));

        double percent = config.batteryCapacityWh <= 0 ? 0
                : Math.max(0, Math.min(100, batteryWh / config.batteryCapacityWh * 100.0));
        List<Component> lore = new ArrayList<>();
        for (String line : lang.raw(language, "item.lore").split("\\n")) {
            lore.add(mm.deserialize(line, tags(percent, odometerMetres, locked, color, language))
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.itemColor(), PersistentDataType.STRING, color.id());
        pdc.set(Keys.itemBattery(), PersistentDataType.DOUBLE, batteryWh);
        pdc.set(Keys.itemOdometer(), PersistentDataType.DOUBLE, odometerMetres);
        pdc.set(Keys.itemLocked(), PersistentDataType.BYTE, (byte) (locked ? 1 : 0));

        stack.setItemMeta(meta);
        return stack;
    }

    private net.kyori.adventure.text.minimessage.tag.resolver.TagResolver tags(
            double percent, double odometerMetres, boolean locked, ScooterColor color,
            String language) {
        var b = net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.builder();
        b.resolver(unparsed("battery", String.format(Locale.ROOT, "%.0f", percent)));
        b.resolver(unparsed("range", String.format(Locale.ROOT, "%.1f", estimatedRangeKm(percent))));
        b.resolver(unparsed("odometer", String.format(Locale.ROOT, "%.2f", odometerMetres / 1000.0)));
        b.resolver(unparsed("color", lang.raw(language, "color." + color.id())));
        b.resolver(unparsed("locked", lang.raw(language, locked ? "state.locked" : "state.unlocked")));
        return b.build();
    }

    private static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver unparsed(
            String key, String value) {
        return net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(key, value);
    }

    private double estimatedRangeKm(double percent) {
        double wh = config.batteryCapacityWh * percent / 100.0;
        double perKm = config.tuning(config.defaultMode).whPerKm();
        return perKm <= 0 ? 0 : wh / perKm;
    }

    // ------------------------------------------------------------------ reading

    public boolean isScooter(ItemStack stack) {
        if (stack == null || stack.getType() != config.baseMaterial || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(Keys.itemColor(),
                PersistentDataType.STRING);
    }

    public ScooterColor colorOf(ItemStack stack) {
        if (!isScooter(stack)) {
            return ScooterColor.DEFAULT;
        }
        return ScooterColor.byId(stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.itemColor(), PersistentDataType.STRING), ScooterColor.DEFAULT);
    }

    public double batteryOf(ItemStack stack) {
        if (!isScooter(stack)) {
            return config.batteryCapacityWh;
        }
        Double v = stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.itemBattery(), PersistentDataType.DOUBLE);
        return v == null ? config.batteryCapacityWh
                : Math.max(0, Math.min(config.batteryCapacityWh, v));
    }

    public double odometerOf(ItemStack stack) {
        if (!isScooter(stack)) {
            return 0;
        }
        Double v = stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.itemOdometer(), PersistentDataType.DOUBLE);
        return v == null ? 0 : Math.max(0, v);
    }

    public boolean lockedOf(ItemStack stack) {
        if (!isScooter(stack)) {
            return false;
        }
        Byte v = stack.getItemMeta().getPersistentDataContainer()
                .get(Keys.itemLocked(), PersistentDataType.BYTE);
        return v != null && v != 0;
    }
}
