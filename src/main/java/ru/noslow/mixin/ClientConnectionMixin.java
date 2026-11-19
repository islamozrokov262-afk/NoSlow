package ru.noslow.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.noslow.NoSlowMod;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void onReceivePacket(net.minecraft.network.handler.ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (NoSlowMod.noSlow != null) {
            if (NoSlowMod.noSlow.onReceivePacket(packet)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "sendImmediately", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, net.minecraft.network.listener.PacketListener listener, CallbackInfo ci) {
        if (NoSlowMod.noSlow != null) {
            if (NoSlowMod.noSlow.onSendPacket(packet)) {
                ci.cancel();
            }
        }
    }
}
