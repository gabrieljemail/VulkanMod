package net.voidsmp.client.addons.config;

import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AddonConfigStorageTest {

    @TempDir
    Path tempDir;

    static class FakeAddon extends Addon {
        FakeAddon() {
            this.id = "voidclient:fake";
            this.name = "Fake";
            this.config = new ConfigEntry[]{
                new ConfigEntry<>("enabled", ConfigEntryType.BOOLEAN, false),
                new ConfigEntry<>("intensity", ConfigEntryType.FLOAT, 1.0f).range(0.0, 2.0)
            };
        }

        @Override
        protected void onRegister(Consumer<Addon> callback) {
        }

        @Override
        protected void onConfigUpdate(Consumer<ConfigEntry> change) {
        }
    }

    @SuppressWarnings("unchecked")
    private static ConfigEntry<Boolean> enabledEntry(Addon addon) {
        return (ConfigEntry<Boolean>) addon.config[0];
    }

    @SuppressWarnings("unchecked")
    private static ConfigEntry<Float> intensityEntry(Addon addon) {
        return (ConfigEntry<Float>) addon.config[1];
    }

    @BeforeEach
    void setUp() {
        AddonConfigStorage.setConfigDir(tempDir);
    }

    @Test
    void saveThenLoadRoundTripsValues() {
        FakeAddon addon = new FakeAddon();
        enabledEntry(addon).set(true);
        intensityEntry(addon).set(1.5f);

        AddonConfigStorage.save(addon);

        FakeAddon reloaded = new FakeAddon();
        AddonConfigStorage.load(reloaded);

        assertEquals(true, enabledEntry(reloaded).get());
        assertEquals(1.5f, intensityEntry(reloaded).get());
    }

    @Test
    void loadWithNoFileOnDiskLeavesDefaults() {
        FakeAddon addon = new FakeAddon();
        AddonConfigStorage.load(addon);

        assertFalse(enabledEntry(addon).get());
        assertEquals(1.0f, intensityEntry(addon).get());
    }

    @Test
    void saveThenLoadRoundTripsAddonEnabledState() {
        FakeAddon addon = new FakeAddon();
        addon.setEnabled(true);

        AddonConfigStorage.save(addon);

        FakeAddon reloaded = new FakeAddon();
        assertFalse(reloaded.isEnabled());
        AddonConfigStorage.load(reloaded);

        assertEquals(true, reloaded.isEnabled());
    }

    @Test
    void loadIgnoresKeysNotPresentInCurrentConfig() {
        FakeAddon addon = new FakeAddon();
        enabledEntry(addon).set(true);
        AddonConfigStorage.save(addon);

        // A future version of the addon might drop a key; loading an addon
        // whose config array no longer has every key that's on disk must not
        // throw.
        FakeAddon reloaded = new FakeAddon();
        AddonConfigStorage.load(reloaded);
        assertEquals(true, enabledEntry(reloaded).get());
    }
}
