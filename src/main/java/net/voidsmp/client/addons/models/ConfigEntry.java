package net.voidsmp.client.addons.models;

import java.util.Collection;
import java.util.function.Consumer;

public abstract class ConfigEntry<T> {
    public String id;
    public String friendlyName;
    public String description;
    public ConfigEntryType type;

    private T value;
    private Collection<Consumer<T>> listeners;

    public T get() { return value; }
    public void set(T newValue) {
        value = newValue;
        for (Consumer<T> consumer : listeners) {
            consumer.accept(newValue);
        }
    }
    public void onChange(Consumer<T> callback) {
        listeners.add(callback);
    }
}
