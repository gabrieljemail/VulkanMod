package net.vulkanmod.render.chunk;

import net.minecraft.world.level.Level;

/**
 * Manages LodRegions (similar to how SectionGrid manages RenderSections).
 * Tracks a grid of LOD regions around the camera.
 */
public class LodGrid {
    
    protected final Level level;
    protected int gridWidth;
    protected int gridHeight;
    public LodRegion[] regions;
    
    private final int regionSize; // N×N sections per LodRegion
    
    public LodGrid(Level level, int renderDistance, int regionSize) {
        this.level = level;
        this.regionSize = regionSize;
        
        // Example: if renderDistance is 12 chunks, and regionSize is 2,
        // grid width is (12 * 2 + 1) / 2
        this.gridWidth = (renderDistance * 2 + 1) / regionSize;
        if (this.gridWidth < 1) this.gridWidth = 1;
        
        // Full height resolution -> height isn't decimated, so we just group horizontally,
        // but maybe we group vertically too? The plan says "keep full vertical resolution".
        // Let's assume LodRegion handles a vertical column of chunks for surface heightmap,
        // or we just divide gridHeight by regionSize if it's a 3D grid.
        // For now, let's just make it a flat 2D grid of regions since caves are ignored.
        this.gridHeight = 1; 
        
        createRegions();
    }
    
    protected void createRegions() {
        int size = this.gridWidth * this.gridHeight * this.gridWidth;
        this.regions = new LodRegion[size];
        
        for (int x = 0; x < this.gridWidth; ++x) {
            for (int z = 0; z < this.gridWidth; ++z) {
                int index = (z * this.gridHeight + 0) * this.gridWidth + x;
                this.regions[index] = new LodRegion(x * 16 * regionSize, z * 16 * regionSize, regionSize);
            }
        }
    }
    
    public void repositionCamera(double x, double z) {
        // TODO: Update ring boundaries based on camera position
        // Similar circular-list logic to SectionGrid, but for regions
    }
}
