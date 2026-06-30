package net.voidsmp.client.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Mixin(CompoundTag.class)
public class CompoundTagMixin {

    @Shadow @Final private Map<String, Tag> tags;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void singleLookupGet(String key, CallbackInfoReturnable<Tag> cir) {
        // Direct map bypass: cuts hash checks in half for every NBT packet parse
        cir.setReturnValue(this.tags.get(key));
    }
}