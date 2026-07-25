package net.voidsmp.client.addons.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.voidsmp.client.addons.models.Addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns everything a {@link HudRenderable} element doesn't: registration with
 * Fabric's HudElementRegistry, and the corner/offset/direction layout math.
 * An add-on only ever supplies content (via {@link HudRenderable#renderHudStack}).
 *
 * <p>Layout rule: items are laid out left-to-right (growing rightward) for a
 * left-anchored corner, and right-to-left (growing leftward, so nothing runs
 * off-screen) for a right-anchored corner — reading order stays left-to-right
 * either way, only the growth direction from the anchor flips. Multiple
 * add-ons sharing the same corner aren't stacked against each other in this
 * version — they'll simply overlap.
 */
public final class HudElementManager {
    private static final int ITEM_SPACING = 4;

    private HudElementManager() {
    }

    public static void register(Addon addon, HudRenderable renderable) {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            renderable.hudElementId(),
            (graphics, tickCounter) -> render(addon, renderable, graphics)
        );
    }

    private static void render(Addon addon, HudRenderable renderable, GuiGraphics graphics) {
        if (!addon.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<HudStackItem> items = renderable.renderHudStack(mc);
        if (items.isEmpty()) {
            return;
        }

        Font font = mc.font;
        List<int[]> sizes = new ArrayList<>(items.size()); // {width, height} per item, same order as items
        int rowHeight = font.lineHeight;
        for (HudStackItem item : items) {
            int w = itemWidth(font, item);
            int h = itemHeight(font, item);
            sizes.add(new int[]{w, h});
            rowHeight = Math.max(rowHeight, h);
        }

        HudAnchor anchor = renderable.hudAnchor();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int y = anchor.isTopAnchored()
            ? renderable.hudOffsetY()
            : screenHeight - renderable.hudOffsetY() - rowHeight;

        if (anchor.isLeftAnchored()) {
            int x = renderable.hudOffsetX();
            for (int i = 0; i < items.size(); i++) {
                drawItem(graphics, font, items.get(i), x, y, rowHeight);
                x += sizes.get(i)[0] + ITEM_SPACING;
            }
        } else {
            List<HudStackItem> reversedItems = new ArrayList<>(items);
            List<int[]> reversedSizes = new ArrayList<>(sizes);
            Collections.reverse(reversedItems);
            Collections.reverse(reversedSizes);

            int x = screenWidth - renderable.hudOffsetX();
            for (int i = 0; i < reversedItems.size(); i++) {
                x -= reversedSizes.get(i)[0];
                drawItem(graphics, font, reversedItems.get(i), x, y, rowHeight);
                x -= ITEM_SPACING;
            }
        }
    }

    private static int itemWidth(Font font, HudStackItem item) {
        return switch (item) {
            case HudStackItem.Text text -> font.width(text.text());
            case HudStackItem.Icon icon -> icon.size();
        };
    }

    private static int itemHeight(Font font, HudStackItem item) {
        return switch (item) {
            case HudStackItem.Text ignored -> font.lineHeight;
            case HudStackItem.Icon icon -> icon.size();
        };
    }

    private static void drawItem(GuiGraphics graphics, Font font, HudStackItem item, int x, int y, int rowHeight) {
        switch (item) {
            case HudStackItem.Text text -> {
                int textY = y + (rowHeight - font.lineHeight) / 2;
                graphics.drawString(font, text.text(), x, textY, text.color());
            }
            case HudStackItem.Icon icon -> {
                int iconY = y + (rowHeight - icon.size()) / 2;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite(), x, iconY, icon.size(), icon.size());
            }
        }
    }
}
