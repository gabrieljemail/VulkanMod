# Add-on Config Persistence, Fast Input Add-on, Texture LOD — Design

Date: 2026-07-22

## Context

Three independent pieces of work for this VulkanMod fork:

1. Per-add-on config persistence (previously written, lost — not recoverable from git history, reflog, stash, other branches/worktrees, or Downloads; confirmed via investigation on 2026-07-22).
2. A new `FastInput` add-on to reduce input latency by decoupling mouse/keyboard sampling from vanilla's 20Hz tick loop.
3. Texture LOD via proper mipmap sampler configuration (mip chains already generated; sampler currently zeroes out bias/anisotropy).

The `next-optimization-pass.md` file referenced at the start of this session does not exist anywhere retrievable (repo, git history, Downloads) and was explicitly dropped from scope by the user ("ignore it for now" — was allegedly related to finishing backface culling, unrelated to this design).

---

## A. Add-on config persistence

**Storage**: `config/VoidAddons/<addon-id>.json`, one file per add-on, via Gson (already used by `net.vulkanmod.config.Config`). Schema: array of `{key, value}` pairs keyed by `ConfigEntry` name; type resolved on read via each entry's `ConfigEntryType`. Unknown keys on disk are ignored; entries missing from disk keep their code-defined default (forward-compatible with add-on devs adding new config entries later).

**Lifecycle wiring** — fills the currently unwired `onRegister`/`onConfigUpdate` hooks in `Addon.java`:
- On `AddonRegistry.register(addon)`: load `<id>.json` if present, apply values onto `addon.config` entries by key, then call `addon.onRegister(...)`.
- The **registry**, not the add-on, owns a listener attached to each `ConfigEntry.onChange` at load time, which persists changes to disk. Add-on code never holds a reference to a config/persistence object — this is what avoids the leak from the previous (lost) implementation.
- On add-on reload, previously attached listeners are explicitly detached (`unregister()`/`Closeable`-style) before new ones are attached, so repeated reloads cannot accumulate duplicate listeners.

**Save timing**:
- Discrete entries (toggles, buttons): write immediately on change.
- Ranged/slider entries: commit and write once on pointer release (using the config screen's drag lifecycle), not per intermediate drag value. If the screen's button/slider widget doesn't cleanly expose a release event, fall back to a short debounce instead of per-tick writes.

**Config screen**: generic, auto-built `AddonConfigScreen` — iterates `addon.config` (a `ConfigEntry[]`) and renders one row per entry, widget chosen by `ConfigEntryType` (slider for ranged numeric, toggle for boolean). No per-add-on custom screen classes needed, including for `FastInput` below.

**ClickGUI wiring**: `AddonTile.java` currently only overrides `onPress` (left-click → `addon.toggle()`). Add a right-click handler that opens `AddonConfigScreen` for that add-on. Screen closes back to ClickGUI on Escape/back.

---

## B. `FastInput` add-on

**Config entries**: `enabled` (base toggle), `pollRateHz` (slider, default 250, range 60–1000).

**Problem being solved**: keyboard state is sampled via GLFW callbacks but only *consumed* by movement/action logic once per 20Hz tick. This add-on decouples keyboard sampling from tick rate. (Mouse look was originally in scope too, but dropped — see below.)

**Correction made during design**: decompiled this Minecraft version's classes to verify the premise. `MouseHandler.handleAccumulatedMovement()` (which turns the camera) is called inside `Minecraft.runTick()` — the per-frame method — not inside `tick()`, the 20Hz method (confirmed via bytecode inspection: the call appears before `tick()`'s bytecode even begins in the class listing). So camera look **already runs every rendered frame in vanilla**, not at 20Hz — a dedicated mouse poll loop would be redundant (and risked double-applying movement). The actual multi-frame input delay the user observed turned out to be a swapchain frame-queue-depth issue, addressed separately in section D (Low Latency Mode).

**Raw input correctness** (confirmed via investigation of decompiled MC 1.21.11 classes — `Window.updateRawMouseInput` exists in vanilla, independent of this fork): GLFW's cursor-pos callback without raw mode is backed by Windows' `WM_MOUSEMOVE`, which is OS-processed (pointer acceleration/ballistics, screen-edge clamping). `GLFW_RAW_MOUSE_MOTION` mode (active only while the cursor is grabbed) switches GLFW internally to Windows' Raw Input API (`RegisterRawInputDevices`/`WM_INPUT`), reading unprocessed relative deltas straight from the driver — no acceleration curve, no clamping.
- On enable (while cursor is grabbed): call `glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE)` if `glfwRawMouseMotionSupported()` is true.
- On disable: restore whatever vanilla's own "Raw Input" option was set to beforehand — don't permanently override the player's existing setting.
- No effect on Wayland (relative motion is already delivered directly at the compositor level there — not a Windows ballistics problem).

**Keyboard**: a per-frame poll (hooked into `Minecraft.runTick`, gated by an accumulator so it only actually samples at `pollRateHz` even though `runTick` fires every frame) reads current key-down state via `glfwGetKey` each cycle and latches it into a shared snapshot. The 20Hz tick reads the freshest snapshot instead of vanilla's queued-since-last-tick callback events, removing up to one tick's worth (~50ms) of queuing latency. This does not increase movement/physics resolution — physics still runs at 20Hz — it only removes latency in *when* a keypress is seen by the next tick, addressing part of the kernel-vs-Minecraft timing gap observed (the full ~150ms the user measured likely has other contributors too, e.g. USB polling interval, not something software-side latching alone fixes).

