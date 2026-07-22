# VulkanMod Fork — Optimization Brief for Claude Code

## Context

Custom fork of VulkanMod for Minecraft, targeting a specific hardware profile:
- **GPU:** Intel HD 530 (Gen9, 24 EUs, UMA/shared memory architecture)
- **CPU:** Intel Core i5-6600
- **RAM:** 16GB dual-channel DDR4
- **Platform constraint:** Gen9 has a single queue family (no async transfer queue), and its hard ceiling is EU count + memory bandwidth — NOT draw call count in isolation. Every optimization should be evaluated through that lens: does this reduce bandwidth/driver overhead, or does it just move work around?

**Baseline FPS in this world/benchmark:** Sodium 120 / stock VulkanMod 169 / this fork currently 220 (via vertex compression, spin-timer frame capping, UMA-aware allocation).

Goal: implement the changes below to push further past 220, in priority order.

---

## Priority 1 — Indirect draw calls (biggest expected win)

**Problem:** Command recording is single-threaded and issues one draw call per chunk section. Driver overhead per draw is the dominant cost on Gen9's Windows driver.

**Fix:** Replace per-section `vkCmdDrawIndexed` calls with `vkCmdDrawIndexedIndirect` using `multiDrawIndirect` (supported on Gen9):
- Build a `VkDrawIndexedIndirectCommand` array per ChunkArea/render type, writable from any thread (cheap CPU work, no GPU sync needed to build it).
- Issue one indirect draw call per render type per area instead of thousands of individual draws.
- Draws within an area already share a vertex buffer binding (existing AreaBuffer design), so this slots in without restructuring buffer layout.

**Why first:** Directly attacks driver overhead, which is the dominant per-frame cost on this hardware. Don't bother with parallelized secondary command buffer recording — collapsing draw count is a better fix than parallelizing the recording of many draws.

---

## Priority 2 — Eliminate staging buffer (UMA-specific)

**Problem:** Chunk uploads currently go through a staging buffer + `vkCmdCopyBuffer` + transfer barrier, which is a discrete-GPU pattern. On UMA there's no separate VRAM, so this doubles memory traffic for no reason.

**Fix:**
- Allocate chunk vertex buffers as `DEVICE_LOCAL | HOST_VISIBLE` (available on integrated Intel).
- Upload via direct memcpy into the live buffer — no copy command, no transfer barrier.
- Do uploads at frame start before the render pass begins, with a tight barrier (no second queue available for async transfer on Gen9, so this has to be serialized carefully, not overlapped via queue).
- Follow-on: most transfer barriers in the current sync code disappear once staging is gone — clean those up too (see Priority 5).

**Why second:** Cuts upload bandwidth in half on a platform whose hard ceiling is bandwidth.

---

## Priority 3 — Chunk mesher: direct-to-compressed emit

**Problem:** The mesher currently builds through Minecraft's `BakedQuad` path into an intermediate buffer, then converts to the fork's compressed vertex format — two passes instead of one.

**Fix:**
- Emit vertices directly into the compressed format inside the mesher, skipping the intermediate buffer.
- Pool/reuse `ThreadLocal` builder buffers across chunk rebuilds to eliminate allocation/GC spikes during fast flight.
- Move translucent face resorting off the render thread onto the existing task-dispatcher threads.

**Why third:** Compounds with existing vertex compression work; reduces CPU-side memory traffic during world traversal, which is where stutter currently shows up.

*(Stretch goal, not required this pass: greedy meshing — bigger win but a full rewrite of the quad emitter, not a patch.)*

---

## Priority 4 — Entity rendering: persistent ring buffer

**Problem:** Entities flush through `BufferSource` per RenderType with a fresh upload every frame, even though entity geometry barely changes frame to frame.

**Fix:**
- Persistent-mapped ring buffer, 3x frame size, fence-guarded.
- Write entity vertex data directly into the ring buffer (no copy, since UMA means CPU and GPU share memory).
- Sort flushed batches by pipeline before recording, to avoid rebinding pipelines per entity type.
- Full instancing/bindless is explicitly out of scope for this pass — ring buffer + pipeline sort captures most of the win at much lower implementation risk.

---

## Priority 5 — Synchronization cleanup

**Fix (do after Priority 2, since staging removal changes what's needed here):**
- Replace per-frame fences with a single timeline semaphore (Vulkan 1.2 — supported on Gen9's Windows driver).
- Narrow barriers from global `VK_ACCESS_MEMORY_*` scopes down to per-buffer barriers with precise stage masks.
- No async transfer queue available on Gen9 (single queue family) — don't try to build one; overlap comes from ordering uploads before the render pass, not from a second queue.

---

## Explicitly out of scope / not worth it on this hardware

- Parallel command buffer recording across threads — collapsed by Priority 1 instead.
- Async transfer queue — not available on Gen9.
- Full bindless/instancing for entities — diminishing returns vs. ring buffer approach.

## Suggested implementation order

1. Indirect draws (P1)
2. Staging elimination (P2)
3. Sync cleanup (P5, since P2 removes most of the barriers it touches)
4. Mesher direct-emit (P3)
5. Entity ring buffer (P4)
