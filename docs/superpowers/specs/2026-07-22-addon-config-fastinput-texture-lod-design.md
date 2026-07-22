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

**Problem being solved**: vanilla samples input once per 20Hz tick via GLFW callbacks queued and drained on the main loop. This add-on decouples sampling from tick rate.

**Mouse look**: a poll loop (dedicated thread or per-frame hook, timed to `pollRateHz`) reads cursor delta directly and applies yaw/pitch to the camera immediately, independent of the tick. This is the primary latency win — same class of technique as raw-input camera code in other performance-oriented mods, and the input-side analog of what NVIDIA Reflex does on the frame-pacing side (not adopted directly — Reflex reorders GPU submission timing, which is a different, largely DX12/vendor-specific mechanism; the sample-rate approach here is portable to Vulkan/Intel without driver-specific hooks).

**Raw input correctness** (confirmed via investigation of decompiled MC 1.21.11 classes — `Window.updateRawMouseInput` exists in vanilla, independent of this fork): GLFW's cursor-pos callback without raw mode is backed by Windows' `WM_MOUSEMOVE`, which is OS-processed (pointer acceleration/ballistics, screen-edge clamping). `GLFW_RAW_MOUSE_MOTION` mode (active only while the cursor is grabbed) switches GLFW internally to Windows' Raw Input API (`RegisterRawInputDevices`/`WM_INPUT`), reading unprocessed relative deltas straight from the driver — no acceleration curve, no clamping. Polling the non-raw path faster is not a real latency win, just faster sampling of an already-smoothed signal.
- On enable (while cursor is grabbed): call `glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE)` if `glfwRawMouseMotionSupported()` is true.
- On disable: restore whatever vanilla's own "Raw Input" option was set to beforehand — don't permanently override the player's existing setting.
- No effect on Wayland (relative motion is already delivered directly at the compositor level there — not a Windows ballistics problem).

**Keyboard**: the same poll loop reads current key-down state via `glfwGetKey` each cycle and latches it into a shared snapshot (volatile/atomic). The 20Hz tick reads the freshest snapshot instead of vanilla's queued-since-last-tick callback events, removing up to one tick's worth (~50ms) of queuing latency. This does not increase movement/physics resolution — physics still runs at 20Hz — it only removes latency in *when* a keypress is seen by the next tick, addressing the kernel-vs-Minecraft timing gap observed (~150ms).

**Fallback**: when disabled, input handling is untouched vanilla GLFW callback path — no permanent hijacking.

---

## C. Texture LOD (mipmapped atlas + sampler LOD)

**Existing state**: mip chains are already generated (`ImageUtil.generateMipmaps`, wired via `glGenerateMipmap` mixin path). `SamplerManager.java` currently hardcodes `mipLodBias(0.0F)` and does not enable anisotropic filtering; `SamplerInfo`'s packed cache key has no bias/anisotropy fields. Before implementation, double-check `SamplerManager`/`SamplerInfo` don't clamp `maxLod` to 0 anywhere, which would force full-resolution sampling regardless of bias settings.

**Fix**:
- Extend `SamplerInfo`'s cache key encoding with `mipLodBias` and `anisotropyLevel` fields so biased/anisotropic samplers get distinct cache entries.
- Wire real values into `VkSamplerCreateInfo` in `SamplerManager`: `mipLodBias(config value)`; enable `anisotropyEnable` + `maxAnisotropy(config value)` when `VkPhysicalDeviceFeatures.samplerAnisotropy` is supported (present on Gen9).
- No manual per-chunk/per-section distance computation needed — GPU screen-space-derivative LOD selection already picks lower mips at distance automatically once the sampler is configured correctly; this fix corrects sampler config, it doesn't add new distance logic.

**Settings** (Optimizations page, alongside existing `hudCache`/`throttleFarRebuilds` etc):
- **Mip LOD Bias**: slider, range -1.0 to +1.0, default 0.0. Negative sharpens (more shimmer), positive softens distant textures more (less aliasing, marginal bandwidth savings).
- **Anisotropic Filtering**: discrete steps 0/2x/4x/8x/16x, default 4x. Primarily helps textures viewed at grazing angles (floors/paths receding into distance).

---

## Out of scope

- `next-optimization-pass.md` content (file lost, explicitly dropped by user this session).
- Manual/non-standard texture LOD (per-chunk distance-bucketed bias) — standard sampler-driven approach was chosen instead.
- Reflex-style GPU frame-pacing/submission reordering — sample-rate approach chosen instead.
- Per-add-on custom config screen classes — generic auto-built screen chosen instead.
