package dev.client.api.injection;

import dev.client.modules.core.render.NoRender;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixins {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Fire")) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
      if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Blocks")) ci.cancel();
    }
}
