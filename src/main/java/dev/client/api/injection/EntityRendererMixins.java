package dev.client.api.injection;

import dev.client.LynxWave;
import dev.client.api.nullcry.helper.entity.EntityRenderStateOwner;
import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import dev.client.modules.core.misc.NameProtect;
import dev.client.modules.core.render.Beautifully;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixins<T extends Entity, S extends EntityRenderState> {
    @Shadow public abstract TextRenderer getTextRenderer();

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void onRenderLabelIfPresentHead(S state, MatrixStack matrices, OrderedRenderCommandQueue renderCommandQueue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        RenderEvent.RenderLabelsEvent<T, S> renderLabelsEvent = new RenderEvent.RenderLabelsEvent<>(state);
        EventManager.call(renderLabelsEvent);

        if (renderLabelsEvent.isCancelled()) {
            ci.cancel();
            return;
        }

        Text displayName = state.displayName;
        if (displayName != null && NameProtect.INSTANCE != null && NameProtect.INSTANCE.isEnabled()) {
            state.displayName = NameProtect.INSTANCE.patchText(displayName);
        }
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void lynx$captureEntity(T entity, S state, float tickDelta, CallbackInfo ci) {
        ((EntityRenderStateOwner) state).lynx$setEntity(entity);
        // Огонь поверх модели не умеет плавно исчезать вместе с ней.
        if (Beautifully.playerFading() && entity == MinecraftClient.getInstance().player) {
            state.onFire = false;
        }
    }
}
