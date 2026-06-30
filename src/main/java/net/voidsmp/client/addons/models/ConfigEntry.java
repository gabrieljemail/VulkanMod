package net.voidsmp.client.addons.models;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A single configurable value belonging to an {@link Addon}. Concrete and
 * generic: create one with a default value, optionally describe it and (for
 * numeric types) give it a range, then listen for changes. The ClickGUI reads
 * {@link #type} (and {@link #min}/{@link #max}) to decide how to render it —
 * a toggle for {@link ConfigEntryType#BOOLEAN}, a slider for numerics, etc.
 *
 * <p>Fluent setters return {@code this} so an add-on can declare entries inline.
 */
public class ConfigEntry<T> {
    public final String id;
    public final ConfigEntryType type;

    public String friendlyName;
    public String description;

    private final T defaultValue;
    private T value;

    // Inclusive bounds for numeric entries; null for non-numeric / unbounded.
    private Double min;
    private Double max;

    private final List<Consumer<T>> listeners = new ArrayList<>();

    public ConfigEntry(String id, ConfigEntryType type, T defaultValue) {
        this.id = id;
        this.type = type;
        this.friendlyName = id;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        value = newValue;
        for (Consumer<T> listener : listeners) {
            listener.accept(newValue);
        }
    }

    public T getDefault() {
        return defaultValue;
    }

    public void reset() {
        set(defaultValue);
    }

    /** Registers a change listener and fires it once with the current value. */
    public ConfigEntry<T> onChange(Consumer<T> callback) {
        listeners.add(callback);
        callback.accept(value);
        return this;
    }

    public ConfigEntry<T> describe(String friendlyName, String description) {
        this.friendlyName = friendlyName;
        this.description = description;
        return this;
    }

    public ConfigEntry<T> range(double min, double max) {
        this.min = min;
        this.max = max;
        return this;
    }

    public Double min() {
        return min;
    }

    public Double max() {
        return max;
    }
}
