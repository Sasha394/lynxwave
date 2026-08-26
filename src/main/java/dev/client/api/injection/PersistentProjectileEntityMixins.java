package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.player.PlayerDamageReceivedEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixins {

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void onEntityHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        Entity hitEntity = entityHitResult.getEntity();

        if (hitEntity instanceof net.minecraft.client.network.ClientPlayerEntity) {
            EventManager.call(new PlayerDamageReceivedEvent(PlayerDamageReceivedEvent.DamageType.ARROW));
        }
    }
}
