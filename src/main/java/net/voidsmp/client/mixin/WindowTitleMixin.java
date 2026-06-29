package net.voidsmp.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class WindowTitleMixin {

    @Inject(method = "updateTitle", at = @At("RETURN"))
    private void overrideWindowTitle(CallbackInfo ci) {
        ((Minecraft) (Object) this).getWindow().setTitle("Void Client | 1.21.11");
    }
}
