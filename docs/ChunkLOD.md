# Chunk LOD — Design Notes (idea capture)

Status: **idea / not started.** Captured so it isn't lost. This is the planned
centerpiece feature.

## The idea

Distance-ringed level of detail, **within render distance** (not a distance
extension):

- Near the camera (e.g. ≤ 4 chunks): full-res per-section meshing, exactly as now.
- Beyond that: **merge groups of sections into single meshes** and **halve
  horizontal geometry resolution** (the next ~8-chunk ring rendered at the detail
  of 4). Optionally further rings drop to ¼, etc.
- **Keep full vertical/height resolution.** Decimating height makes terrain
  appear to "grow taller" as you approach it — looks bad. Horizontal-only avoids
  that.

## Why it's good (the core insight — don't lose this)

1. **It's LOD *within* render distance, not distance extension.** Unlike Distant
   Horizons / Voxy, it renders chunks you *already have*, just cheaper for the
   far ones. So **no disk chunk cache and no "the server never sent this chunk"
   problem** — it works on multiplayer (Void SMP, the real target) for free.
   DH/Voxy's hardest part doesn't exist here.
2. **It targets the actual bottleneck.** The dev machine is CPU-bound (see
   docs/MotionBlur.md). Both wins are CPU-side: **fewer draw calls** (merged
   meshes) and **fewer vertices to build/submit** (half-res). Better matched to a
   weak iGPU than any GPU trick.

## Hard parts (known, none fatal)

- **LOD seams / T-junction cracks** at full↔half borders → fix with **skirts**
  (extra geometry hanging down at LOD edges to hide gaps). Usually the biggest
  time sink in terrain LOD.
- **Transition popping** when a region crosses a ring as you move → accept, or
  cross-fade.
- **Decimation needs a representative-block choice** per merged cell. Textured
  decimation shimmers/misaligns; DH renders far terrain as averaged colored
  blocks. Decide early: textured-but-blocky vs flat-shaded.
- **Caves/overhangs:** full 3D voxel decimation is hard. Treat distant terrain as
  a **surface heightmap** (you can't see into caves at range) — pairs naturally
  with "keep full height."
- **Rebuild cost** must stay off-thread; rings shift as you walk.

## Nvidium caveat

Nvidium's speed is **mesh shaders** (NVIDIA-leaning Vulkan extension), not plain
vertex shaders, and the dev iGPU (Intel UHD G1) almost certainly lacks them.
Borrow the *idea* (cheap distant geometry) but implement as **CPU-meshed LOD**
like DH, or it won't run on the test machine.

## Where it slots into VulkanMod (entry-point map)

Per-section meshing today:
- [`build/task/BuildTask.java`](../src/main/java/net/vulkanmod/render/chunk/build/task/BuildTask.java)
  `compile()` meshes one 16³ section into per-render-type `UploadBuffer`s →
  `CompiledSection` set on the `RenderSection`. An LOD path is an **alternate
  build task** (`LodBuildTask`) that reads block data across a *merged* region
  and emits a decimated surface-heightmap mesh.
- [`build/task/TaskDispatcher.java`](../src/main/java/net/vulkanmod/render/chunk/build/task/TaskDispatcher.java)
  schedules builds off-thread — reuse for LOD builds.
- [`RenderSection`](../src/main/java/net/vulkanmod/render/chunk/RenderSection.java) /
  [`SectionGrid`](../src/main/java/net/vulkanmod/render/chunk/SectionGrid.java) /
  [`ChunkArea`](../src/main/java/net/vulkanmod/render/chunk/ChunkArea.java) manage
  sections. LOD needs a **parallel grouping** of N×N sections into LOD regions.
- [`WorldRenderer.java`](../src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java)
  submits draws via `buildDrawBatchesIndirect` + `IndirectBuffer`. Render near
  sections full-res (skipping those an active LOD region covers) and LOD regions
  via their merged meshes through the **same indirect path**.

Vertex format: LOD meshes can reuse `COMPRESSED_TERRAIN` (16-byte, see
docs/VertexFormat.md) — no new pipeline, just fewer/decimated vertices.

## Suggested first slice (when resumed)

1. LOD region grouping structure (N×N sections beyond a ring) parallel to
   `SectionGrid`.
2. `LodBuildTask`: surface-heightmap mesh of a merged region, half horizontal
   res, full height, `COMPRESSED_TERRAIN` format, off-thread.
3. Render LOD regions in `WorldRenderer` alongside full-res near sections; skip
   full-res sections covered by an active LOD region.
4. Skirts at LOD borders.
5. Ring management on camera move.

Scope: centerpiece-tier — alternate meshing pipeline + LOD ring management +
render integration. Much bigger than motion blur.
