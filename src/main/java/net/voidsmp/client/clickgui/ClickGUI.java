/*

The ClickGUI is the mod menu used to toggle and
configure Add-ons. Its default key is Right Shift.

*/

package net.voidsmp.client.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ClickGUI {
    // Register the keybinding on startup.
    public static final KeyMapping TOGGLE_HUD = KeyBindingHelper.registerKeyBinding(
        new KeyMapping(
            "key.voidclient.toggle_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyMapping.Category.MISC
        )
    );

    public ClickGUI() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        // consumeClick() only fires while no screen is open, so this opens the
        // menu; closing is handled inside ClickGuiScreen (keyPressed) and by ESC.
        while (TOGGLE_HUD.consumeClick()) {
            if (!(client.screen instanceof ClickGuiScreen)) {
                client.setScreen(new ClickGuiScreen());
            }
        }
    }
}
