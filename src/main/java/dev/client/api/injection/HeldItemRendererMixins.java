package dev.client.api.injection;

import dev.client.api.compat.RenderSystem;
import dev.client.api.nullcry.ClientApi;
import dev.client.modules.core.render.GlassHands;
import dev.client.modules.core.render.SwordAnimation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixins implements ClientApi {

    @Inject(method = "renderFirstPersonItem", at = @At(value = "HEAD"), cancellable = true)
    private void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue renderCommandQueue, int light, CallbackInfo ci) {
        GlassHands glass = GlassHands.INSTANCE;
        boolean applyGlass = glass != null && glass.isEnabled();

        if (!(item.isEmpty()) && !(item.getItem() instanceof FilledMapItem)) {
            ci.cancel();

            if (applyGlass) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }

            SwordAnimation.INSTANCE.renderFirstPersonItem(player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, renderCommandQueue, light);

            if (applyGlass) {
                RenderSystem.disableBlend();
            }
        }
    }
}
