package net.voidsmp.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.voidsmp.client.addons.Fullbright;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright for entities, items, particles and block entities, which sample
 * the lightmap through vanilla {@link LevelRenderer#getLightColor}. Terrain
 * goes through VulkanMod's own pipeline instead (see LightDataAccessMixin).
 */
@Mixin(LevelRenderer.class)
public class LightColorMixin {

    @Inject(
        method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void voidFullbright(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (Fullbright.ENABLED) {
            cir.setReturnValue(LightTexture.FULL_BRIGHT);
        }
    }
}
