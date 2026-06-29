package net.voidsmp.client.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.voidsmp.client.addons.Fullbright;
import net.vulkanmod.render.chunk.build.light.data.LightDataAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright for terrain. VulkanMod bakes block/sky light into chunk meshes via
 * {@link LightDataAccess#getLightmap(int)}; forcing it to FULL_BRIGHT lights
 * every block face at mesh-build time (cached, no per-frame cost). The emissive
 * path already returns FULL_BRIGHT and routes here for the non-emissive case.
 */
@Mixin(LightDataAccess.class)
public class LightDataAccessMixin {

    @Inject(method = "getLightmap(I)I", at = @At("HEAD"), cancellable = true)
    private static void voidFullbright(int word, CallbackInfoReturnable<Integer> cir) {
        if (Fullbright.ENABLED) {
            cir.setReturnValue(LightTexture.FULL_BRIGHT);
        }
    }
}
