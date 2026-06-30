package net.voidsmp.client.addons;

import net.minecraft.client.Minecraft;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;

import java.util.function.Consumer;

/**
 * ChunkRes — distance-based chunk level of detail. Beyond a start distance, far
 * chunks are meshed at reduced horizontal resolution (and merged into larger
 * meshes) to cut vertex memory, upload churn and draw calls. Horizontal-only,
 * so terrain doesn't "grow taller" as you approach it. See docs/ChunkLOD.md.
 *
 * <p>Gated behind this addon's toggle on purpose: the LOD meshing path is
 * work-in-progress, so keeping it off by default means a bug there can never
 * crash startup — it only runs once enabled (and the failure is loggable), and
 * config changes rebuild chunks to apply.
 *
 * <p>The meshing pipeline itself isn't wired yet; this is the toggle + config
 * scaffold. The static fields below are what that pipeline will read.
 */
public class ChunkRes extends Addon {

    // Hot-path state for the (future) LOD meshing pipeline — plain statics.
    public static boolean ENABLED = false;
    public static int START_DISTANCE = 4; // chunks from camera before LOD begins
    public static int DETAIL_DIVISOR = 2; // horizontal decimation factor (2 = half res)

    private final ConfigEntry<Integer> startDistance =
        new ConfigEntry<>("start_distance", ConfigEntryType.UINT, 4)
            .describe("Start Distance", "Chunks away from you before far-chunk LOD kicks in.")
            .range(2, 32)
            .onChange(v -> { START_DISTANCE = v; rebuild(); });

    private final ConfigEntry<Integer> detailDivisor =
        new ConfigEntry<>("detail_divisor", ConfigEntryType.UINT, 2)
            .describe("Detail Divisor", "How much to cut far-chunk detail. Higher = blurrier and cheaper.")
            .range(2, 4)
            .onChange(v -> { DETAIL_DIVISOR = v; rebuild(); });

    public ChunkRes() {
        this.id = "voidclient:chunk_res";
        this.name = "Chunk Resolution";
        this.config = new ConfigEntry[]{ startDistance, detailDivisor };
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        ENABLED = enabled;
        rebuild();
    }

    private void rebuild() {
        // LOD is baked into chunk meshes, so existing chunks must re-mesh to
        // apply a toggle or setting change. Guarded: during addon construction
        // (client init) the level renderer isn't up yet, so this is a no-op then.
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    @Override
    protected void onRegister(Consumer<Addon> callback) {
        // Nothing to register yet.
    }

    @Override
    protected void onConfigUpdate(Consumer<ConfigEntry> change) {
        // Per-entry listeners handle updates directly.
    }
}
