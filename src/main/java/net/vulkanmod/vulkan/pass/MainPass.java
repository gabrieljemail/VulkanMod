package net.vulkanmod.vulkan.pass;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public interface MainPass {

    void begin(VkCommandBuffer commandBuffer, MemoryStack stack);

    void end(VkCommandBuffer commandBuffer);

    void cleanUp();

    void onResize();

    default void mainTargetBindWrite() {}

    default void mainTargetUnbindWrite() {}

    default void rebindMainTarget() {}

    default void bindAsTexture() {}

    // [VoidClient] Apply the world-layer motion blur. Called from a Gui.render
    // HEAD hook — after the world, before the HUD — so the HUD stays sharp.
    default void applyMotionBlur() {}

    default Framebuffer getMainFramebuffer() {
        return null;
    }

    default GpuTexture getColorAttachment() {
        return null;
    }

    default GpuTextureView getColorAttachmentView() {
        return null;
    }

    default GpuTexture getDepthAttachment() {
        return null;
    }

}
