package net.voidsmp.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public class SplashOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private long fadeOutStart;

    /**
     * Recolor the loading-screen background. Keeps the original alpha (so fade-in
     * still works), swaps RGB for Void Client's purple.
     */
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
        index = 4
    )
    private int voidPurpleBackground(int color) {
        return (color & 0xFF000000) | 0x1D1128;
    }

    /**
     * Skip the fade-out. Vanilla {@code tick()} already calls onFinish and sets
     * fadeOutStart once the reload is done; at that point we just remove the overlay
     * immediately instead of rendering the ~1s fade.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void skipFadeOut(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.fadeOutStart > -1L) {
            this.minecraft.setOverlay(null);
            ci.cancel();
        }
    }
}
