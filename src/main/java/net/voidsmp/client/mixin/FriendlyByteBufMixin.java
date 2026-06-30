package net.voidsmp.client.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FriendlyByteBuf.class)
public class FriendlyByteBufMixin {

    @Inject(method = "readNbt", at = @At("RETURN"), cancellable = true)
    private void sanitizeIncomingNBT(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null) {
            // Remove ForgeCaps globally
            if (tag.contains("ForgeCaps")) tag.remove("ForgeCaps");
            
            // Handle the modern Optional structure safely using lambda expressions
            tag.getCompound("display").ifPresent(display -> display.remove("Lore"));
            tag.getCompound("BlockEntityTag").ifPresent(blockEntity -> blockEntity.remove("Items"));
        }
    }
}