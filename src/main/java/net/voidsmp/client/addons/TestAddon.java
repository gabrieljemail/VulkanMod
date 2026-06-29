package net.voidsmp.client.addons;

import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;

import java.util.function.Consumer;

/**
 * Throwaway add-on used to exercise the ClickGUI tile grid before the real
 * add-ons (and the ConfigEntry system) exist.
 */
public class TestAddon extends Addon {

    public TestAddon() {
        this.id = "voidclient:test";
        this.name = "Test Addon";
        this.config = new ConfigEntry[0];
    }

    @Override
    protected void onRegister(Consumer<Addon> callback) {
        // Nothing to register yet — hook for sub-add-ons / listeners later.
    }

    @Override
    protected void onConfigUpdate(Consumer<ConfigEntry> change) {
        // No config entries yet.
    }
}
