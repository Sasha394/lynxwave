package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.other.NoPushEvent;
import dev.client.api.nullcry.events.core.player.PlayerAttackEvent;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixins extends LivingEntity {

    protected PlayerEntityMixins(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "isPushedByFluids", at = @At("HEAD"), cancellable = true)
    public void onIsPushedByFluids(CallbackInfoReturnable<Boolean> infoReturnable) {
        NoPushEvent noPushEvent = new NoPushEvent(NoPushEvent.CollisionEnum.Fluids);
        EventManager.call(noPushEvent);

        if ((Object) this instanceof ClientPlayerEntity && noPushEvent.isCancelled()) {
            infoReturnable.setReturnValue(false);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void attackAHook2(Entity target, CallbackInfo ci) {
        PlayerAttackEvent event = new PlayerAttackEvent(target);
        EventManager.call(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = {"knockbackTarget", "doSweepingAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"), require = 0)
    private float hookFixRotation(float original) {
        return RotationController.INSTANCE.getMoveRotation().getYaw();
    }
}
