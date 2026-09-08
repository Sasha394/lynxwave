package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.render.RenderEvent;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ник над игроком.
 * <p>
 * PlayerEntityRenderer переопределяет renderLabelIfPresent и не зовёт super, поэтому
 * инжект в EntityRenderer для игроков не срабатывал — модуль NameTags рисовал свои
 * ники поверх ванильных. Здесь событие отмены доводится и до игроков; на мобах и
 * табличках-голограммах по-прежнему работает инжект в EntityRendererMixins.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererLabelMixins {

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lynx$cancelPlayerLabel(PlayerEntityRenderState state, MatrixStack matrices,
                                        OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState,
                                        CallbackInfo ci) {
        RenderEvent.RenderLabelsEvent<Entity, PlayerEntityRenderState> event = new RenderEvent.RenderLabelsEvent<>(state);
        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
