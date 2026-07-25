package net.voidsmp.client.addons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;

import java.util.function.Consumer;

/**
 * Forces every light sample to full brightness, removing darkness. Implemented
 * by overriding the packed light coordinate (see LightDataAccessMixin for
 * terrain and LightColorMixin for entities/items) rather than touching the
 * world light engine, so it costs nothing per frame and doesn't corrupt
 * lighting data.
 */
public class Fullbright extends Addon {

    // Read from chunk-meshing / entity-render hot paths, so it's a plain static
    // flag instead of a registry lookup.
    public static boolean ENABLED = false;

    // Gamma value restored when the add-on is turned back off.
    private double previousGamma;

    // setEnabled(true) can run from AddonConfigStorage.load() -> AddonRegistry.register(),
    // which Fabric invokes from inside Minecraft's own constructor -- before
    // Minecraft.options exists. When that happens this flag is set instead of
    // touching options, and CLIENT_STARTED (registered below, fires exactly
    // once, after options definitely exists) applies it.
    private boolean gammaApplyPending;

    public Fullbright() {
        this.id = "voidclient:fullbright";
        this.name = "Fullbright";
        this.config = new ConfigEntry[0];

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            if (gammaApplyPending) {
                applyGamma(this.enabled);
            }
        });
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        ENABLED = enabled;
        applyGamma(enabled);
    }

    private void applyGamma(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            gammaApplyPending = true;
            return;
        }
        gammaApplyPending = false;

        if (enabled) {
            this.previousGamma = mc.options.gamma().get();
            mc.options.gamma().set(15.0);
        } else {
            mc.options.gamma().set(this.previousGamma);
        }

        // Terrain light is baked into chunk meshes, so existing chunks must be
        // re-meshed for the change to show. (Runs on the render thread — toggles
        // come from the ClickGUI click handler, or from CLIENT_STARTED above.)
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
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
