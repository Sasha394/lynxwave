package dev.client.api.injection;

import com.mojang.authlib.GameProfile;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.game.UseItemEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixins extends PlayerEntity {
    @Shadow public ServerPlayNetworkHandler networkHandler;

    protected ServerPlayerEntityMixins(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "consumeItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;consumeItem()V"))
    private void onConsumeItem(CallbackInfo ci) {
        if (this.activeItemStack != null && !this.activeItemStack.isEmpty()) {
            EventManager.call(new UseItemEvent(this, this.activeItemStack.copy()));
        }
    }
}
