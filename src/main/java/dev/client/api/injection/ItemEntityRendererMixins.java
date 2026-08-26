package dev.client.api.injection;

import dev.client.modules.core.render.ItemPhysic;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * Порт {@code rich.mixin.MixinItemEntityRenderer} для Yarn 1.21.11
 * ({@link OrderedRenderCommandQueue} + render-state based item rendering).
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixins {

    @Unique
    private static final WeakHashMap<ItemEntityRenderState, Boolean> lynxwave$groundState = new WeakHashMap<>();

    @Unique
    private ItemEntityRenderState lynxwave$itemPhysicState;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void lynxwave$captureGround(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        lynxwave$groundState.put(state, entity.isOnGround());
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void lynxwave$beginRender(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        lynxwave$itemPhysicState = state;
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN")
    )
    private void lynxwave$endRender(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        lynxwave$itemPhysicState = null;
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0)
    )
    private void lynxwave$redirectTranslate(MatrixStack matrices, float x, float y, float z) {
        ItemPhysic mod = ItemPhysic.INSTANCE;
        ItemEntityRenderState state = lynxwave$itemPhysicState;

        if (mod != null && mod.physicEnabled() && state != null) {
            float modelMinY = lynxwave$modelMinY(state);
            float f = -modelMinY + 0.0625F;
            matrices.translate(x, f, z);
        } else {
            matrices.translate(x, y, z);
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V"
            )
    )
    private void lynxwave$redirectRenderStack(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            net.minecraft.client.render.entity.state.ItemStackEntityRenderState stackState,
            Random random,
            Box box
    ) {
        ItemPhysic mod = ItemPhysic.INSTANCE;
        ItemEntityRenderState state = lynxwave$itemPhysicState;

        if (mod != null && mod.physicEnabled() && state != null) {
            float age = state.age;
            float offset = state.uniqueOffset;

            boolean isOnGround = lynxwave$groundState.getOrDefault(state, false);

            float rotation = ItemEntity.getRotation(age, offset);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-rotation));

            if (isOnGround) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                float yOffset = (float) box.getLengthY() / 2.0f;
                matrices.translate(0, -yOffset + 0.0625f, 0);
            } else {
                float spinSpeed = 15.0f;
                float itemRotation = (age * spinSpeed + offset * 360.0f) % 360.0f;
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(itemRotation));
            }
        }

        ItemEntityRenderer.render(matrices, queue, light, stackState, random, box);
    }

    @Unique
    private static float lynxwave$modelMinY(ItemEntityRenderState state) {
        if (state.itemRenderState != null && !state.itemRenderState.isEmpty()) {
            return -0.125f;
        }
        return 0f;
    }

    @Unique
    private static Box lynxwave$approxModelBox(ItemEntityRenderState state) {
        float w = Math.max(0.125f, state.width * 0.5f);
        float h = Math.max(0.125f, state.height * 0.35f);
        return new Box(-w, 0, -w, w, h, w);
    }
}
