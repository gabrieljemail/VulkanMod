package net.voidsmp.client.addons;

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

    public Fullbright() {
        this.id = "voidclient:fullbright";
        this.name = "Fullbright";
        this.config = new ConfigEntry[0];
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        ENABLED = enabled;

        // Terrain light is baked into chunk meshes, so existing chunks must be
        // re-meshed for the change to show. (Runs on the render thread — toggles
        // come from the ClickGUI click handler.)
        Minecraft mc = Minecraft.getInstance();
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
