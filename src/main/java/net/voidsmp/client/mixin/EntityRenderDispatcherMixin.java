package net.voidsmp.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void cullDistantEntityMath(Entity entity, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        double distanceSq = entity.distanceToSqr(camX, camY, camZ);
        
        if (distanceSq > 1024.0) { // 32 blocks.
            // Divide update speed by 3.
            if (net.minecraft.client.Minecraft.getInstance().level.getGameTime() % 3 != 0) {
                cir.setReturnValue(false);
            }
        }
    }
}