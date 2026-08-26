package dev.client.api.injection.custommodels;

import dev.client.modules.core.render.custommodels.CustomPlayerModelRenderer;
import dev.client.modules.core.render.custommodels.ICustomPlayerModelState;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCustomModelsMixin {
    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V")
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void lynxwave$replacePlayerModel(
            OrderedRenderCommandQueue queue,
            Model model,
            Object submittedState,
            MatrixStack matrices,
            RenderLayer renderLayer,
            int light,
            int overlay,
            int color,
            Sprite sprite,
            int outlineColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
            LivingEntityRenderState state,
            MatrixStack originalMatrices,
            OrderedRenderCommandQueue originalQueue,
            CameraRenderState cameraState
    ) {
        if (submittedState instanceof PlayerEntityRenderState playerState
                && submittedState instanceof ICustomPlayerModelState customState
                && customState.lynxwave$hasCustomModel()
                && model instanceof PlayerEntityModel playerModel) {
            playerModel.setAngles(playerState);
            if (CustomPlayerModelRenderer.render(
                    customState.lynxwave$getCustomModel(),
                    playerModel,
                    playerState,
                    matrices,
                    queue,
                    light,
                    overlay,
                    color,
                    outlineColor
            )) {
                return;
            }
        }

        queue.submitModel(model, submittedState, matrices, renderLayer, light, overlay, color, sprite, outlineColor, crumblingOverlay);
    }
}
