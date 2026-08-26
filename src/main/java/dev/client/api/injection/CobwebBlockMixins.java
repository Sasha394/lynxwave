package dev.client.api.injection;

import dev.client.api.nullcry.modules.Module;
import dev.client.modules.core.player.NoWeb;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CobwebBlock.class)
public class CobwebBlockMixins {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean phantom, CallbackInfo ci) {
        if (NoWeb.INSTANCE.isEnabled() && NoWeb.INSTANCE.mode.isSelected("Ignore") && entity == MinecraftClient.getInstance().player) {
            ci.cancel();
            if (NoWeb.INSTANCE.grim.getEnabled())
                Module.sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, id));
        }
    }
}
