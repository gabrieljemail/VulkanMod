package net.voidsmp.client.addons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;

import java.util.function.Consumer;

/**
 * Velocity-driven motion blur. The strength scales with how fast the camera is
 * moving, modulated by a configurable {@code intensity}. This is the first
 * add-on to actually use {@link ConfigEntry}.
 *
 * <p>The config + velocity plumbing is complete here: {@link #STRENGTH} is the
 * final 0..1 blur amount, recomputed each tick, ready for the post-process pass
 * to consume. (That GPU pass is the remaining piece — see notes.)
 */
public class MotionBlur extends Addon {

    // Hot-path state read by the render pass — plain statics, no lookups.
    public static boolean ENABLED = false;
    public static float INTENSITY = 0.5f;     // user setting, 0..1
    public static float STRENGTH = 0.0f;      // velocity-scaled result, 0..1
    public static boolean HORIZONTAL = true;  // blur axis: true = yaw (turning), false = pitch

    // Maps camera rotation speed (degrees per tick) onto the 0..1 strength
    // range. A brisk flick is tens of degrees/tick, so this saturates on fast
    // turns and stays subtle on slow ones.
    private static final float ROTATION_TO_STRENGTH = 0.06f;

    private float prevYaw;
    private float prevPitch;
    private boolean hasPrev = false;

    private final ConfigEntry<Float> intensity =
        new ConfigEntry<>("intensity", ConfigEntryType.UFLOAT, 0.5f)
            .describe("Intensity", "How strong the motion blur is at full turn speed.")
            .range(0.0, 1.0)
            .onChange(v -> INTENSITY = v);

    public MotionBlur() {
        this.id = "voidclient:motion_blur";
        this.name = "Motion Blur";
        this.config = new ConfigEntry[]{ intensity };

        // Recompute the direction/strength from camera rotation every tick.
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (!ENABLED || client.player == null) {
            STRENGTH = 0.0f;
            hasPrev = false;
            return;
        }

        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();
        if (!hasPrev) {
            prevYaw = yaw;
            prevPitch = pitch;
            hasPrev = true;
            STRENGTH = 0.0f;
            return;
        }

        float dYaw = Mth.wrapDegrees(yaw - prevYaw);   // turning left/right
        float dPitch = pitch - prevPitch;              // looking up/down
        prevYaw = yaw;
        prevPitch = pitch;

        // Blur along whichever rotation dominates this tick.
        HORIZONTAL = Math.abs(dYaw) >= Math.abs(dPitch);
        float rotationSpeed = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        STRENGTH = Mth.clamp(rotationSpeed * ROTATION_TO_STRENGTH * INTENSITY, 0.0f, 1.0f);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        ENABLED = enabled;
    }

    @Override
    protected void onRegister(Consumer<Addon> callback) {
        // Nothing to register yet.
    }

    @Override
    protected void onConfigUpdate(Consumer<ConfigEntry> change) {
        // Per-entry listeners (see the intensity entry) handle updates directly.
    }
}
