package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.engine.VkRenderPass;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow @Final private GuiRenderState renderState;
    @Shadow private @Nullable GpuTextureView itemsAtlasView;

    @SuppressWarnings("rawtypes")
    @Shadow @Final private List draws;
    @Shadow private int firstDrawIndexAfterBlur;

    // [VoidClient] Retained HUD draw caching. Vanilla rebuilds every GUI vertex and
    // re-uploads it into the ring buffers each frame even when nothing on screen
    // changed. The element render states are value-equal records, so after item/
    // text/PiP preparation and sorting we can fingerprint the frame: if the element
    // list matches the previous frame, the vertex data would come out identical, so
    // we skip mesh building + upload, replay last frame's Draw records, and skip
    // the ring-buffer rotation so the data they reference stays untouched. Dynamic
    // content stays live because it is prepared BEFORE the fingerprint hook and
    // lives in textures (item atlas animations, PiP renders, glyph atlas) that the
    // replayed draws keep sampling.
    @Unique private ObjectArrayList<GuiElementRenderState> prevElements = new ObjectArrayList<>();
    @Unique private ObjectArrayList<GuiElementRenderState> currElements = new ObjectArrayList<>();
    @Unique private final List<Object> cachedDraws = new ArrayList<>();
    @Unique private int cachedFirstDrawIndexAfterBlur;
    @Unique private boolean reuseCachedDraws;

    @SuppressWarnings("unchecked")
    @Inject(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;sortElements(Ljava/util/Comparator;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void hudCacheCheck(CallbackInfo ci) {
        if (!Initializer.CONFIG.hudCache) {
            this.prevElements.clear();
            this.cachedDraws.clear();
            this.reuseCachedDraws = false;
            return;
        }

        ObjectArrayList<GuiElementRenderState> current = this.currElements;
        current.clear();
        this.renderState.forEachElement(current::add, GuiRenderState.TraverseRange.ALL);

        if (elementsMatch(this.prevElements, current)) {
            this.draws.addAll(this.cachedDraws);
            this.firstDrawIndexAfterBlur = this.cachedFirstDrawIndexAfterBlur;
            this.reuseCachedDraws = true;
            ci.cancel();
            return;
        }

        this.currElements = this.prevElements;
        this.prevElements = current;
        this.reuseCachedDraws = false;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "prepare", at = @At("RETURN"))
    private void hudCacheCapture(CallbackInfo ci) {
        if (this.reuseCachedDraws || !Initializer.CONFIG.hudCache)
            return;

        this.cachedDraws.clear();
        this.cachedDraws.addAll(this.draws);
        this.cachedFirstDrawIndexAfterBlur = this.firstDrawIndexAfterBlur;
    }

    // On a cache-hit frame nothing was written, and the cached draws still point at
    // the current ring buffer contents; rotating would let the next miss frame map
    // and overwrite the very buffer the GPU is reading for this frame's draws.
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MappableRingBuffer;rotate()V"))
    private void skipRotateOnCachedFrame(MappableRingBuffer instance) {
        if (!this.reuseCachedDraws)
            instance.rotate();
    }

    @Unique
    private static boolean elementsMatch(ObjectArrayList<GuiElementRenderState> a, ObjectArrayList<GuiElementRenderState> b) {
        int size = a.size();
        if (size != b.size())
            return false;

        for (int i = 0; i < size; i++) {
            if (!a.get(i).equals(b.get(i)))
                return false;
        }
        return true;
    }

//    // Debug
//    @Redirect(method = "method_71055", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;isAnimated()Z"))
//    private boolean forceRender(TrackingItemStackRenderState instance) {
//        return true;
//    }

    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"), cancellable = true)
    private void submitBlitFromItemAtlas(GuiItemRenderState guiItemRenderState, float u, float v, int size, int atlasSize,
                                         CallbackInfo ci) {
        v = 1.0f - v;
        float u1 = u + (float)size / atlasSize;
        float v1 = v + (float)(size) / atlasSize;
        this.renderState
                .submitBlitToCurrentLayer(
                        new BlitRenderState(
                                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                                TextureSetup.singleTexture(this.itemsAtlasView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                                guiItemRenderState.pose(),
                                guiItemRenderState.x(),
                                guiItemRenderState.y(),
                                guiItemRenderState.x() + 16,
                                guiItemRenderState.y() + 16,
                                u,
                                u1,
                                v,
                                v1,
                                -1,
                                guiItemRenderState.scissorArea(),
                                null
                        )
                );

        ci.cancel();
    }

    @Redirect(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setIndexBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V"))
    private void removeIndexBuffer(RenderPass instance, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
        // This draw method forces quad index buffer, not allowing other draw modes
        // Not binding it here will allow for a proper index  selection in lower level methods
    }

    @Redirect(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"))
    private void useVertexCount(RenderPass renderPass, int baseVertex, int firstIndex, int indexCount, int instanceCount) {
        // For the same reason here we need to use vertexCount instead of indexCount

        VkRenderPass vkRenderPass = (VkRenderPass) renderPass;
        if (vkRenderPass.getPipeline().getVertexFormatMode() != VertexFormat.Mode.TRIANGLES) {
            int vertexCount = indexCount * 2 / 3;
            renderPass.drawIndexed(baseVertex, 0, vertexCount, 1);
        }
        else {
            renderPass.drawIndexed(baseVertex, 0, indexCount, 1);
        }
    }

}
