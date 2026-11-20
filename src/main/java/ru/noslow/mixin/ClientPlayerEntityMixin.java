package ru.noslow.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec2f;
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

    @WrapOperation(method = "applyMovementSpeedFactors", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec2f;multiply(F)Lnet/minecraft/util/math/Vec2f;", ordinal = 1))
    private Vec2f onApplyMovementSpeedFactors(Vec2f instance, float multiplier, Operation<Vec2f> original) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (NoSlowMod.noSlow != null && NoSlowMod.noSlow.shouldApplyNoSlow(player) && player.isUsingItem()) {
            return original.call(instance, 1.0F); // Просто убираем замедление, никаких добавок
        }
        return original.call(instance, multiplier);
    }
}
