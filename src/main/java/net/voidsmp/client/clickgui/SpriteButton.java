package net.voidsmp.client.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A button styled entirely from GUI atlas sprites instead of MCEF/HTML or a
 * custom render target. The sprites live in the vanilla GUI atlas, so they're
 * batched with every other GUI draw (no extra draw calls, no off-screen
 * framebuffer) and can be re-skinned by any resource pack that ships the same
 * sprite paths under {@code assets/voidclient/textures/gui/sprites/}.
 */
public class SpriteButton extends AbstractButton {

    // Identifiers resolve to assets/voidclient/textures/gui/sprites/<path>.png
    private static final WidgetSprites SPRITES = new WidgetSprites(
        Identifier.parse("voidclient:widget/button"),             // enabled
        Identifier.parse("voidclient:widget/button"),             // disabled
        Identifier.parse("voidclient:widget/button_highlighted"), // enabled + hovered/focused
        Identifier.parse("voidclient:widget/button_highlighted")  // disabled + focused
    );

    private final Runnable onPress;

    public SpriteButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Identifier sprite = SPRITES.get(this.active, this.isHoveredOrFocused());
        // nine-slice scales the 16x16 source to any width/height with crisp corners
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight());
        graphics.drawCenteredString(
            Minecraft.getInstance().font,
            getMessage(),
            getX() + getWidth() / 2,
            getY() + (getHeight() - 8) / 2,
            0xFFFFFFFF
        );
    }
}
