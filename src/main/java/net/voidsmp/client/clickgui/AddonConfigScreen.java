package net.voidsmp.client.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;

/**
 * Generic, auto-built config screen for any add-on: one row per
 * {@link ConfigEntry}, widget chosen by {@link ConfigEntryType}. No add-on
 * needs its own screen class. Opened by right-clicking an {@link AddonTile}.
 */
public class AddonConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 200;

    private final Addon addon;
    private final Screen parent;

    public AddonConfigScreen(Addon addon, Screen parent) {
        super(Component.literal((addon.name == null ? addon.id : addon.name) + " Settings"));
        this.addon = addon;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int startY = 32;
        int x = (this.width - ROW_WIDTH) / 2;

        if (addon.config.length == 0) {
            return; // renders a "no settings" message instead, see render()
        }

        for (int i = 0; i < addon.config.length; i++) {
            ConfigEntry<?> entry = addon.config[i];
            int y = startY + i * (ROW_HEIGHT + 4);

            if (entry.type == ConfigEntryType.BOOLEAN) {
                addRenderableWidget(booleanWidget(x, y, entry));
            } else {
                addRenderableWidget(rangeWidget(x, y, entry));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private SpriteButton booleanWidget(int x, int y, ConfigEntry<?> entry) {
        ConfigEntry<Boolean> boolEntry = (ConfigEntry<Boolean>) entry;
        return new SpriteButton(x, y, ROW_WIDTH, ROW_HEIGHT,
            label(entry, boolEntry.get() ? "ON" : "OFF"),
            () -> boolEntry.set(!boolEntry.get()));
    }

    @SuppressWarnings("unchecked")
    private AbstractSliderButton rangeWidget(int x, int y, ConfigEntry<?> entry) {
        ConfigEntry<Number> numEntry = (ConfigEntry<Number>) entry;
        double min = numEntry.min() != null ? numEntry.min() : 0.0;
        double max = numEntry.max() != null ? numEntry.max() : 100.0;
        double current = numEntry.get().doubleValue();
        double progress = max > min ? (current - min) / (max - min) : 0.0;

        return new AbstractSliderButton(x, y, ROW_WIDTH, ROW_HEIGHT, label(entry, String.valueOf(numEntry.get())), progress) {
            @Override
            protected void updateMessage() {
                double value = min + (max - min) * this.value;
                setMessage(label(entry, formatValue(entry.type, value)));
            }

            @Override
            protected void applyValue() {
                double value = min + (max - min) * this.value;
                numEntry.set(convert(entry.type, value));
            }
        };
    }

    private static Component label(ConfigEntry<?> entry, String valueText) {
        String name = entry.friendlyName != null ? entry.friendlyName : entry.id;
        return Component.literal(name + ": " + valueText);
    }

    private static String formatValue(ConfigEntryType type, double value) {
        return switch (type) {
            case FLOAT, UFLOAT -> String.format("%.2f", value);
            default -> String.valueOf((long) value);
        };
    }

    private static Number convert(ConfigEntryType type, double value) {
        return switch (type) {
            case FLOAT, UFLOAT -> (float) value;
            case SHORT, USHORT -> (short) value;
            case LONG, ULONG -> (long) value;
            case BYTE, UBYTE -> (byte) value;
            default -> (int) value;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (addon.config.length == 0) {
            graphics.drawCenteredString(this.font, "This add-on has no settings.", this.width / 2, 40, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
