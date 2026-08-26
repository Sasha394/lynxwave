package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.network.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.zip.DataFormatException;

@Mixin(ClientConnection.class)
public class ClientConnectionMixins {

    @Inject(method = "exceptionCaught(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V", at = @At("HEAD"), cancellable = true)
    private void onExceptionCaught(ChannelHandlerContext context, Throwable cause, CallbackInfo ci) {
        if (cause instanceof DecoderException decoderEx) {
            Throwable inner = decoderEx.getCause();
            if (inner instanceof DataFormatException dataEx && "incorrect header check".equals(dataEx.getMessage())) {
                ci.cancel();
                ClientConnection self = (ClientConnection) (Object) this;
                Text message = Text.literal("Ошибка сжатия данных от сервера. Попробуйте переподключиться или другой сервер.");
                self.disconnect(message);
            }
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onChannelRead0(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketEvent event = new PacketEvent(packet, PacketEvent.Type.RECEIVE);
        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        if (LynxWave.silentPackets.contains(packet)) {
            LynxWave.silentPackets.remove(packet);
            return;
        }

        PacketEvent event = new PacketEvent(packet, PacketEvent.Type.SEND);
        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
