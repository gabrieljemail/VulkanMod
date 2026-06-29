package net.voidsmp.client.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.voidsmp.client.addons.AddonRegistry;

/**
 * The ClickGUI menu used to toggle and configure Add-ons.
 *
 * <p>This is a {@link Screen} (not a HUD layer) so it frees the mouse cursor,
 * dims the world behind it, and receives mouse/keyboard input — everything a
 * clickable menu needs. It's opened/closed by {@link ClickGUI}'s keybinding.
 */
public class ClickGuiScreen extends Screen {

    public ClickGuiScreen() {
        super(Component.literal("Void Client"));
    }

    // Tile grid layout.
    private static final int TILE_W = 120;
    private static final int TILE_H = 36;
    private static final int GAP = 8;
    private static final int COLUMNS = 3;

    @Override
    protected void init() {
        // Lay out a clickable tile per registered add-on. Re-run on every
        // open/resize so the grid stays centred.
        var addons = AddonRegistry.all();
        int rows = Math.max(1, (addons.size() + COLUMNS - 1) / COLUMNS);
        int gridW = COLUMNS * TILE_W + (COLUMNS - 1) * GAP;
        int gridH = rows * TILE_H + (rows - 1) * GAP;
        int startX = (this.width - gridW) / 2;
        int startY = (this.height - gridH) / 2;

        for (int i = 0; i < addons.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int x = startX + col * (TILE_W + GAP);
            int y = startY + row * (TILE_H + GAP);
            addRenderableWidget(new AddonTile(x, y, TILE_W, TILE_H, addons.get(i)));
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // dims the background + draws widgets
        context.drawCenteredString(this.font, "Void Client", this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Let the same toggle key close the menu (keybinds don't fire while a
        // screen is open, so this is handled here instead of in the tick poll).
        if (ClickGUI.TOGGLE_HUD.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
