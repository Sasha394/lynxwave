package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.nullcry.cmdHelper.DispatchResult;
import dev.client.cmd.core.Cmd_Initializer;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.game.PickupItemEvent;
import dev.client.modules.core.combat.NoServerDesync;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixins {
    @Shadow private ClientWorld world;

    @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String text, CallbackInfo ci) {
        var dispatcher = LynxWave.getInstance().getCommandDispatcher();
        Cmd_Initializer initializer = LynxWave.getInstance().getCmdInitializer();
        String prefix = ".";
        if (initializer != null && initializer.getCommandDispatcher() != null) {
            prefix = initializer.getCommandDispatcher().getPrefix().get();
        }
        String normalized = text;
        if (prefix != null && !prefix.isEmpty() && text.startsWith("/" + prefix)) {
            normalized = text.substring(1);
        }
        if (dispatcher != null && dispatcher.dispatch(normalized) == DispatchResult.DISPATCHED) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"), cancellable = true)
    private void onPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        NoServerDesync noServerDesync = NoServerDesync.INSTANCE;
        if (noServerDesync == null || !noServerDesync.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null) return;

        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double x = packet.change().position().x;
        double y = packet.change().position().y;
        double z = packet.change().position().z;

        float yaw = packet.change().yaw();
        float pitch = packet.change().pitch();

        Set<PositionFlag> flags = packet.relatives();

        if (flags.contains(PositionFlag.X)) x += currentPos.x;
        if (flags.contains(PositionFlag.Y)) y += currentPos.y;
        if (flags.contains(PositionFlag.Z)) z += currentPos.z;

        if (flags.contains(PositionFlag.X_ROT)) pitch += player.getPitch();
        if (flags.contains(PositionFlag.Y_ROT)) yaw += player.getYaw();

        noServerDesync.sendRotationPacket(yaw, pitch);
        player.setPosition(x, y, z);

        ((ClientPlayNetworkHandler) (Object) this).sendPacket(new TeleportConfirmC2SPacket(packet.teleportId()));
        boolean onGround = player.isOnGround();
        boolean horizontalCollision = player.horizontalCollision;
        ((ClientPlayNetworkHandler) (Object) this).sendPacket(new PlayerMoveC2SPacket.Full(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(),
                onGround, horizontalCollision
        ));

        ci.cancel();
    }

    @Inject(method = "onItemPickupAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private void onItemPickupAnimationInvoke(ItemPickupAnimationS2CPacket packet, CallbackInfo ci) {
        Entity entity = this.world.getEntityById(packet.getEntityId());
        if (!(entity instanceof ItemEntity itemEntity)) return;

        LivingEntity livingEntity = (LivingEntity) this.world.getEntityById(packet.getCollectorEntityId());
        if (livingEntity == null) livingEntity = MinecraftClient.getInstance().player;

        ItemStack fullStack = itemEntity.getStack();
        if (fullStack.isEmpty()) return;

        ItemStack picked = fullStack.copy();
        int pickedAmount = Math.min(picked.getCount(), packet.getStackAmount());
        picked.setCount(pickedAmount);

        EventManager.call(new PickupItemEvent(picked, livingEntity, itemEntity));
    }
}
