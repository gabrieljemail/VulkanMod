package net.vulkanmod.render.chunk;

import net.minecraft.world.level.Level;
import net.vulkanmod.render.chunk.build.task.CompiledSection;
import org.joml.Vector3d;

/**
 * A region of merged sections for Chunk LOD rendering.
 * Groups N×N sections into a single mesh with decimated horizontal geometry.
 */
public class LodRegion {
    public final int xOffset;
    public final int zOffset;
    public final int size; // How many chunks wide this region is (e.g., 2)
    
    private CompiledSection compiledSection = CompiledSection.UNCOMPILED;
    public short lastFrame = -1;
    
    private boolean dirty = true;
    public long visibility;

    public LodRegion(int xOffset, int zOffset, int size) {
        this.xOffset = xOffset;
        this.zOffset = zOffset;
        this.size = size;
    }
    
    public void setOrigin(int x, int z) {
        this.dirty = true;
        this.compiledSection = CompiledSection.UNCOMPILED;
    }

    public boolean isCompiled() {
        return this.compiledSection != CompiledSection.UNCOMPILED;
    }

    public CompiledSection getCompiledSection() {
        return compiledSection;
    }

    public void setCompiledSection(CompiledSection compiledSection) {
        this.compiledSection = compiledSection;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    
    public boolean isDirty() {
        return dirty;
    }
    
    public boolean setLastFrame(short frame) {
        boolean alreadySet = frame == this.lastFrame;
        if (!alreadySet) {
            this.lastFrame = frame;
        }
        return alreadySet;
    }
    
    public short getLastFrame() {
        return this.lastFrame;
    }
}
