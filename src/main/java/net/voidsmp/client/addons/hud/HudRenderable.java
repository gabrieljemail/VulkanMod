package net.voidsmp.client.addons.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.voidsmp.client.addons.models.Addon;

import java.util.List;

/**
 * Opt-in capability for an {@link Addon} that wants to draw on the actual
 * game HUD (as opposed to {@link Addon#renderTile}, which only draws the
 * ClickGUI menu tile). Most add-ons have nothing to draw here, so this is a
 * separate interface rather than a method on {@code Addon} itself — only
 * {@code AddonRegistry.register()} needs to know an implementor exists,
 * via an {@code instanceof} check, and it must also be an {@code Addon}
 * (the registry looks the implementor up as one) for {@code isEnabled()}
 * to gate rendering.
 */
public interface HudRenderable {
    /** Stable id used to register this element with Fabric's HudElementRegistry. */
    Identifier hudElementId();

    HudAnchor hudAnchor();

    int hudOffsetX();

    int hudOffsetY();

    /**
     * Called once per frame while the owning add-on is enabled. Returns the
     * content to draw this frame — an empty list draws nothing. Framework
     * owns positioning; this only supplies content.
     */
    List<HudStackItem> renderHudStack(Minecraft mc);
}
