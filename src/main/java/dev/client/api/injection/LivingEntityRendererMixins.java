package dev.client.api.injection;

import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.combat.killaura.rotation.RotationController;
import dev.client.modules.core.render.SeeInvisible;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixins implements ClientApi {
    @Unique private static final ThreadLocal<LivingEntityRenderState> LIVING_RENDER_STATE = new ThreadLocal<>();

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"))
    private void onRenderHead(LivingEntityRenderState renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        LIVING_RENDER_STATE.set(renderState);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("RETURN"))
    private void onRenderReturn(LivingEntityRenderState renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        LIVING_RENDER_STATE.remove();
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;mix(II)I"), index = 0)
    private int onModifyInvisibleAlpha(int original) {
        LivingEntityRenderState renderState = LIVING_RENDER_STATE.get();
        if (renderState != null && SeeInvisible.INSTANCE.isEnabled() && renderState.invisible && !renderState.invisibleToPlayer) {
            Float sliderValue = SeeInvisible.INSTANCE.transparency.getValue();
            if (sliderValue != null) {
                float clampedValue = MathHelper.clamp(sliderValue, 0.0f, 1.0f);
                int alpha = MathHelper.clamp(Math.round(clampedValue * 255.0f), 0, 255);
                return ColorHelper.getArgb(alpha, 255, 255, 255);
            }
        }
        return original;
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void onUpdateRenderState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        RotationController controller = RotationController.INSTANCE;
        if (entity == mc.player && controller != null && mc.player != null) {
            if (!(mc.currentScreen instanceof HandledScreen)) {
                float playerYaw = mc.player.getYaw();
                float targetYaw = controller.getRotation().getYaw();
                float prevTargetYaw = controller.getPreviousRotation().getYaw();
                if (targetYaw != playerYaw || prevTargetYaw != playerYaw) {
                    float lerpedRotationYaw = MathHelper.lerpAngleDegrees(tickDelta, prevTargetYaw, targetYaw);
                    state.bodyYaw = lerpedRotationYaw;
                }
            }
        }
    }
}
