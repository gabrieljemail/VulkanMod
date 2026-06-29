/*

Addons are basically mods that can be toggled and
configured through the ClickGUI in a centralized way.

*/

package net.voidsmp.client.addons.models;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public abstract class Addon {
    // Instance fields: each add-on carries its own identity/state. The tile
    // renderer and ClickGUI rely on these being per-add-on (not static).
    public String id;
    public String name;
    public ConfigEntry[] config;

    protected boolean enabled;

    // Tile sprites — assets/voidclient/textures/gui/sprites/widget/<name>.png.
    // Nine-sliced, so they scale to any tile size, and a resource pack can
    // re-skin every add-on tile by shipping the same paths.
    private static final Identifier TILE = Identifier.parse("voidclient:widget/tile");
    private static final Identifier TILE_HOVERED = Identifier.parse("voidclient:widget/tile_highlighted");

    protected abstract void onRegister(Consumer<Addon> callback);
    protected abstract void onConfigUpdate(Consumer<ConfigEntry> change);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Draws this add-on as a tile/card in the ClickGUI. Sprite-styled and
     * batched through the GUI atlas (no extra draw calls), consistent with the
     * rest of the menu. Add-ons may override this to customise their tile, but
     * the default covers the common case: a panel, the add-on name, and an
     * on/off status indicator.
     */
    public void renderTile(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        Identifier sprite = hovered ? TILE_HOVERED : TILE;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);

        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, name == null ? id : name, x + 6, y + 6, 0xFFFFFFFF);

        Component status = Component.literal(enabled ? "ON" : "OFF");
        int statusColor = enabled ? 0xFF8B5CF6 : 0xFF6B6B6B;
        int statusWidth = font.width(status);
        graphics.drawString(font, status, x + width - statusWidth - 6, y + height - 12, statusColor);
    }
}
