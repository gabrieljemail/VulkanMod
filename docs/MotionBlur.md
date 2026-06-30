# Motion Blur — Feasibility Results

## Question

Can Void Client ship a motion blur **without** the 20–30% FPS drops seen on other clients/mods?

**Answer: yes.** The cost cliff comes from one design choice, and the cheap path sidesteps it.

## Why other clients are expensive

"Good" motion blur uses **per-object motion vectors** — the scene geometry is rendered a second time (or to an extra G-buffer target) with the previous frame's transforms to know how each pixel moved. On a *forward* renderer like vanilla / VulkanMod that's effectively a second geometry pass plus another full-screen render target. **That** is the 20–30%, not the blur math.

## The cheap approach

**Camera-only directional blur:** one full-screen pass that samples the color target a few times along the camera's motion direction. The camera delta is just the view-matrix change frame-to-frame (already on the CPU) — **no velocity buffer, no second geometry pass.**

- Blurs from camera movement/rotation only, not individual moving entities. For a Minecraft client that's exactly what players want (turning/sprinting blur).
- This is what `MotionBlur.STRENGTH` (velocity-scaled, computed per tick) is meant to feed.

## Measurements

Dev laptop: Intel i3-1005G1 / UHD G1 (Ice Lake), 256 MB VRAM, 1366×768, Vulkan 1.4, VulkanMod 0.6.7-dev.

**Singleplayer (CPU-bound — misleading):**

| Test | Result | Interpretation |
|---|---|---|
| Baseline | ~7–8 ms/frame (~130 fps) | CPU-bound: aniso 8× + smooth lighting changed nothing. The integrated server shared the CPU. |
| +1 fullscreen blit | no measurable change | cost hidden under CPU time |
| +50 fullscreen blits | min ~13 ms, avg wrecked by 2.5 s spikes | GPU work was mostly hidden; min implied ~0.12 ms/pass but this **underestimates** because the frame was CPU-limited |

**Multiplayer (Paper server, GPU-bound — the real number):** open arena, no integrated server.

| Test | Result | Interpretation |
|---|---|---|
| Baseline | ~198 fps (~5.0 ms avg) | GPU-bound; no integrated-server CPU tax |
| +4 fullscreen blits | ~154 fps (~6.5 ms avg) | Δ ≈ 1.4 ms for 4 passes |

**Per-pass cost ≈ 0.35 ms** (1366×768, this iGPU). It's a roughly **fixed millisecond cost** (resolution/bandwidth bound, ~independent of scene), so the *percentage* depends entirely on framerate:

- At 198 fps (5 ms frames): one pass ≈ 0.35 ms ≈ **~6–7%** — looks big because the frames are cheap.
- At 60 fps (16.7 ms frames): the same 0.35 ms ≈ **~2%**.

Still well clear of the 20–30% that per-object-velocity clients pay. Lesson: **measure rendering cost GPU-bound** (multiplayer / no integrated server / high fill) — CPU-bound tests hide it and gave a 3× underestimate.

### Important stability caveat

~50 sustained fullscreen blits/frame **destabilized this iGPU**: 2565 ms frame spikes (GPU hang/timeout, likely `VK_ERROR_DEVICE_LOST`) and a crash when GUI allocation was added on top. The shipping shader version must stay **single-pass, modest tap count**, and be re-validated on this machine. Weak iGPUs with little VRAM are fragile under heavy fullscreen-pass load.

## How it was measured (the prototype)

A gated cost-probe in `DefaultMainPass`, off unless the **Motion Blur** add-on is toggled:

- `motionBlurCostBenchmark()` runs `BENCHMARK_PASSES` fullscreen blits of the finished frame into an offscreen `history` image via the tested `ImageUtil.blitFramebuffer(...)`.
- The swapchain is a **read-only blit source** (never written), so the displayed frame can't be corrupted — it measures GPU cost only, no visual.
- Skipped while a menu is open. `BENCHMARK_PASSES = 1` is a realistic single pass; crank it up purely to find the GPU bottleneck. Set back to `1` (or remove) before shipping.

### Gotcha: BGRA swapchains

Intel swapchains are `VK_FORMAT_B8G8R8A8_UNORM` (format 44), which `VulkanImage.Builder.formatSize()` rejects. The history image uses the Builder's default RGBA8 instead — `vkCmdBlitImage` converts formats and the image is never displayed, so channel order is irrelevant.

## Next steps

1. Replace the blit loop with a single directional-blur shader pass fed by `MotionBlur.STRENGTH`.
2. Validate it doesn't trip GPU timeouts on the dev iGPU.
3. **TAA (later):** note that *good* TAA needs the same per-object motion-vector machinery as good motion blur, so it shares the cost cliff. *Cheap* TAA (no motion vectors) is light but ghosts on moving objects. A camera-only directional blur sidesteps the cliff; TAA quality won't.
