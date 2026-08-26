package dev.client.api.injection;

import dev.client.api.nullcry.events.EventManager;
import dev.client.api.nullcry.events.core.world.FogEvent;
import dev.client.api.nullcry.render.ColorUtils;
import dev.client.modules.core.render.NoRender;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class BackGroundRendererMixins {
    @Shadow @Final private GpuBuffer emptyBuffer;

    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private void getFogColorHook(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        FogEvent event = new FogEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            int color = event.getColor();
            cir.setReturnValue(new Vector4f(ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color), ColorUtils.alphaf(color)));
        }
    }

    @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
    private void modifyFog(FogRenderer.FogType fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mode.isSelected("Fog")) {
            cir.setReturnValue(this.emptyBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE));
        }
    }
}
