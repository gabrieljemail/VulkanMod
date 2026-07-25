package net.voidsmp.client.addons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.voidsmp.client.addons.hud.HudAnchor;
import net.voidsmp.client.addons.hud.HudRenderable;
import net.voidsmp.client.addons.hud.HudStackItem;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shows the player's current movement state — useful for toggle-sprint /
 * toggle-crouch players, who get no held-key feedback for what state
 * they're actually in. Only one state is shown at a time even though the
 * underlying flags can overlap (e.g. sprint-swimming): Flying beats
 * Swimming beats Sneaking beats Sprinting, with Walking as the default.
 */
public class PlayerStateHud extends Addon implements HudRenderable {
    private static final Identifier ELEMENT_ID = Identifier.fromNamespaceAndPath("voidclient", "player_state_hud");

    // Keep this order in lockstep with corner.choices() below — the config
    // value is an index into both.
    private static final HudAnchor[] ANCHORS = {
        HudAnchor.TOP_LEFT, HudAnchor.TOP_RIGHT, HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT
    };

    private final ConfigEntry<Integer> corner =
        new ConfigEntry<>("corner", ConfigEntryType.ENUM, 0)
            .choices("Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right")
            .describe("Corner", "Which screen corner to anchor to.");

    private final ConfigEntry<Integer> offsetX =
        new ConfigEntry<>("offset_x", ConfigEntryType.UINT, 4)
            .range(0, 64)
            .describe("Offset X", "Horizontal inset from the corner, in pixels.");

    private final ConfigEntry<Integer> offsetY =
        new ConfigEntry<>("offset_y", ConfigEntryType.UINT, 4)
            .range(0, 64)
            .describe("Offset Y", "Vertical inset from the corner, in pixels.");

    public PlayerStateHud() {
        this.id = "voidclient:player_state_hud";
        this.name = "Player State HUD";
        this.config = new ConfigEntry[]{corner, offsetX, offsetY};
    }

    @Override
    protected void onRegister(Consumer<Addon> callback) {
    }

    @Override
    protected void onConfigUpdate(Consumer<ConfigEntry> change) {
    }

    @Override
    public Identifier hudElementId() {
        return ELEMENT_ID;
    }

    @Override
    public HudAnchor hudAnchor() {
        return ANCHORS[corner.get()];
    }

    @Override
    public int hudOffsetX() {
        return offsetX.get();
    }

    @Override
    public int hudOffsetY() {
        return offsetY.get();
    }

    @Override
    public List<HudStackItem> renderHudStack(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return List.of();
        }

        String label;
        if (player.isFallFlying() || player.getAbilities().flying) {
            label = "Flying";
        } else if (player.isSwimming()) {
            label = "Swimming";
        } else if (player.isCrouching()) {
            label = "Sneaking";
        } else if (player.isSprinting()) {
            label = "Sprinting";
        } else {
            label = "Walking";
        }

        return List.of(new HudStackItem.Text(Component.literal(label), 0xFFFFFFFF));
    }
}
