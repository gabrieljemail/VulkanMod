package net.voidsmp.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the motion-blur pass at the start of HUD rendering — i.e. after the
 * world is drawn but before the HUD composites on top — so only the world
 * layer is blurred, not the UI. The pass itself lives in DefaultMainPass; this
 * just triggers it at the right point in the frame.
 */
@Mixin(Gui.class)
public class MotionBlurGuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void applyWorldMotionBlur(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Renderer.getInstance().getMainPass().applyMotionBlur();
    }
}
