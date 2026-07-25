package net.voidsmp.client.addons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists each add-on's {@link ConfigEntry} values to its own JSON file
 * under {@code config/VoidAddons/}, so settings survive a restart. Loading
 * and saving are explicit, one-shot operations — callers decide when they
 * run (e.g. once at register time, and on each config change thereafter);
 * this class holds no listeners itself and can't leak them.
 */
public final class AddonConfigStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configDir = Path.of("config", "VoidAddons");

    /**
     * Reserved key for the add-on's on/off state. This lives on {@link Addon}
     * as a plain {@code boolean} field (not a {@link ConfigEntry}), so it's
     * written/read separately from the {@code addon.config} loop below.
     */
    private static final String ENABLED_KEY = "__enabled";

    private AddonConfigStorage() {
    }

    /** Overrides where config files are read from/written to. Used by tests and by startup wiring. */
    public static void setConfigDir(Path dir) {
        configDir = dir;
    }

    private static Path fileFor(Addon addon) {
        return configDir.resolve(addon.id.replace(':', '_') + ".json");
    }

    public static void load(Addon addon) {
        Path file = fileFor(addon);
        if (!Files.exists(file)) {
            return;
        }

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read addon config: " + file, e);
        }

        if (json.has(ENABLED_KEY)) {
            addon.setEnabled(json.get(ENABLED_KEY).getAsBoolean());
        }

        for (ConfigEntry<?> entry : addon.config) {
            if (json.has(entry.id)) {
                applyValue(entry, json.get(entry.id));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyValue(ConfigEntry<T> entry, JsonElement element) {
        Object value = switch (entry.type) {
            case BOOLEAN -> element.getAsBoolean();
            case STRING -> element.getAsString();
            case FLOAT, UFLOAT -> element.getAsFloat();
            case SHORT, USHORT -> element.getAsShort();
            case INT, UINT, ENUM -> element.getAsInt();
            case LONG, ULONG -> element.getAsLong();
            case BYTE, UBYTE -> element.getAsByte();
        };
        entry.set((T) value);
    }

    public static void save(Addon addon) {
        JsonObject json = new JsonObject();
        json.addProperty(ENABLED_KEY, addon.isEnabled());
        for (ConfigEntry<?> entry : addon.config) {
            json.add(entry.id, GSON.toJsonTree(entry.get()));
        }

        Path file = fileFor(addon);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write addon config: " + file, e);
        }
    }
}
