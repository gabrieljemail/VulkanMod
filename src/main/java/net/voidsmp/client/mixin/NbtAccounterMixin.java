package net.voidsmp.client.mixin;

import net.minecraft.nbt.NbtAccounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(NbtAccounter.class)
public class NbtAccounterMixin {

    // Overrides vanilla's hard limit for max NBT bytes allowed per packet allocation
    @ModifyConstant(method = "<init>", constant = @Constant(longValue = 2097152L))
    private long breakAllocationWall(long originalLimit) {
        // Raise threshold to 16MB to comfortably handle heavy mod payloads
        return 16777216L;
    }
}