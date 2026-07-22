package net.vulkanmod.vulkan.util;

import org.lwjgl.glfw.GLFWVidMode;

import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.glfw.GLFW.*;

/**
 * CPU-side frame pacing for VSync (FIFO/FIFO_RELAXED present modes).
 *
 * Without this, the render thread runs ahead of the display until the swapchain
 * queue fills and it blocks inside vkAcquireNextImageKHR at an unpredictable
 * point mid-frame. Frames are still *displayed* at the refresh rate, but game
 * time and input are sampled at jittery offsets, which shows up as judder.
 * Pacing the thread to the refresh interval keeps the swapchain queue shallow,
 * so acquire/present never block and the frame loop stays phase-stable.
 */
public class FramePacer {
    // Windows timer/park granularity is ~1ms; sleep coarsely up to this margin
    // before the deadline, then spin the rest for sub-0.1ms pacing accuracy.
    private static final long SPIN_MARGIN_NS = 2_000_000;

    private static final long REFRESH_QUERY_PERIOD_NS = 1_000_000_000;

    private static long nextDeadline;

    private static long cachedIntervalNs = 1_000_000_000 / 60;
    private static long lastRefreshQuery;

    public static void pace(long windowHandle) {
        final long interval = refreshIntervalNs(windowHandle);

        long now = System.nanoTime();
        long deadline = nextDeadline + interval;

        // Running behind the display (heavy frame, or fps below refresh rate):
        // don't wait, just re-anchor the timeline.
        if (deadline <= now) {
            nextDeadline = now;
            return;
        }

        final long sleepUntil = deadline - SPIN_MARGIN_NS;
        while (now < sleepUntil) {
            LockSupport.parkNanos(sleepUntil - now);
            now = System.nanoTime();
        }

        while (now < deadline) {
            Thread.onSpinWait();
            now = System.nanoTime();
        }

        nextDeadline = deadline;
    }

    private static long refreshIntervalNs(long windowHandle) {
        long now = System.nanoTime();
        if (now - lastRefreshQuery > REFRESH_QUERY_PERIOD_NS) {
            lastRefreshQuery = now;

            long monitor = glfwGetWindowMonitor(windowHandle);
            if (monitor == 0)
                monitor = glfwGetPrimaryMonitor();

            GLFWVidMode vidMode = monitor != 0 ? glfwGetVideoMode(monitor) : null;
            int refreshRate = vidMode != null ? vidMode.refreshRate() : 0;

            cachedIntervalNs = 1_000_000_000L / (refreshRate > 0 ? refreshRate : 60);
        }

        return cachedIntervalNs;
    }
}
