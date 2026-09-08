package dev.client.api.injection;

import dev.client.api.nullcry.helper.entity.EntityRenderStateOwner;
import dev.client.modules.core.render.Beautifully;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Предмет в руке не умеет плавно исчезать, поэтому во время перехода
 * Beautifully «Плавный F5» он просто не рисуется у локального игрока.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixins {

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ArmedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void lynx$hideDuringFade(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ArmedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!Beautifully.playerFading()) return;
        if (((EntityRenderStateOwner) state).lynx$getEntity() == MinecraftClient.getInstance().player) {
            ci.cancel();
        }
    }
}
