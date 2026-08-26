package dev.client.api.injection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.client.api.compat.EntityCompat;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.input.MovementEvent;
import dev.client.api.nullcry.events.core.input.PostMovementEvent;
import dev.client.api.nullcry.events.core.player.PlayerVelocityStrafeEvent;
import dev.client.api.nullcry.helper.entity.CameraOverriddenEntity;
import dev.client.api.nullcry.helper.entity.ITrailEntity;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.render.SeeInvisible;
import dev.client.modules.core.render.Trails;
import dev.client.modules.core.movement.freelook.FreeLookState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixins implements ITrailEntity, CameraOverriddenEntity {
    @Shadow public abstract void setVelocity(Vec3d velocity);
    @Shadow public abstract float getYaw();
    @Shadow public abstract Vec3d getVelocity();
    @Shadow public abstract float getYaw(float tickDelta);
    @Shadow public abstract EntityType<?> getType();
    @Unique private double preX;
    @Unique private double preZ;
    @Unique private final List<Trails.Trail> lumora$trails = new ArrayList<>();
    @Unique private Vec3d lumora$lastTrailPos;
    @Unique private float lumora$cameraYaw;
    @Unique private float lumora$cameraPitch;

    @ModifyExpressionValue(
            method = "move",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"),
            require = 0
    )
    private boolean onFixFallDistance(boolean original) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            return false;
        }

        return original;
    }

    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;movementInputToVelocity(Lnet/minecraft/util/math/Vec3d;FF)Lnet/minecraft/util/math/Vec3d;"))
    public Vec3d hookVelocity(Vec3d movementInput, float speed, float yaw) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            PlayerVelocityStrafeEvent event = new PlayerVelocityStrafeEvent(movementInput, speed, yaw, Entity.movementInputToVelocity(movementInput, speed, yaw));
            EventManager.call(event);
            return event.getVelocity();
        }
        return Entity.movementInputToVelocity(movementInput, speed, yaw);
    }

    @ModifyExpressionValue(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d onMoveVariable(Vec3d movement) {
        Entity self = (Entity) (Object) this;

        MovementEvent moveEvent = new MovementEvent(
                EntityCompat.pos(self),
                EntityCompat.pos(self).add(movement),
                movement,
                self.isOnGround(),
                self.horizontalCollision,
                self.verticalCollision,
                self.getBoundingBox()
        );

        EventManager.call(moveEvent);

        if (moveEvent.isCancelled()) {
            return Vec3d.ZERO;
        }

        return moveEvent.getMotion();
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void onMoveHead(MovementType type, Vec3d movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        this.preX = self.getX();
        this.preZ = self.getZ();
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double deltaX, double deltaY, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (FreeLookState.active && self instanceof ClientPlayerEntity) {
            lumora$cameraYaw += (float) deltaX * 0.15F;
            lumora$cameraPitch = MathHelper.clamp(lumora$cameraPitch + (float) deltaY * 0.15F, -90.0F, 90.0F);
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("TAIL"))
    private void onMoveTail(MovementType type, Vec3d movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        if (self instanceof ClientPlayerEntity) {
            double deltaX = self.getX() - this.preX;
            double deltaZ = self.getZ() - this.preZ;

            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            PostMovementEvent event = new PostMovementEvent(distance);
            EventManager.call(event);
        }
    }

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void onIsInvisibleTo(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (SeeInvisible.INSTANCE.isEnabled()) {
            if (this.getType() != EntityType.ARMOR_STAND) {
                cir.setReturnValue(false);
            }
        }
    }

    @ModifyVariable(method = "getRotationVector(FF)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float modifyPitch(float pitch) {
        if ((Object) this instanceof ClientPlayerEntity && RotationController.INSTANCE.getCurrentAngle() !=null) {
            return RotationController.INSTANCE.getCurrentAngle().getPitch();
        }
        return pitch;
    }

    @Override
    public List<Trails.Trail> lumora$getTrails() {
        return lumora$trails;
    }

    @Override
    public Vec3d lumora$getLastTrailPos() {
        return lumora$lastTrailPos;
    }

    @Override
    public void lumora$setLastTrailPos(Vec3d pos) {
        this.lumora$lastTrailPos = pos;
    }

    @Override
    public float getCameraYaw() {
        return lumora$cameraYaw;
    }

    @Override
    public float getCameraPitch() {
        return lumora$cameraPitch;
    }

    @Override
    public void setCameraYaw(float yaw) {
        lumora$cameraYaw = yaw;
    }

    @Override
    public void setCameraPitch(float pitch) {
        lumora$cameraPitch = pitch;
    }
}
