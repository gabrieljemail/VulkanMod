package net.voidsmp.client.addons;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Decouples keyboard sampling from the 20Hz game tick and forces raw mouse
 * motion on while active. Mouse *look* is already per-frame in vanilla
 * (Minecraft.runTick calls MouseHandler.handleAccumulatedMovement, not
 * Minecraft.tick), so this addon doesn't touch mouse look — only keyboard
 * state, which vanilla still only consumes once per tick.
 *
 * <p>Singleton (not {@code new}-constructed like the other add-ons): {@link
 * FastInputMixin} needs a stable reference to poll every frame, so this
 * exposes {@link #INSTANCE} and is registered via that instance rather than
 * a fresh object.
 */
public class FastInputAddon extends Addon {
    public static final FastInputAddon INSTANCE = new FastInputAddon();

    private final ConfigEntry<Integer> pollRateHz =
        new ConfigEntry<>("pollRateHz", ConfigEntryType.UINT, 250)
            .range(60, 1000)
            .describe("Poll Rate (Hz)", "How often keyboard state is re-sampled, independent of the 20Hz game tick.");

    private double nextPollTime = Double.NEGATIVE_INFINITY;
    private Boolean rawMotionWasEnabled;
    private boolean rawMotionApplyPending;

    private FastInputAddon() {
        this.id = "voidclient:fast_input";
        this.name = "Fast Input";
        this.config = new ConfigEntry[]{pollRateHz};
    }

    @Override
    protected void onRegister(Consumer<Addon> callback) {
        // Nothing to register yet.
    }

    @Override
    protected void onConfigUpdate(Consumer<ConfigEntry> change) {
        // Per-entry listeners handle updates directly.
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = isEnabled();
        super.setEnabled(enabled);
        if (enabled && !wasEnabled) {
            onEnabled();
        } else if (!enabled && wasEnabled) {
            onDisabled();
        }
    }

    private void onEnabled() {
        nextPollTime = Double.NEGATIVE_INFINITY;
        applyRawMotionForcedOn();
    }

    private void onDisabled() {
        Window window = getWindowOrNull();
        // If the window never existed while this was "on" (see
        // applyRawMotionForcedOn), rawMotionWasEnabled is still null and
        // nothing was ever actually changed — there's nothing to restore.
        if (window != null && rawMotionWasEnabled != null) {
            GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_RAW_MOUSE_MOTION, rawMotionWasEnabled ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        }
        rawMotionWasEnabled = null;
        rawMotionApplyPending = false;
    }

    /**
     * Forces raw mouse motion on, saving the prior state to restore later.
     * {@code setEnabled(true)} can run before Minecraft's GLFW window
     * exists — e.g. this addon's persisted "on" state is applied by
     * {@code AddonConfigStorage.load()} from {@code AddonRegistry.register()},
     * which Fabric invokes from inside {@code Minecraft}'s own constructor,
     * before the window field is set. In that case this defers the actual
     * GLFW call to the first {@link #pollIfDue()} call, which only ever runs
     * from the per-frame render-loop mixin — well after the window exists.
     */
    private void applyRawMotionForcedOn() {
        Window window = getWindowOrNull();
        if (window == null) {
            rawMotionApplyPending = true;
            return;
        }

        long handle = window.handle();
        rawMotionWasEnabled = GLFW.glfwGetInputMode(handle, GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE;
        if (GLFW.glfwRawMouseMotionSupported()) {
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
        }
        rawMotionApplyPending = false;
    }

    private static Window getWindowOrNull() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getWindow();
    }

    /** Called every rendered frame by {@code FastInputMixin}; internally throttles to {@link #pollRateHz}. */
    public void pollIfDue() {
        if (!isEnabled()) {
            return;
        }

        if (rawMotionApplyPending) {
            applyRawMotionForcedOn();
        }

        double now = GLFW.glfwGetTime();
        if (now < nextPollTime) {
            return;
        }
        nextPollTime = now + 1.0 / Math.max(1, pollRateHz.get());

        latchKeyboardState();
    }

    private void latchKeyboardState() {
        // Skip entirely while any screen (chat, inventory, a config screen,
        // pause menu, ...) owns input focus. Vanilla stops feeding physical
        // key state into KeyMapping while a screen is open specifically so
        // typing "e" into chat doesn't also fire the inventory keybind;
        // polling raw GLFW state unconditionally here bypassed that and
        // fired every matching keybind while typing.
        if (Minecraft.getInstance().screen != null) {
            return;
        }

        // Re-reads current GLFW key state directly (bypassing vanilla's
        // queued-since-last-tick callback events) for every key vanilla's
        // KeyMapping system tracks, so the next tick sees the freshest
        // possible state. KeyMapping's own key/press bookkeeping already
        // updates on the GLFW key callback; polling here just forces a fresh
        // read immediately before it matters instead of relying on whatever
        // was last queued.
        //
        // KeyMapping doesn't expose a public getter for its bound Key (the
        // field is protected, package-private in effect from here), so the
        // bound key is recovered the same way vanilla persists it: via
        // saveString() (== key.getName()) round-tripped through
        // InputConstants.getKey(String), the public inverse of that name.
        Window window = Minecraft.getInstance().getWindow();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            InputConstants.Key key = InputConstants.getKey(mapping.saveString());
            if (key.getType() != InputConstants.Type.KEYSYM) {
                continue;
            }
            boolean pressed = InputConstants.isKeyDown(window, key.getValue());
            KeyMapping.set(key, pressed);
        }
    }
}