**Fallback**: when disabled, input handling is untouched vanilla GLFW callback path — no permanent hijacking.

---

## C. Texture LOD (mipmapped atlas + sampler LOD)

**Existing state**: mip chains are already generated (`ImageUtil.generateMipmaps`, wired via `glGenerateMipmap` mixin path). **Correction made during design**: anisotropic filtering is *not* actually missing — it's already fully implemented via vanilla's own "Texture Filtering" / "Anisotropic Filtering" settings (`Options.java:201-252`, `TextureFilteringMethod` enum), already wired through to the block atlas sampler at `WorldRenderer.java:357-362` (`useAnisotropy`/`maxAnisotropy` read from `minecraft.options`, passed into `SamplerManager.getSampler(...)`), with a working cache-invalidation path (`WorldRenderer.resetSampler()`, called when the setting changes). The only genuine gap is `mipLodBias`: `SamplerManager.java:98` hardcodes `mipLodBias(0.0F)`, and `SamplerInfo`'s packed cache key ([SamplerInfo.java:12-14](src/main/java/net/vulkanmod/vulkan/texture/SamplerInfo.java:12)) has no bias field at all. (There's also an unused `SamplerManager.MIP_BIAS = -0.5f` constant at line 32 — dead code, not wired to anything; leave it or remove it, don't repurpose it since -0.5 isn't necessarily the right default.)

**Fix**:
- Extend `SamplerInfo`'s cache key (the `encodedState`/fields used in `equals`/`hashCode`) with a `mipLodBias` field so biased samplers get distinct cache entries instead of colliding with the existing default-bias sampler.
- Thread `mipLodBias` through `SamplerManager.getSampler(...)`'s overloads down to `createTextureSampler`, replacing the hardcoded `samplerInfo.mipLodBias(0.0F)` at line 98 with the real value.
- In `WorldRenderer.java`, read the new `Config.mipLodBiasTenths` value alongside the existing `useAnisotropy`/`maxAnisotropy` reads (lines 357-358), pass it into the `getSampler(...)` call at line 362, and call `resetSampler()` when the setting changes (reusing the exact invalidation pattern already used for the anisotropy options) so the slider takes effect live instead of requiring a restart.
- No manual per-chunk/per-section distance computation needed — GPU screen-space-derivative LOD selection already picks lower mips at distance automatically once the sampler is configured correctly; this fix corrects sampler config, it doesn't add new distance logic.

**Settings** (Optimizations page, alongside existing `hudCache`/`throttleFarRebuilds` etc — anisotropic filtering already has its own setting under vanilla's Graphics page and needs no new UI):
- **Mip LOD Bias**: `RangeOption` is integer-only (`RangeOption.java:12`), so store as `mipLodBiasTenths` (int, range -10 to +10, default 0), display-translated as tenths (e.g. `-5` → "-0.5"), divided by 10.0f before use as the actual float bias. Negative sharpens (more shimmer), positive softens distant textures more (less aliasing, marginal bandwidth savings).

---

## D. Low Latency Mode (video setting)

**Root cause identified during design** (supersedes the mouse-poll-loop idea in section B, which was dropped after confirming vanilla already turns the camera every rendered frame, inside `Minecraft.runTick`, not at 20Hz): the actual source of the multi-frame input-to-photon delay is `Config.frameQueueSize` (default 2, range 2-5 in settings — [Options.java:475](src/main/java/net/vulkanmod/config/option/Options.java:475)), which lets the CPU record/submit up to N frames ahead of what the GPU has presented. A fresh camera angle is correct the instant it's computed, but the frame carrying it can sit queued for up to `frameQueueSize` frames before it reaches the screen. This is the same class of problem NVIDIA Reflex addresses, and matches how Ixeris achieves lower latency (minimizing render-ahead queue at the cost of CPU idle time / spin-polling).

**Fix**: add a `lowLatencyMode` boolean to `Config`. When enabled, it forces the effective frame queue depth to 1 (reusing the existing `framesNum`-generic fence/semaphore/command-buffer machinery in `Renderer.java`, which already live-resizes via `Renderer.scheduleSwapChainUpdate()` when `frameQueueSize` changes — no new synchronization logic needed) instead of whatever the Frame Queue slider is set to.

**Settings**: a `SwitchOption` "Low Latency Mode" placed directly next to the existing Frame Queue `RangeOption` (same `OptionBlock` in `getOtherOpts()`/wherever the Frame Queue slider currently lives — [Options.java:475-482](src/main/java/net/vulkanmod/config/option/Options.java:475)). When enabled, the Frame Queue slider is disabled (greyed via `setActivationFn`, same pattern already used for `maxAnisotropyOption`/`farRebuildBudgetOption` elsewhere in this file) since it's overridden. Default **off** (opt-in), with a tooltip explaining the tradeoff: lower input latency, at the cost of possible stutter during CPU-heavy frames (chunk rebuild bursts, GC pauses) since there's no longer a queued frame to absorb them.

---

## Out of scope

- `next-optimization-pass.md` content (file lost, explicitly dropped by user this session).
- Manual/non-standard texture LOD (per-chunk distance-bucketed bias) — standard sampler-driven approach was chosen instead.
- A dedicated mouse poll loop for `FastInput` — dropped after confirming vanilla already turns the camera every rendered frame (`runTick`, not `tick`); Low Latency Mode (section D) addresses the actual observed delay instead.
- Per-add-on custom config screen classes — generic auto-built screen chosen instead.
