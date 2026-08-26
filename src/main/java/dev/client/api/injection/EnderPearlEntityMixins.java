package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.player.PlayerDamageReceivedEvent;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderPearlEntity.class)
public class EnderPearlEntityMixins {

    @Inject(method = "onCollision", at = @At("HEAD"))
    public void onCollisionHead(HitResult hitResult, CallbackInfo ci) {
        EventManager.call(new PlayerDamageReceivedEvent(PlayerDamageReceivedEvent.DamageType.ENDER_PEARL));
    }
}
