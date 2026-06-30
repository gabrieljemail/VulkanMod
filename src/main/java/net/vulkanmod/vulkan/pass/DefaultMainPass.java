package net.vulkanmod.vulkan.pass;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.voidsmp.client.addons.MotionBlur;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanmod.vulkan.texture.ImageUtil;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;

import java.util.function.IntSupplier;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.*;

public class DefaultMainPass implements MainPass {

    public static DefaultMainPass create() {
        return new DefaultMainPass();
    }

    private Framebuffer mainFramebuffer;

    private RenderPass mainRenderPass;
    private RenderPass auxRenderPass;

    private GpuTexture[] colorAttachmentTextures;
    private GpuTextureView[] colorAttachmentTextureViews;
    IntSupplier imageIdxSupplier;
    private GpuTexture depthAttachmentTexture;

    // [VoidClient] Motion blur. Cache one intermediate image per distinct (w,h)
    // so changing blur axis/strength between frames never reallocates (that
    // reallocation caused the stutter while flicking). Freed on resize/cleanup.
    private final java.util.HashMap<Long, VulkanImage> motionBlurImages = new java.util.HashMap<>();

    DefaultMainPass() {
        createResources();
    }

    private void createResources() {
        if (this.mainFramebuffer != null) {
            if (this.mainFramebuffer != Renderer.getInstance()
                                                .getSwapChain()) {
                this.mainFramebuffer.cleanUp(true);
            }

            this.mainRenderPass.cleanUp();
            this.auxRenderPass.cleanUp();
        }

        Framebuffer framebuffer;
        if (Renderer.getInstance().getSwapChain().hasImages()) {
            framebuffer = Renderer.getInstance().getSwapChain();
        }
        else {
            framebuffer = Framebuffer.builder(10, 10, 1, true)
                                     .build();
        }

        this.mainFramebuffer = framebuffer;

        createRenderPasses();
        createAttachmentTextures();
    }

    private void createRenderPasses() {
        RenderPass.Builder builder = RenderPass.builder(this.mainFramebuffer);
        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_STORE_OP_STORE);

        this.mainRenderPass = builder.build();

