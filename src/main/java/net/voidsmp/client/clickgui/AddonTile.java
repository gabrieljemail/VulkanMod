package net.voidsmp.client.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.voidsmp.client.addons.models.Addon;

/**
 * Clickable wrapper around an {@link Addon}'s tile. Clicking toggles the
 * add-on on/off; the visuals are delegated to {@link Addon#renderTile} so an
 * add-on stays in control of how its own tile looks.
 */
public class AddonTile extends AbstractButton {

    private final Addon addon;

    public AddonTile(int x, int y, int width, int height, Addon addon) {
        super(x, y, width, height, Component.literal(addon.name == null ? addon.id : addon.name));
        this.addon = addon;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        addon.toggle();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        addon.renderTile(graphics, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
