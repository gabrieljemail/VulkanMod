package net.voidsmp.client.mixin;

import net.minecraft.client.Minecraft;
import net.voidsmp.client.addons.FastInputAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives {@link FastInputAddon#pollIfDue()} once per rendered frame — the
 * same injection point VulkanMod's own frame mixin uses (runTick fires every
 * frame, not every tick).
 */
@Mixin(Minecraft.class)
public class FastInputMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void voidclient$pollFastInput(boolean renderLevel, CallbackInfo ci) {
        FastInputAddon.INSTANCE.pollIfDue();
    }
}
