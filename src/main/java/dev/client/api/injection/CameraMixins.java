package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.other.CameraClipEvent;
import dev.client.api.nullcry.helper.entity.CameraOverriddenEntity;
import dev.client.modules.core.movement.freelook.FreeLookState;
import dev.client.modules.core.render.NoRender;
import dev.client.modules.core.render.SmoothCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixins {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Unique
    private boolean lumora$initialized = false;
    @Shadow
    private Vec3d pos;

    @Shadow private Entity focusedEntity;

    @Unique
    private float smoothYaw;
    @Unique
    private float smoothPitch;
    @Unique
    private double smoothOffsetX;
    @Unique
    private double smoothOffsetZ;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V", ordinal = 0))
    public void onUpdate(Args args) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Clip")) args.set(0, -3.5f);
    }

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", shift = At.Shift.AFTER))
    private void onUpdate(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {


        // Коррекция камеры для низкого роста: только в первом лице и только для малыша
        if (!thirdPerson && focusedEntity instanceof PlayerEntity player && player.isBaby()) {
            double vanillaEye = 1.62;
            double babyEye = 0.81;
            this.pos = this.pos.subtract(0.0, vanillaEye - babyEye, 0.0);
        }
        MinecraftClient mc = MinecraftClient.getInstance();

        // FreeLook: отдельная логика камеры
        if (mc.player != null && FreeLookState.active && mc.player instanceof CameraOverriddenEntity entity) {
            if (!lumora$initialized) {
                entity.setCameraPitch(mc.player.getPitch());
                entity.setCameraYaw(mc.player.getYaw());
                lumora$initialized = true;
            }
            setRotation(entity.getCameraYaw(), entity.getCameraPitch());
            return;
        }
        lumora$initialized = false;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdateSmoothCamera(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (SmoothCamera.INSTANCE != null && SmoothCamera.INSTANCE.isEnabled()) {
            SmoothCamera.INSTANCE.cameraUpdate(this.pos);
            if (SmoothCamera.INSTANCE.shouldApplyChanges()) {
                this.pos = SmoothCamera.INSTANCE.getSmoothPos();
            }
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    public void onClipToSpace(float distance, CallbackInfoReturnable<Float> cir) {
        CameraClipEvent event = new CameraClipEvent();
        EventManager.call(event);

        if (event.isCancelled()) {
            cir.setReturnValue(distance);
        }

        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Clip"))
            cir.setReturnValue(3.5f);
    }
}