        // Create an auxiliary RenderPass needed in case of main target rebinding
        builder = RenderPass.builder(this.mainFramebuffer);
        builder.getColorAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getDepthAttachmentInfo().setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE);
        builder.getColorAttachmentInfo().setFinalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        this.auxRenderPass = builder.build();
    }

    @Override
    public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
        Framebuffer framebuffer = this.mainFramebuffer;

        VulkanImage colorAttachment = framebuffer.getColorAttachment();
        colorAttachment.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        Renderer.getInstance().beginRenderPass(this.mainRenderPass, framebuffer);

        Renderer.setViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight(), stack);

        VkRect2D.Buffer pScissor = framebuffer.scissor(stack);
        vkCmdSetScissor(commandBuffer, 0, pScissor);
    }

    @Override
    public void end(VkCommandBuffer commandBuffer) {
        Renderer.getInstance().endRenderPass(commandBuffer);

        if (this.mainFramebuffer == Renderer.getInstance().getSwapChain()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                this.mainFramebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            }
        }

        int result = vkEndCommandBuffer(commandBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer:" + result);
        }
    }

    @Override
    public void cleanUp() {
        freeMotionBlurHistory();
        this.mainRenderPass.cleanUp();
        this.auxRenderPass.cleanUp();
    }

    @Override
    public void onResize() {
        freeMotionBlurHistory();
        createResources();
    }

    // [VoidClient] One extra fullscreen blit of the finished frame into an
    // offscreen image. Purely to measure the GPU cost of a single fullscreen
    // pass on this hardware — the result is never read back or displayed, and
    // the swapchain image is only used as a blit source (read-only), so the
    // visible frame is unchanged. Toggling Motion Blur on/off and watching the
    // FPS delta gives the real cost ceiling for a single-pass post effect.
    // Shader-free directional blur (v2): downsample the finished frame along ONE
    // axis into a narrow image with linear filtering, then upsample back — the
    // bilinear averaging along that axis is a directional smear. The axis and
    // strength come from camera rotation (MotionBlur), so it blurs horizontally
    // while turning and vertically while looking up/down, only while moving.
    // Arbitrary per-pixel angles still need a fragment shader (v3); this reuses
    // the proven blit path. NB: still runs at the present hook, so it also
    // smears the HUD — fixing that needs an earlier, pre-GUI hook point.
    private static final int MOTION_BLUR_MAX_DOWNSCALE = 8;
    private static final float MOTION_BLUR_THRESHOLD = 0.04f;

    @Override
    public void applyMotionBlur() {
        if (!MotionBlur.ENABLED || MotionBlur.STRENGTH <= MOTION_BLUR_THRESHOLD) {
            return; // disabled, or not moving enough to bother
        }
        if (net.minecraft.client.Minecraft.getInstance().screen != null) {
            return; // not in a menu
        }
        if (this.mainFramebuffer != Renderer.getInstance().getSwapChain()) {
            return;
        }
        VulkanImage color = this.mainFramebuffer.getColorAttachment();
        int fw = this.mainFramebuffer.getWidth();
        int fh = this.mainFramebuffer.getHeight();

        // Stronger turn -> more downscale along the blur axis -> longer smear.
        // Quantised so the intermediate image isn't reallocated every frame.
        int level = 2 + Math.round(MotionBlur.STRENGTH * (MOTION_BLUR_MAX_DOWNSCALE - 2));
        int w = MotionBlur.HORIZONTAL ? Math.max(1, fw / level) : fw;
        int h = MotionBlur.HORIZONTAL ? fh : Math.max(1, fh / level);

        VulkanImage history = motionBlurImage(w, h);
        ImageUtil.blitFramebuffer(color, history, VK_FILTER_LINEAR); // downsample along axis
        ImageUtil.blitFramebuffer(history, color, VK_FILTER_LINEAR); // upsample -> directional smear
    }

    private VulkanImage motionBlurImage(int width, int height) {
        long key = (((long) width) << 32) | (height & 0xFFFFFFFFL);
        VulkanImage image = this.motionBlurImages.get(key);
        if (image == null) {
            // Builder's default RGBA8, not the swapchain's: BGRA swapchains
            // (Intel, format 44) aren't handled by the Builder. vkCmdBlitImage
            // converts formats and this image is never displayed, so channel
            // order doesn't matter.
            image = new VulkanImage.Builder(width, height)
                .setName("VoidClient Motion Blur " + width + "x" + height)
                .createVulkanImage();
            this.motionBlurImages.put(key, image);
        }
        return image;
    }

    private void freeMotionBlurHistory() {
        for (VulkanImage image : this.motionBlurImages.values()) {
            image.free();
        }
        this.motionBlurImages.clear();
    }

    public void rebindMainTarget() {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        // Do not rebind if the framebuffer is already bound
        RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
        if (boundRenderPass == this.mainRenderPass || boundRenderPass == this.auxRenderPass)
            return;

        Renderer.getInstance().endRenderPass(commandBuffer);
        Renderer.getInstance().beginRenderPass(this.auxRenderPass, this.mainFramebuffer);
    }

    @Override
    public void bindAsTexture() {
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        // Check if render pass is using the framebuffer
        RenderPass boundRenderPass = Renderer.getInstance().getBoundRenderPass();
        if (boundRenderPass == this.mainRenderPass || boundRenderPass == this.auxRenderPass)
            Renderer.getInstance().endRenderPass(commandBuffer);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.mainFramebuffer.getColorAttachment().transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }

        VTextureSelector.bindTexture(this.mainFramebuffer.getColorAttachment());
    }

    @Override
    public Framebuffer getMainFramebuffer() {
        return mainFramebuffer;
    }

    @Override
    public GpuTexture getColorAttachment() {
        return this.colorAttachmentTextures[this.imageIdxSupplier.getAsInt()];
    }

    @Override
    public GpuTextureView getColorAttachmentView() {
        return this.colorAttachmentTextureViews[this.imageIdxSupplier.getAsInt()];
    }

    @Override
    public GpuTexture getDepthAttachment() {
        return this.depthAttachmentTexture;
    }

    private void createAttachmentTextures() {
        VkGpuDevice device = (VkGpuDevice) RenderSystem.getDevice();

        SwapChain swapChain = Renderer.getInstance().getSwapChain();
        if (this.mainFramebuffer == swapChain) {
            var swapChainImages = swapChain.getImages();

            int imageCount = swapChainImages.size();
            this.colorAttachmentTextures = new GpuTexture[imageCount];
            this.colorAttachmentTextureViews = new GpuTextureView[imageCount];

            for (int i = 0; i < imageCount; ++i) {
                VkGpuTexture attachmentTexture = device.gpuTextureFromVulkanImage(swapChainImages.get(i));
                GpuTextureView attachmentTextureView = device.createTextureView(attachmentTexture);
                this.colorAttachmentTextures[i] = attachmentTexture;
                this.colorAttachmentTextureViews[i] = attachmentTextureView;
            }

            this.imageIdxSupplier = Renderer::getCurrentImage;
        }
        else {
            this.colorAttachmentTextures = new GpuTexture[1];
            this.colorAttachmentTextureViews = new GpuTextureView[1];

            VkGpuTexture attachmentTexture = device.gpuTextureFromVulkanImage(this.mainFramebuffer.getColorAttachment());
            GpuTextureView attachmentTextureView = device.createTextureView(attachmentTexture);
            this.colorAttachmentTextures[0] = attachmentTexture;
            this.colorAttachmentTextureViews[0] = attachmentTextureView;

            // Always return idx 0 as there's only 1 image
            this.imageIdxSupplier = () -> 0;
        }

        this.depthAttachmentTexture = device.gpuTextureFromVulkanImage(this.mainFramebuffer.getDepthAttachment());
    }
}
