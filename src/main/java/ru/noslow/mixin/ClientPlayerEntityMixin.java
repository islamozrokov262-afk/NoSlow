package ru.noslow.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.noslow.NoSlowMod;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "isBlockedFromSprinting", at = @At("HEAD"), cancellable = true)
    private void onIsBlockedFromSprinting(CallbackInfoReturnable<Boolean> cir) {
        if (NoSlowMod.noSlow != null && NoSlowMod.noSlow.shouldApplyNoSlow((ClientPlayerEntity)(Object)this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getActiveItemSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void onGetActiveItemSpeedMultiplier(CallbackInfoReturnable<Float> cir) {
        if (NoSlowMod.noSlow != null && NoSlowMod.noSlow.shouldApplyNoSlow((ClientPlayerEntity)(Object)this)) {
            cir.setReturnValue(1.0F);
        }
    }
}
