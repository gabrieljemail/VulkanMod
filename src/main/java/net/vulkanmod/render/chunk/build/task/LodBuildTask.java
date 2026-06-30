package net.vulkanmod.render.chunk.build.task;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.LodRegion;
import net.vulkanmod.render.chunk.build.RenderRegion;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Vector3d;

import java.util.concurrent.atomic.AtomicBoolean;

public class LodBuildTask extends ChunkTask {
    
    private final LodRegion lodRegion;
    private RenderRegion renderRegion;
    
    public LodBuildTask(LodRegion lodRegion, RenderRegion renderRegion, Vector3d cameraPos) {
        super(null, cameraPos); // RenderSection is null for LOD tasks
        this.lodRegion = lodRegion;
        this.renderRegion = renderRegion;
        this.highPriority = false;
    }

    @Override
    public String name() {
        return "rend_lod_rebuild";
    }

    @Override
    public Result runTask(BuilderResources builderResources) {
        if (this.cancelled.get()) {
            return Result.CANCELLED;
        }

        // Scaffold for LOD mesh generation:
        // 1. Read block data across the merged N×N region
        // 2. Generate a surface heightmap mesh (caves ignored)
        // 3. Halve horizontal resolution
        // 4. Keep full vertical height resolution
        // 5. Use COMPRESSED_TERRAIN vertex format
        
        CompileResult compileResult = new CompileResult(null, true);
        CompiledSection compiledSection = new CompiledSection();
        
        int divisor = net.voidsmp.client.addons.ChunkRes.DETAIL_DIVISOR;
        
        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; z += divisor) {
                for (int x = 0; x < 16; x += divisor) {
                    BlockState blockState = this.renderRegion.getBlockStateFast(x, y, z);
                    
                    if (blockState.isAir()) continue;

                    // TODO: Actual meshing logic utilizing TerrainBuilder
                    // Generate a flat-shaded quad spanning (divisor x divisor) horizontally
                }
            }
        }
        
        compileResult.compiledSection = compiledSection;
        
        if (this.cancelled.get()) {
            compileResult.renderedLayers.values().forEach(UploadBuffer::release);
            return Result.CANCELLED;
        }

        // Notify that the LOD region has been compiled
        this.lodRegion.setCompiledSection(compiledSection);
        this.lodRegion.setDirty(false);
        
        return Result.SUCCESSFUL;
    }
}
