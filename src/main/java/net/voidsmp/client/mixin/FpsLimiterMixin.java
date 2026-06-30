package net.voidsmp.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Precise frame limiter. Vanilla's {@code limitDisplayFPS} waits with
 * {@code glfwWaitEventsTimeout}, whose OS-timer granularity overshoots the
 * frame deadline — and it then anchors {@code lastDrawTime} to that overshot
 * time, so the schedule drifts and you land ~10% under the cap (e.g. 162 of a
 * requested 180).
 *
 * <p>This replaces it with a hybrid: sleep-and-pump-events until ~1ms before
 * the deadline (so input stays responsive), then busy-spin the final sliver to
 * hit the deadline to the microsecond, and anchor to the ideal deadline so the
 * cadence never drifts. The spin only runs when an FPS cap below your achievable
 * rate is set, and only for that last &lt;1ms — so the CPU cost is small.
 */
@Mixin(RenderSystem.class)
public class FpsLimiterMixin {

    @Shadow private static double lastDrawTime;

    // How long before the deadline to stop sleeping and start spinning. Bigger =
    // more reliable precision but more CPU spent spinning; smaller = leaner but
    // risks the sleep overshooting past it. 1ms is a safe margin on Linux.
    private static final double SPIN_MARGIN = 0.001;

    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void preciseLimit(int fps, CallbackInfo ci) {
        if (fps < 1) {
            return; // no real cap — let vanilla handle it
        }

        double deadline = lastDrawTime + 1.0 / fps;
        double now = GLFW.glfwGetTime();

        if (now < deadline) {
            // Sleep the bulk of the wait while still pumping window/input events.
            while (deadline - now > SPIN_MARGIN) {
                GLFW.glfwWaitEventsTimeout(deadline - now - SPIN_MARGIN);
                now = GLFW.glfwGetTime();
            }
            // Spin the final sub-millisecond for an exact frame delivery.
            while (now < deadline) {
                Thread.onSpinWait();
                now = GLFW.glfwGetTime();
            }
            // Anchor to the ideal deadline, not the (slightly past) now, so the
            // cadence stays on a perfect grid instead of drifting.
            lastDrawTime = deadline;
        } else {
            // Frame ran longer than the budget — reset rather than try to catch
            // up with a burst of un-throttled frames.
            lastDrawTime = now;
        }

        ci.cancel();
    }
}
