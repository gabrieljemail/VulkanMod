package net.voidsmp.client.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.voidsmp.client.addons.config.AddonConfigStorage;
import net.voidsmp.client.addons.models.Addon;
import org.lwjgl.glfw.GLFW;

/**
 * Clickable wrapper around an {@link Addon}'s tile. Left-click toggles the
 * add-on on/off; right-click opens its config screen. The visuals are
 * delegated to {@link Addon#renderTile} so an add-on stays in control of how
 * its own tile looks.
 */
public class AddonTile extends AbstractButton {

    private final Addon addon;

    public AddonTile(int x, int y, int width, int height, Addon addon) {
        super(x, y, width, height, Component.literal(addon.name == null ? addon.id : addon.name));
        this.addon = addon;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || info.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (input instanceof MouseButtonEvent event && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AddonConfigScreen(addon, minecraft.screen));
        } else {
            addon.toggle();
            AddonConfigStorage.save(addon);
        }
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
